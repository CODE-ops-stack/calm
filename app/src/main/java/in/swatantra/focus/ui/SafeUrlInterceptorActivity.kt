package `in`.swatantra.focus.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SafeUrlInterceptorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deContext = createDeviceProtectedStorageContext()
        val directivePrefs = deContext.getSharedPreferences(MainActivity.PREFS_DIRECTIVES, Context.MODE_PRIVATE)
        val armedDirective = directivePrefs.getString(
            MainActivity.KEY_ACTIVE_DIRECTIVE,
            MainActivity.UNIVERSAL_IF_THEN_DIRECTIVES[0]
        ) ?: MainActivity.UNIVERSAL_IF_THEN_DIRECTIVES[0]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(56, 56, 56, 56)
        }

        val title = TextView(this).apply {
            text = "TACTICAL INTERCEPT // STAY ON MISSION"
            textSize = 20f
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val directiveCard = TextView(this).apply {
            text = "YOUR ARMED PROTOCOL:\n\n$armedDirective"
            textSize = 15f
            setTextColor(Color.parseColor("#F59E0B"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(36, 36, 36, 36)
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 28) }
            layoutParams = lp
        }

        val subtitle = TextView(this).apply {
            text = "Distractions fade. Your future achievement stays. Execute your rule and return to your sortie."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }

        val returnButton = Button(this).apply {
            text = "EXECUTE 'THEN' PROTOCOL & RETURN TO SORTIE"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(directiveCard)
        root.addView(subtitle)
        root.addView(returnButton)
        setContentView(root)
    }
}
