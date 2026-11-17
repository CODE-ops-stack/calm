package `in`.swatantra.focus.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SafeUrlInterceptorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deContext = createDeviceProtectedStorageContext()
        val interceptedUri = intent?.dataString ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F7F5F0"))
            setPadding(64, 64, 64, 64)
        }

        val titleView = TextView(this).apply {
            text = "Swatantra Identity Pact"
            textSize = 22f
            setTextColor(Color.parseColor("#1E242B"))
        }

        val subtitleView = TextView(this).apply {
            text = "\"I protect my deep study block from attention residue.\"\n\nPark this link or thought for your 5:00 PM break (Surf the Urge — 10-Minute Rule):"
            textSize = 15f
            setTextColor(Color.parseColor("#4A5568"))
            setPadding(0, 24, 0, 32)
        }

        val noteInput = EditText(this).apply {
            setText(interceptedUri)
            hint = "Write the doubt or topic to check after 5:00 PM..."
            setTextColor(Color.parseColor("#1E242B"))
            setBackgroundColor(Color.parseColor("#EAE6DF"))
            setPadding(32, 24, 32, 24)
        }

        val saveAndReturnBtn = Button(this).apply {
            text = "Park for 5:00 PM Break & Return to Study"
            setBackgroundColor(Color.parseColor("#2B4C7E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val parkedText = noteInput.text?.toString()?.trim().orEmpty()
                if (parkedText.isNotEmpty()) {
                    val prefs = deContext.getSharedPreferences("urge_parking_lot_de", Context.MODE_PRIVATE)
                    val existing = prefs.getStringSet("parked_items", emptySet())?.toMutableSet() ?: mutableSetOf()
                    existing.add("${System.currentTimeMillis()}|$parkedText")
                    prefs.edit().putStringSet("parked_items", existing).commit()
                }
                finish()
            }
        }

        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(noteInput)
        root.addView(saveAndReturnBtn)
        setContentView(root)
    }
}
