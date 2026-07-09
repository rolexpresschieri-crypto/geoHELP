package it.geohelp.mandown

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import it.geohelp.MainActivity
import it.geohelp.R
import it.geohelp.data.sms.EmergencyTypeCatalog
import it.geohelp.data.sms.SmsEventKeys
import it.geohelp.data.sms.SmsEventsRepository
import it.geohelp.location.LocationPermissions
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MAN DOWN: in ascolto su accelerometro (accelerazione lineare, o modulo accelerometro grezzo
 * in fallback) per picchi da caduta/urto; dopo il picco avvia un countdown (default 60s).
 * Se non annullato, invia SMS ai numeri primari via Twilio (rete); dopo 10 s verifica movimento e,
 * se significativo, tracciamento ogni 15 s per 60 s (valanga/frana).
 */
class ManDownForegroundService : Service(), SensorEventListener {

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val smsEventsRepository = SmsEventsRepository()
    private val twilioRepository = ManDownTwilioRepository()

    private enum class ManDownPhase { ARMED, COUNTDOWN, VERIFY_MOVEMENT, TRACKING }

    private lateinit var sensorManager: SensorManager
    private var activeSensor: Sensor? = null
    private var useRawAccelerometer: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var countDownTimer: CountDownTimer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var armed = false
    private var phase = ManDownPhase.ARMED
    /** Fino a questo [SystemClock.elapsedRealtime] non si rilevano nuovi urti. */
    private var resumeListenAfterElapsedMs: Long = 0L

    private var anchorLocation: Location? = null
    private val verifyMovementTracker = ManDownLocationTracker()
    private var trackingSmsIndex = 0

    private var lang = "it"
    private var displayName = ""
    private var birthYear: Int? = null
    private var userPhoneE164 = ""
    private var hasMedicalConsent = false
    private var medicalSummary = ""
    /** Fino a 2 primari + 1 backup (max 3) per SMS MAN DOWN. */
    private val smsDestinations = mutableListOf<String>()
    private var twilioRetryGeneration = 0
    private var twilioRetryRunnable: Runnable? = null
    private var twilioOnlineFailStreak = 0
    private var twilioInFlight = false
    /** Evita re-invio se un tentativo precedente ha già consegnato (retry duplicati). */
    private var twilioDeliveredCacheKey: String? = null
    private var countdownAlarmPlayer: MediaPlayer? = null
    private val locationTracker = ManDownLocationTracker()
    private var gpsUpdatesActive = false
    private val gpsListener = LocationListener { loc ->
        locationTracker.ingest(loc)
        if (phase == ManDownPhase.VERIFY_MOVEMENT) {
            verifyMovementTracker.ingest(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (linear != null) {
            activeSensor = linear
            useRawAccelerometer = false
        } else {
            activeSensor = accel
            useRawAccelerometer = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> {
                lang = intent.getStringExtra(EXTRA_LANG) ?: "it"
                displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
                birthYear = intent.getIntExtra(EXTRA_BIRTH_YEAR, -1).takeIf { it > 0 }
                userPhoneE164 = intent.getStringExtra(EXTRA_USER_PHONE).orEmpty().trim()
                hasMedicalConsent = intent.getBooleanExtra(EXTRA_HAS_MEDICAL_CONSENT, false)
                medicalSummary = intent.getStringExtra(EXTRA_MEDICAL_SUMMARY).orEmpty()
                smsDestinations.clear()
                intent.getStringExtra(EXTRA_PRIMARY_NUMBERS)?.split(",")
                    ?.mapNotNull { normalizeSmsAddress(it) }
                    ?.distinct()
                    ?.take(MAX_SMS_DESTINATIONS)
                    ?.let { smsDestinations.addAll(it) }
                if (smsDestinations.isEmpty()) {
                    smsDestinations.add(
                        normalizeSmsAddress("112") ?: "112",
                    )
                }
                Log.i(
                    TAG,
                    "MAN DOWN armed — ${smsDestinations.size} destinatario/i: " +
                        smsDestinations.joinToString { "***${it.takeLast(4)}" } +
                        " · invio SMS via Twilio",
                )

                ensureNotificationChannel()
                armed = true
                phase = ManDownPhase.ARMED
                cancelPendingPostImpactWork()
                resumeListenAfterElapsedMs = 0L
                getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_MAN_DOWN_ARMED, true).apply()
                startAsForegroundQuiet()
                startGpsUpdates()
                registerSensorListener()
            }
            ACTION_DISARM -> {
                getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_MAN_DOWN_ARMED, false).apply()
                disarmInternal(stopService = true)
            }
            ACTION_CANCEL_COUNTDOWN -> {
                cancelActiveManDownSequence(resumeSensorsAfterMs = 5_000L)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        logScope.cancel()
        disarmInternal(stopService = false)
        super.onDestroy()
    }

    private fun disarmInternal(stopService: Boolean) {
        armed = false
        getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PREF_MAN_DOWN_ARMED, false).apply()
        cancelActiveManDownSequence(resumeSensorsAfterMs = 0L)
        stopGpsUpdates()
        unregisterSensorListener()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopService) {
            stopSelf()
        }
    }

