plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import java.io.File

/**
 * Legge una chiave da local.properties (non committato), restituisce "" se assente.
 * Usato per non hard-codare URL e anon key di Supabase nel repo.
 */
fun localProp(key: String): String {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return ""
    val p = Properties().apply { load(f.inputStream()) }
    return (p.getProperty(key) ?: "").trim()
}

android {
    namespace = "it.geohelp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "it.geohelp"
        minSdk = 24
        targetSdk = 36
        versionCode = 58
        versionName = "1.2.37"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // ----------------------------------------------------------------------
    // Flavor:
    //   dev  -> applicationId it.geohelp.dev, label "geoHELP Dev", Supabase dev
    //   prod -> applicationId it.geohelp,     label "geoHELP",     Supabase prod
    //
    // Le chiavi Supabase vengono lette da local.properties (gitignored):
    //   SUPABASE_URL_DEV=...
    //   SUPABASE_ANON_KEY_DEV=...
    //   SUPABASE_URL_PROD=...
    //   SUPABASE_ANON_KEY_PROD=...
    // ----------------------------------------------------------------------
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            // versionName dedicato al flavor dev, letto da gradle.properties (geohelp.devVersion).
            // Sovrascrive quello di defaultConfig: la build dev avrà versionName = "1.0.01" (o quello impostato).
            versionName = (project.findProperty("geohelp.devVersion") as? String) ?: "0.0.0"
            buildConfigField("String", "SUPABASE_URL",      "\"${localProp("SUPABASE_URL_DEV")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY_DEV")}\"")
            buildConfigField("String", "SOS_ADMIN_PIN",     "\"${localProp("SOS_ADMIN_PIN")}\"")
            val iubendaPrivacyIt = localProp("IUBENDA_PRIVACY_URL_IT").ifEmpty { localProp("IUBENDA_PRIVACY_URL") }
            val iubendaPrivacyEn = localProp("IUBENDA_PRIVACY_URL_EN").ifEmpty { iubendaPrivacyIt }
            buildConfigField("String", "PRIVACY_POLICY_URL_IT", "\"$iubendaPrivacyIt\"")
            buildConfigField("String", "PRIVACY_POLICY_URL_EN", "\"$iubendaPrivacyEn\"")
            buildConfigField(
                "String",
                "SUPABASE_AUTH_REDIRECT_URL",
                "\"${localProp("SUPABASE_AUTH_REDIRECT_URL_DEV")}\"",
            )
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "SUPABASE_URL",      "\"${localProp("SUPABASE_URL_PROD")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY_PROD")}\"")
            buildConfigField("String", "SOS_ADMIN_PIN",     "\"${localProp("SOS_ADMIN_PIN")}\"")
            val iubendaPrivacyIt = localProp("IUBENDA_PRIVACY_URL_PROD_IT")
                .ifEmpty { localProp("IUBENDA_PRIVACY_URL_IT") }
                .ifEmpty { localProp("IUBENDA_PRIVACY_URL") }
            val iubendaPrivacyEn = localProp("IUBENDA_PRIVACY_URL_PROD_EN")
                .ifEmpty { localProp("IUBENDA_PRIVACY_URL_EN") }
                .ifEmpty { iubendaPrivacyIt }
            buildConfigField("String", "PRIVACY_POLICY_URL_IT", "\"$iubendaPrivacyIt\"")
            buildConfigField("String", "PRIVACY_POLICY_URL_EN", "\"$iubendaPrivacyEn\"")
            buildConfigField(
                "String",
                "SUPABASE_AUTH_REDIRECT_URL",
                "\"${localProp("SUPABASE_AUTH_REDIRECT_URL_PROD")}\"",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Necessario per supabase-kt con minSdk < 26 (usa API Java 8+)
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        language {
            // Keep both IT/EN resources in Play-installed bundles so in-app language switch works.
            enableSplit = false
        }
        abi {
            // APK universale da Play: evita split ABI che a volte causano crash su dispositivi specifici.
            enableSplit = false
        }
    }
}

/**
 * Dopo assembleProdRelease + bundleProdRelease, copia gli artefatti **prod** con nome fisso:
 *   geoHELP-<versionName>-release.apk
 *   geoHELP-<versionName>-release.aab
 *
 * Non usare bundle/release/app-release.aab: con i flavor dev+prod è un artefatto legacy
 * (versionCode/versionName non aggiornati). Play richiede app-prod-release.aab.
 *
 * Uso: .\gradlew.bat assembleProdRelease bundleProdRelease renameReleaseArtifacts
 */
