package it.geohelp
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.util.Locale
class HomeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocale(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // Per ora commenta il Menu finché non lo creiamo
        // findViewById<MaterialCardView>(R.id.cardMainLogo).setOnClickListener {
        //     startActivity(Intent(this, MenuActivity::class.java))
        // }
        findViewById<ImageView>(R.id.flagItHome).setOnClickListener {
            setLanguage("it")
        }
        findViewById<ImageView>(R.id.flagEnHome).setOnClickListener {
            setLanguage("en")
        }
    }
    private fun setLanguage(langCode: String) {
        val prefs = getSharedPreferences("geohelp_prefs", MODE_PRIVATE)
        prefs.edit().putString("lang", langCode).apply()
        recreate()
    }
    private fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("geohelp_prefs", MODE_PRIVATE)
        val lang = prefs.getString("lang", "it") ?: "it"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}