    private fun startAsForegroundQuiet() {
        val notification = buildArmedNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerSensorListener() {
        val s = activeSensor ?: return
        runCatching { sensorManager.unregisterListener(this) }
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterSensorListener() {
        runCatching { sensorManager.unregisterListener(this) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!armed || phase != ManDownPhase.ARMED) return
        val e = event ?: return
        if (SystemClock.elapsedRealtime() < resumeListenAfterElapsedMs) return

        val m = when (e.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            }
            else -> return
        }
        val threshold = if (useRawAccelerometer) RAW_IMPACT_THRESHOLD_MS2 else IMPACT_THRESHOLD_MS2
        if (m >= threshold) {
            Log.i(TAG, "Impact peak detected m=$m (raw=$useRawAccelerometer) — starting countdown")
            startCountdown()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startCountdown() {
        if (phase != ManDownPhase.ARMED) return
        phase = ManDownPhase.COUNTDOWN
        unregisterSensorListener()
        locationTracker.reset()
        verifyMovementTracker.reset()
        startGpsUpdates()
        promoteForegroundForActiveManDown()
        sampleGpsForCountdown()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "geohelp:ManDownCountdown"
        ).apply {
            acquire(
                COUNTDOWN_MS + POST_INITIAL_VERIFY_MS + TRACKING_DURATION_MS + 30_000L
            )
        }

        updateCountdownNotification(COUNTDOWN_SEC)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(COUNTDOWN_MS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sampleGpsForCountdown()
                val sec = ((millisUntilFinished + 999) / 1000).toInt()
                updateCountdownNotification(sec)
                sendBroadcast(
                    Intent(ACTION_COUNTDOWN_TICK).setPackage(packageName)
                        .putExtra(EXTRA_COUNTDOWN_SEC, sec)
                )
            }

            override fun onFinish() {
                onCountdownFinishedSendInitialSms()
            }
        }.start()
        startCountdownAlarmSound()
    }

    /**
     * Stessa sirena dell’app AllarmeApp (`res/raw/siren.mp3`), copiata come [R.raw.mandown_siren].
     * Fallback: suoneria di sistema se il file raw non è disponibile.
     */
    private fun startCountdownAlarmSound() {
        stopCountdownAlarmSound()
        runCatching {
            val mp = MediaPlayer.create(this, R.raw.mandown_siren)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                @Suppress("DEPRECATION")
                setWakeMode(this@ManDownForegroundService, PowerManager.PARTIAL_WAKE_LOCK)
                setVolume(1f, 1f)
                start()
            }
            if (mp != null) {
                countdownAlarmPlayer = mp
                return
            }
        }.onFailure {
            Log.e(TAG, "Sirena MAN DOWN (raw) non avviata", it)
        }

        val uri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri == null) {
            Log.w(TAG, "Nessun URI suoneria di sistema per countdown MAN DOWN")
            return
        }
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.setVolume(1f, 1f)
            mp.start()
            countdownAlarmPlayer = mp
        }.onFailure {
            Log.e(TAG, "Audio countdown MAN DOWN (sistema) non avviato", it)
        }
    }

    private fun stopCountdownAlarmSound() {
        countdownAlarmPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        countdownAlarmPlayer = null
    }

    private fun cancelActiveManDownSequence(resumeSensorsAfterMs: Long) {
        cancelPendingPostImpactWork()
        stopGpsUpdates()
        locationTracker.reset()
        verifyMovementTracker.reset()
        anchorLocation = null
        stopCountdownAlarmSound()
        countDownTimer?.cancel()
        countDownTimer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        phase = ManDownPhase.ARMED
        if (!armed) return

        resumeListenAfterElapsedMs = SystemClock.elapsedRealtime() + resumeSensorsAfterMs

        sendBroadcast(Intent(ACTION_COUNTDOWN_CANCELLED).setPackage(packageName))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildArmedNotification())

        if (resumeSensorsAfterMs > 0L) {
            mainHandler.postDelayed({
                if (armed && phase == ManDownPhase.ARMED) registerSensorListener()
            }, resumeSensorsAfterMs)
        } else {
            if (armed) registerSensorListener()
        }
    }

    private fun cancelPendingPostImpactWork() {
        mainHandler.removeCallbacks(verifySampleRunnable)
        mainHandler.removeCallbacks(finishVerifyRunnable)
        mainHandler.removeCallbacks(trackingSmsRunnable)
        cancelTwilioRetry()
    }

    private fun cancelTwilioRetry() {
        twilioRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        twilioRetryRunnable = null
        twilioRetryGeneration++
        twilioOnlineFailStreak = 0
        twilioInFlight = false
    }

    private fun twilioDeliveryCacheKey(
        messageKind: String,
        traceIndex: Int?,
        text: String,
    ): String = "$messageKind|${traceIndex ?: -1}|${text.hashCode()}"

    private val verifySampleRunnable = Runnable { runVerifyMovementSample() }

    private val finishVerifyRunnable = Runnable { finishVerifyMovementWindow() }

    private val trackingSmsRunnable = Runnable { sendNextTrackingSmsIfNeeded() }

    private fun runVerifyMovementSample() {
        if (phase != ManDownPhase.VERIFY_MOVEMENT) return
        sampleGpsForCountdown()
        readBestLastLocation()?.let { verifyMovementTracker.ingest(it) }
        mainHandler.postDelayed(verifySampleRunnable, 1_000L)
    }

    private fun finishVerifyMovementWindow() {
        if (phase != ManDownPhase.VERIFY_MOVEMENT) return
        mainHandler.removeCallbacks(verifySampleRunnable)
        if (hasSignificantMovementSinceAnchor()) {
            Log.i(TAG, "Movimento significativo — avvio tracciamento 60s")
            startTrackingPhase()
        } else {
            Log.i(TAG, "Nessun movimento significativo — fine evento MAN DOWN")
            // Non inviare ACTION_SMS_SENT qui: l'SMS iniziale è già stato inviato; evita toast duplicati.
            completeManDownEvent()
        }
    }

    private fun onCountdownFinishedSendInitialSms() {
        stopCountdownAlarmSound()
        countDownTimer = null
        sendBroadcast(Intent(ACTION_COUNTDOWN_CANCELLED).setPackage(packageName))

        sampleGpsForCountdown()
        readBestLastLocation()?.let { anchorLocation = Location(it) }

        sendSmsToPrimariesAsync(buildInitialSmsBody(), SmsEventKeys.KIND_INITIAL) { success ->
            if (!success) {
                Log.e(TAG, "Invio SMS iniziale MAN DOWN fallito")
                sendLostSignalSms()
                publishUserSmsOutcome(ACTION_SMS_FAILED, failed = true)
                completeManDownEvent()
                return@sendSmsToPrimariesAsync
            }
            Log.i(TAG, "SMS iniziale MAN DOWN confermato dal modem — verifica movimento tra 10s")
            publishUserSmsOutcome(ACTION_SMS_SENT, failed = false)
            startVerifyMovementWindow()
        }
    }

    private fun startVerifyMovementWindow() {
        phase = ManDownPhase.VERIFY_MOVEMENT
        verifyMovementTracker.reset()
        readBestLastLocation()?.let { verifyMovementTracker.ingest(it) }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildVerifyNotification())
        mainHandler.post(verifySampleRunnable)
        mainHandler.postDelayed(finishVerifyRunnable, POST_INITIAL_VERIFY_MS)
    }

    private fun hasSignificantMovementSinceAnchor(): Boolean {
        val anchor = anchorLocation ?: return false
        readBestLastLocation()?.let { current ->
            val dist = ManDownGeo.distanceMeters(anchor, current)
            if (dist >= MOVEMENT_MIN_DISTANCE_M) {
                Log.i(TAG, "Movimento: spostamento ${dist}m")
                return true
            }
        }
        val maxMs = verifyMovementTracker.peakSpeedMs()
        if (maxMs >= MOVEMENT_MIN_SPEED_MS) {
            Log.i(TAG, "Movimento: velocità max ${maxMs * 3.6f} km/h")
            return true
        }
        return false
    }

    private fun startTrackingPhase() {
        phase = ManDownPhase.TRACKING
        trackingSmsIndex = 0
        locationTracker.reset()
        sendNextTrackingSmsIfNeeded()
    }

    private fun sendNextTrackingSmsIfNeeded() {
        if (phase != ManDownPhase.TRACKING) return
        trackingSmsIndex++
        val index = trackingSmsIndex
        if (index > TRACKING_SMS_COUNT) {
            completeManDownEvent()
            return
        }
        sampleGpsForCountdown()
        val pos = locationTracker.snapshotInstant()
        val anchor = anchorLocation
        val current = locationTracker.latestLocation() ?: readBestLastLocation()
        val bearing = if (anchor != null && current != null) {
            ManDownGeo.formatBearing(anchor, current)
        } else {
            "---"
        }
        val distance = if (anchor != null && current != null) {
            ManDownGeo.formatDistanceM(ManDownGeo.distanceMeters(anchor, current))
        } else {
            "---"
        }
        val body = EmergencySmsText.buildManDownTrackingSmsBody(
            context = this,
            languageCode = lang,
            displayName = displayName,
            latitude = pos.latitude,
            longitude = pos.longitude,
            altitude = pos.altitude,
            accuracy = pos.accuracy,
            speedKmh = pos.speedKmh,
            traceIndex = index,
            traceTotal = TRACKING_SMS_COUNT,
            bearing = bearing,
            distanceFromStart = distance,
        )
        updateTrackingNotification(index)
        sendSmsToPrimariesAsync(body, SmsEventKeys.KIND_TRACE, traceIndex = index) { success ->
            if (!success) {
                Log.e(TAG, "SMS traccia $index fallito")
                sendLostSignalSms()
                publishUserSmsOutcome(ACTION_SMS_FAILED, failed = true)
                completeManDownEvent()
                return@sendSmsToPrimariesAsync
            }
            Log.i(
                TAG,
                "SMS traccia MAN DOWN $index/$TRACKING_SMS_COUNT confermato — " +
                    "pos ${pos.latitude},${pos.longitude} acc ${pos.accuracy}",
            )
            publishUserSmsOutcome(ACTION_SMS_SENT, failed = false)
            if (index >= TRACKING_SMS_COUNT) {
                completeManDownEvent()
            } else {
                mainHandler.postDelayed(trackingSmsRunnable, TRACKING_INTERVAL_MS)
            }
        }
    }

    private fun completeManDownEvent() {
        twilioDeliveredCacheKey = null
        cancelPendingPostImpactWork()
        stopGpsUpdates()
        locationTracker.reset()
        verifyMovementTracker.reset()
        anchorLocation = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        phase = ManDownPhase.ARMED
        resumeListenAfterElapsedMs = SystemClock.elapsedRealtime() + 45_000L
        if (armed) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildArmedNotification())
            mainHandler.postDelayed({
                if (armed && phase == ManDownPhase.ARMED) registerSensorListener()
            }, 45_000L)
        }
    }

    private fun buildInitialSmsBody(): String {
        val pos = locationTracker.snapshot()
        return EmergencySmsText.buildManDownSmsBody(
            context = this,
            languageCode = lang,
            displayName = displayName,
            birthYear = birthYear,
            latitude = pos.latitude,
            longitude = pos.longitude,
            altitude = pos.altitude,
            accuracy = pos.accuracy,
            speedKmh = pos.speedKmh,
            hasMedicalConsent = hasMedicalConsent,
            medicalSmsSummary = medicalSummary,
            userPhoneE164 = userPhoneE164,
        )
    }

    private fun sendLostSignalSms() {
        val body = EmergencySmsText.buildManDownLostSignalSmsBody(
            this,
            lang,
            userPhoneE164,
            displayName,
        )
        sendSmsToPrimariesAsync(body, SmsEventKeys.KIND_LOST_SIGNAL) { success ->
            publishUserSmsOutcome(
                if (success) ACTION_SMS_SENT else ACTION_SMS_FAILED,
                failed = !success,
            )
        }
    }

    /** MAN DOWN: invio SMS solo via Twilio (rete); Help manuale usa l'app Messaggi. */
    private fun sendSmsToPrimariesAsync(
        body: String,
        messageKind: String,
        traceIndex: Int? = null,
        onDone: (Boolean) -> Unit,
    ) {
        val text = body.trim()
        if (text.isBlank() || smsDestinations.isEmpty()) {
            Log.e(TAG, "MAN DOWN SMS: corpo vuoto o senza destinatari")
            recordSmsEvent(messageKind, SmsEventKeys.OUTCOME_FAILED, traceIndex, 0, 0)
            mainHandler.post {
                publishUserSmsOutcome(ACTION_SMS_FAILED, failed = true)
                onDone(false)
            }
            return
        }
        val dests = smsDestinations.mapNotNull { normalizeSmsAddress(it) }
        if (dests.isEmpty()) {
            recordSmsEvent(messageKind, SmsEventKeys.OUTCOME_FAILED, traceIndex, 0, 0)
            mainHandler.post {
                publishUserSmsOutcome(ACTION_SMS_FAILED, failed = true)
                onDone(false)
            }
            return
        }
        val segmentCount = estimateSmsSegments(text)
        Log.i(
            TAG,
            "MAN DOWN SMS via Twilio: $messageKind → ${dests.size} destinatari, ${text.length} char",
        )
        attemptTwilioWithRetry(text, dests, messageKind, traceIndex, segmentCount, onDone)
    }

    private fun estimateSmsSegments(text: String): Int {
        val len = text.length
        return if (len <= 160) 1 else ((len + 152) / 153).coerceAtLeast(1)
    }

    /** Retry Twilio finché c’è rete validata; senza apertura Messaggi. */
    private fun attemptTwilioWithRetry(
        text: String,
        dests: List<String>,
        messageKind: String,
        traceIndex: Int?,
        segmentCount: Int,
        onDone: (Boolean) -> Unit,
    ) {
        val cacheKey = twilioDeliveryCacheKey(messageKind, traceIndex, text)
        if (twilioDeliveredCacheKey == cacheKey) {
            Log.i(TAG, "MAN DOWN Twilio: già consegnato per $messageKind — skip retry")
            onDone(true)
            return
        }
        cancelTwilioRetry()
        val generation = twilioRetryGeneration
        lateinit var retryRunnable: Runnable
        retryRunnable = Runnable {
            if (generation != twilioRetryGeneration || !armed) {
                Log.i(TAG, "MAN DOWN Twilio: retry annullato")
                return@Runnable
            }
            if (twilioDeliveredCacheKey == cacheKey) {
                cancelTwilioRetry()
                onDone(true)
                return@Runnable
            }
            if (twilioInFlight) {
                twilioRetryRunnable = retryRunnable
                mainHandler.postDelayed(retryRunnable, TWILIO_RETRY_DELAY_MS)
                return@Runnable
            }
            if (!ManDownNetwork.hasValidatedInternet(this@ManDownForegroundService)) {
                twilioOnlineFailStreak = 0
                Log.w(TAG, "MAN DOWN Twilio: in attesa rete (Wi‑Fi/dati)…")
                publishTwilioRetryNotification()
                twilioRetryRunnable = retryRunnable
                mainHandler.postDelayed(retryRunnable, TWILIO_RETRY_DELAY_MS)
                return@Runnable
            }
            twilioInFlight = true
            logScope.launch {
                try {
                    val ok = sendViaTwilio(text, dests, messageKind, traceIndex, segmentCount)
                    mainHandler.post {
                        twilioInFlight = false
                        if (generation != twilioRetryGeneration || !armed) return@post
                        if (ok) {
                            twilioDeliveredCacheKey = cacheKey
                            cancelTwilioRetry()
                            onDone(true)
                            return@post
                        }
                        twilioOnlineFailStreak++
                        if (twilioOnlineFailStreak >= TWILIO_MAX_ONLINE_FAILS) {
                            Log.e(TAG, "MAN DOWN Twilio: troppi tentativi falliti con rete")
                            cancelTwilioRetry()
                            publishUserSmsOutcome(ACTION_SMS_FAILED, failed = true)
                            onDone(false)
                        } else {
                            Log.w(
                                TAG,
                                "MAN DOWN Twilio: tentativo $twilioOnlineFailStreak/" +
                                    "$TWILIO_MAX_ONLINE_FAILS — retry",
                            )
                            publishTwilioRetryNotification()
                            twilioRetryRunnable = retryRunnable
                            mainHandler.postDelayed(retryRunnable, TWILIO_RETRY_DELAY_MS)
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        twilioInFlight = false
                        if (generation != twilioRetryGeneration || !armed) return@post
                        twilioRetryRunnable = retryRunnable
                        mainHandler.postDelayed(retryRunnable, TWILIO_RETRY_DELAY_MS)
                    }
                }
            }
        }
        twilioRetryRunnable = retryRunnable
        mainHandler.post(retryRunnable)
    }

    private fun publishTwilioRetryNotification() {
        val t = localizedContext()
        val openPi = PendingIntent.getActivity(
            this,
            9203,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(t.getString(R.string.mandown_notif_sms_retry_title))
            .setContentText(t.getString(R.string.mandown_notif_sms_retry_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 3, notif)
    }

    /** Notifica + broadcast: sempre visibile anche a schermo spento / app in background. */
    private fun publishUserSmsOutcome(action: String, failed: Boolean) {
        sendBroadcast(Intent(action).setPackage(packageName))
        val t = localizedContext()
        val title = if (failed) {
            t.getString(R.string.mandown_notif_sms_failed_title)
        } else {
            t.getString(R.string.mandown_notif_sms_ok_title)
        }
        val text = if (failed) {
            t.getString(R.string.mandown_notif_sms_failed_text)
        } else {
            t.getString(R.string.mandown_notif_sms_ok_text)
        }
        val openPi = PendingIntent.getActivity(
            this,
            9202,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 2, notif)
    }

    /** Twilio cloud dopo fallimento modem; richiede rete + JWT. */
    private suspend fun sendViaTwilio(
        text: String,
        dests: List<String>,
        messageKind: String,
        traceIndex: Int?,
        segmentCount: Int,
    ): Boolean {
        val response = twilioRepository.send(text, dests)
        if (response == null) {
            Log.w(TAG, "MAN DOWN Twilio: nessuna risposta (rete/config/sessione)")
            return false
        }
        Log.i(
            TAG,
            "MAN DOWN Twilio: ok=${response.ok} sent=${response.sent} failed=${response.failed} from=${response.from}",
        )
        if (response.sent > 0) {
            recordSmsEvent(
                messageKind = messageKind,
                outcome = SmsEventKeys.OUTCOME_OK,
                traceIndex = traceIndex,
                destCount = response.sent,
                segmentCount = segmentCount,
            )
            return true
        }
        response.results.firstOrNull { !it.ok }?.error?.let { err ->
            Log.w(TAG, "MAN DOWN Twilio errore: $err")
        }
        return false
    }

    private fun recordSmsEvent(
        messageKind: String,
        outcome: String,
        traceIndex: Int?,
        destCount: Int,
        segmentCount: Int,
    ) {
        logScope.launch {
            smsEventsRepository.record(
                channel = SmsEventKeys.CHANNEL_MANDOWN,
                messageKind = messageKind,
                emergencyType = EmergencyTypeCatalog.MANDOWN,
                outcome = outcome,
                destCount = destCount,
                segmentCount = segmentCount,
                traceIndex = traceIndex,
            )
        }
    }

    /** Solo cifre e + iniziale; 00… → +…; spazi/trattini ignorati. */
    private fun normalizeSmsAddress(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        var normalized = buildString(s.length + 2) {
            var i = 0
            if (s[0] == '+') {
                append('+')
                i = 1
            }
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) append(c)
                i++
            }
        }
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.drop(2)
        }
        val digits = if (normalized.startsWith('+')) normalized.drop(1) else normalized
        if (digits.length < 3) return null
        if (!normalized.startsWith("+") && digits.length >= 9 && digits.startsWith("3")) {
            normalized = "+39$normalized"
        }
        return normalized
    }

    private fun sampleGpsForCountdown() {
        val loc = readBestLastLocation() ?: return
        locationTracker.ingest(loc)
    }

    private fun promoteForegroundForActiveManDown() {
        val notification = buildCountdownNotification(COUNTDOWN_SEC)
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                if (!hasLocation) {
                    Log.w(TAG, "startForeground MAN DOWN attivo: posizione non concessa")
                    return@runCatching
                }
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.e(TAG, "startForeground attivo MAN DOWN fallito", it)
        }
    }

    private fun startGpsUpdates() {
        if (gpsUpdatesActive) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "GPS updates: permesso posizione negato")
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Log.w(TAG, "GPS updates: nessun provider attivo")
                return
            }
        }
        runCatching {
            lm.requestLocationUpdates(
                provider,
                2_000L,
                0f,
                gpsListener,
                mainHandler.looper,
            )
            gpsUpdatesActive = true
            Log.i(
                TAG,
                "GPS updates avviati ($provider, ogni 2s) backgroundLoc=" +
                    LocationPermissions.hasBackgroundLocation(this),
            )
        }.onFailure {
            Log.e(TAG, "GPS updates non avviati", it)
        }
    }

    private fun stopGpsUpdates() {
        if (!gpsUpdatesActive) return
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        }
        gpsUpdatesActive = false
    }

    private fun readBestLastLocation(): Location? {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return null
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun localizedContext(): Context {
        val cfg = Configuration(resources.configuration)
        cfg.setLocale(Locale(lang))
        return createConfigurationContext(cfg)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = localizedContext()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Foreground service notification cannot be dismissed while active.
        // Use a low-importance channel for the "armed/active" persistent status,
        // and a high-importance channel only for countdown/tracking alarms.
        val ongoing = NotificationChannel(
            CHANNEL_ID_ONGOING,
            ctx.getString(R.string.mandown_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = ctx.getString(R.string.mandown_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setShowBadge(true)
        }
        val alerts = NotificationChannel(
            CHANNEL_ID_ALERTS,
            ctx.getString(R.string.mandown_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = ctx.getString(R.string.mandown_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(alerts)
    }

    private fun buildArmedNotification(): Notification {
        val disarm = PendingIntent.getService(
            this,
            2,
            Intent(this, ManDownForegroundService::class.java).apply { action = ACTION_DISARM },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = localizedContext()
        return NotificationCompat.Builder(this, CHANNEL_ID_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(t.getString(R.string.mandown_notif_armed_title))
            .setContentText(t.getString(R.string.mandown_notif_armed_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, t.getString(R.string.mandown_notif_disarm_action), disarm)
            .build()
    }

    private fun buildVerifyNotification(): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ManDownForegroundService::class.java).apply { action = ACTION_CANCEL_COUNTDOWN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = localizedContext()
        return NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(t.getString(R.string.mandown_notif_armed_title))
            .setContentText(t.getString(R.string.mandown_notif_verify_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, t.getString(R.string.mandown_notif_cancel), cancel)
            .build()
    }

    private fun updateTrackingNotification(traceIndex: Int) {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ManDownForegroundService::class.java).apply { action = ACTION_CANCEL_COUNTDOWN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = localizedContext()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(t.getString(R.string.mandown_notif_tracking_title, traceIndex, TRACKING_SMS_COUNT))
            .setContentText(t.getString(R.string.mandown_notif_tracking_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, t.getString(R.string.mandown_notif_cancel), cancel)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun buildCountdownNotification(secondsLeft: Int): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ManDownForegroundService::class.java).apply { action = ACTION_CANCEL_COUNTDOWN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = localizedContext()
        return NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(t.getString(R.string.mandown_notif_countdown_title, secondsLeft))
            .setContentText(t.getString(R.string.mandown_notif_countdown_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, t.getString(R.string.mandown_notif_cancel), cancel)
            .build()
    }

    private fun updateCountdownNotification(secondsLeft: Int) {
        val notif = buildCountdownNotification(secondsLeft)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    companion object {
        private const val TAG = "ManDownService"

        const val ACTION_ARM = "it.geohelp.mandown.ARM"
        const val ACTION_DISARM = "it.geohelp.mandown.DISARM"
        const val ACTION_CANCEL_COUNTDOWN = "it.geohelp.mandown.CANCEL_COUNTDOWN"

        const val ACTION_COUNTDOWN_TICK = "it.geohelp.mandown.COUNTDOWN_TICK"
        const val ACTION_COUNTDOWN_CANCELLED = "it.geohelp.mandown.COUNTDOWN_CANCELLED"
        const val ACTION_SMS_SENT = "it.geohelp.mandown.SMS_SENT"
        /** Legacy: MAN DOWN non apre più Messaggi; usato solo da Help/manuale se necessario. */
        const val ACTION_SMS_OPEN_MESSAGES = "it.geohelp.mandown.SMS_OPEN_MESSAGES"
        const val ACTION_SMS_FAILED = "it.geohelp.mandown.SMS_FAILED"

        const val EXTRA_LANG = "lang"
        const val EXTRA_DISPLAY_NAME = "displayName"
        const val EXTRA_BIRTH_YEAR = "birthYear"
        const val EXTRA_USER_PHONE = "userPhone"
        const val EXTRA_HAS_MEDICAL_CONSENT = "hasMedicalConsent"
        const val EXTRA_MEDICAL_SUMMARY = "medicalSummary"
        const val EXTRA_PRIMARY_NUMBERS = "primaryNumbers"
        const val EXTRA_COUNTDOWN_SEC = "countdownSec"

        const val SHARED_PREFS_NAME = "geohelp_prefs"
        const val PREF_MAN_DOWN_ARMED = "man_down_armed"

        private const val CHANNEL_ID_ONGOING = "geohelp_man_down_ongoing"
        private const val CHANNEL_ID_ALERTS = "geohelp_man_down_alerts"
        private const val NOTIFICATION_ID = 7101

        /** Picco di accelerazione lineare (m/s²) oltre il quale si considera un urto/caduta. */
        private const val IMPACT_THRESHOLD_MS2 = 27f
        /** Modulo accelerometro “grezzo” (include gravità): soglia più alta. */
        private const val RAW_IMPACT_THRESHOLD_MS2 = 38f

        private const val COUNTDOWN_SEC = 60
        private const val COUNTDOWN_MS = COUNTDOWN_SEC * 1000L

        private const val POST_INITIAL_VERIFY_MS = 10_000L
        private const val TRACKING_INTERVAL_MS = 15_000L
        private const val TRACKING_DURATION_MS = 60_000L
        private const val TRACKING_SMS_COUNT = (TRACKING_DURATION_MS / TRACKING_INTERVAL_MS).toInt()

        /** Spostamento minimo in 10 s dopo il primo SMS per avviare tracciamento. */
        private const val MOVEMENT_MIN_DISTANCE_M = 20f
        /** ~6 km/h */
        private const val MOVEMENT_MIN_SPEED_MS = 6f / 3.6f

        private const val TWILIO_RETRY_DELAY_MS = 20_000L
        private const val TWILIO_MAX_ONLINE_FAILS = 20

        /** 2 numeri primari + 1 backup dal foglio / sos_recipients. */
        const val MAX_SMS_DESTINATIONS = 3

        fun buildArmIntent(
            context: Context,
            lang: String,
            displayName: String,
            birthYear: Int?,
            userPhoneE164: String,
            hasMedicalConsent: Boolean,
            medicalSummary: String,
            primaryNumbersCsv: String,
        ): Intent = Intent(context, ManDownForegroundService::class.java).apply {
            action = ACTION_ARM
            putExtra(EXTRA_LANG, lang)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            putExtra(EXTRA_BIRTH_YEAR, birthYear ?: -1)
            putExtra(EXTRA_USER_PHONE, userPhoneE164)
            putExtra(EXTRA_HAS_MEDICAL_CONSENT, hasMedicalConsent)
            putExtra(EXTRA_MEDICAL_SUMMARY, medicalSummary)
            putExtra(EXTRA_PRIMARY_NUMBERS, primaryNumbersCsv)
        }

        fun disarm(context: Context) {
            context.startService(
                Intent(context, ManDownForegroundService::class.java).apply {
                    action = ACTION_DISARM
                }
            )
        }
    }
}