/** Solo APK versionato: geoHELP-<versionName>-release.apk (senza build AAB). */
tasks.register("renameReleaseApkOnly") {
    dependsOn("assembleProdRelease")
    doLast {
        val versionName =
            project.extensions.getByType(ApplicationExtension::class.java).defaultConfig.versionName
                ?: "unknown"
        val base = "geoHELP-${versionName}-release"
        val apkIn = file("build/outputs/apk/prod/release/app-prod-release.apk")
        val apkOut = file("build/outputs/apk/prod/release/$base.apk")
        if (apkIn.exists()) {
            apkOut.delete()
            apkIn.copyTo(apkOut, overwrite = true)
            logger.lifecycle("Copied prod APK -> ${apkOut.name}")
        } else {
            logger.warn("Sorgente non trovata: ${apkIn.absolutePath}")
        }
    }
}

tasks.register("renameReleaseArtifacts") {
    dependsOn("renameReleaseApkOnly", "bundleProdRelease")
    doLast {
        val versionName =
            project.extensions.getByType(ApplicationExtension::class.java).defaultConfig.versionName
                ?: "unknown"
        val base = "geoHELP-${versionName}-release"
        val aabIn = file("build/outputs/bundle/prodRelease/app-prod-release.aab")
        val aabOut = file("build/outputs/bundle/prodRelease/$base.aab")
        if (aabIn.exists()) {
            aabOut.delete()
            aabIn.copyTo(aabOut, overwrite = true)
            logger.lifecycle("Copied prod AAB -> ${aabOut.name} (use this for Play)")
        } else {
            logger.warn("Sorgente non trovata: ${aabIn.absolutePath}")
        }
    }
}

/** Alias del vecchio nome: esegue anche la copia dell'AAB (come renameReleaseArtifacts). */
tasks.register("renameReleaseApk") {
    dependsOn("renameReleaseArtifacts")
}

/**
 * Dopo `assembleDevDebug` (e idem per `assembleDevRelease`) copia l'APK del flavor dev
 * con nome versionato: app-dev-debug_<geohelp.devVersion>.apk
 *
 * Incrementare la versione = cambiare `geohelp.devVersion` in gradle.properties (es. 1.0.02).
 *
 * Esempio finale:
 *   app/build/outputs/apk/dev/debug/app-dev-debug.apk            (file originale, sempre presente)
 *   app/build/outputs/apk/dev/debug/app-dev-debug_1.0.01.apk     (copia versionata)
 */
val devVersionProp = (project.findProperty("geohelp.devVersion") as? String) ?: "unknown"

tasks.register("renameDevDebugArtifact") {
    val outDir = file("build/outputs/apk/dev/debug")
    val devVer = devVersionProp
    doLast {
        if (outDir.isDirectory) {
            outDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("app-dev-debug_") && f.name.endsWith(".apk")) {
                    f.delete()
                }
            }
        }
        val src = File(outDir, "app-dev-debug.apk")
        if (src.exists()) {
            val dst = File(outDir, "app-dev-debug_$devVer.apk")
            src.copyTo(dst, overwrite = true)
            logger.lifecycle("Renamed dev debug APK -> ${dst.name}")
        } else {
            logger.warn("Sorgente non trovata: ${src.absolutePath}")
        }
    }
}

tasks.register("renameDevReleaseArtifact") {
    val outDir = file("build/outputs/apk/dev/release")
    val devVer = devVersionProp
    doLast {
        if (outDir.isDirectory) {
            outDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("app-dev-release_") && f.name.endsWith(".apk")) {
                    f.delete()
                }
            }
        }
        val src = File(outDir, "app-dev-release.apk")
        if (src.exists()) {
            val dst = File(outDir, "app-dev-release_$devVer.apk")
            src.copyTo(dst, overwrite = true)
            logger.lifecycle("Renamed dev release APK -> ${dst.name}")
        } else {
            logger.warn("Sorgente non trovata: ${src.absolutePath}")
        }
    }
}

// Hook automatico: ogni assembleDev{Debug,Release} produce anche la copia versionata.
tasks.matching { it.name == "assembleDevDebug" }
    .configureEach { finalizedBy("renameDevDebugArtifact") }
tasks.matching { it.name == "assembleDevRelease" }
    .configureEach { finalizedBy("renameDevReleaseArtifact") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Supabase Kotlin (BOM + Auth + Postgrest) + Ktor engine + serialization
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // EncryptedSharedPreferences per il verifier del PIN (mai conservato in chiaro)
    implementation(libs.androidx.security.crypto)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
}
