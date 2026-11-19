package `in`.swatantra.focus.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import `in`.swatantra.focus.dpc.ParentTotpAuthenticator

class FamilySetupActivity : Activity() {

    private lateinit var generatedTotpSecret: String
    private lateinit var generatedRecoveryKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deContext = createDeviceProtectedStorageContext()
        val callPrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        if (callPrefs.getBoolean("family_numbers_locked", false)) {
            finish()
            return
        }

        generatedTotpSecret = ParentTotpAuthenticator.generateBase32Secret(16)
        generatedRecoveryKey = ParentTotpAuthenticator.generateRecoveryKey()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "SWATANTRA // ONE-TIME FAMILY LOCK SETUP"
            textSize = 19f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        val subtitle = TextView(this).apply {
            text = "Lock 1 to 3 family phone numbers, set a 6-digit Parent PIN, and save the free offline Authenticator & Recovery keys with your parents."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        fun makeField(hintText: String, inputTypeFlags: Int, maxLen: Int): EditText {
            return EditText(this).apply {
                hint = hintText
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.parseColor("#E2E8F0"))
                setBackgroundColor(Color.parseColor("#161F30"))
                inputType = inputTypeFlags
                filters = arrayOf(InputFilter.LengthFilter(maxLen))
                setPadding(28, 22, 28, 22)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 14) }
                layoutParams = lp
            }
        }

        val slot1 = makeField("Family Slot 1 (10-digit mobile, required)", InputType.TYPE_CLASS_PHONE, 15)
        val slot2 = makeField("Family Slot 2 (optional)", InputType.TYPE_CLASS_PHONE, 15)
        val slot3 = makeField("Family Slot 3 (optional)", InputType.TYPE_CLASS_PHONE, 15)

        val pinField = makeField(
            "Set 6-Digit Parent Unlock PIN",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            6
        )
        val confirmPinField = makeField(
            "Confirm 6-Digit Parent Unlock PIN",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            6
        )

        val totpCard = TextView(this).apply {
            text = "OFFLINE GOOGLE AUTHENTICATOR KEY (₹0 COST):\n$generatedTotpSecret\n\nAdd once in parent's Google Authenticator (+ -> Enter a setup key -> Time-based).\n\nOFFLINE BACKUP RECOVERY KEY (SAVE WITH PARENTS):\n$generatedRecoveryKey"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(28, 24, 28, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 24) }
            layoutParams = lp
        }

        val saveButton = Button(this).apply {
            text = "LOCK FAMILY NUMBERS, PIN & AUTHENTICATOR"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setPadding(28, 28, 28, 28)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        saveButton.setOnClickListener {
            val rawNumbers = listOf(slot1.text.toString(), slot2.text.toString(), slot3.text.toString())
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val normalized = rawNumbers.mapNotNull { normalizeIndianNumber(it) }.distinct()
            if (normalized.isEmpty() || normalized.size > 3) {
                Toast.makeText(this, "Enter 1 to 3 valid 10-digit family numbers.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pin = pinField.text?.toString()?.trim().orEmpty()
            val confirmPin = confirmPinField.text?.toString()?.trim().orEmpty()
            if (pin.length != 6 || !pin.all { it.isDigit() }) {
                Toast.makeText(this, "Parent PIN must be exactly 6 digits.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pin != confirmPin) {
                Toast.makeText(this, "PINs do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pinHash = ParentTotpAuthenticator.sha256Hex(pin)
            val recoveryHash = ParentTotpAuthenticator.sha256Hex(
                ParentTotpAuthenticator.normalizeRecoveryKey(generatedRecoveryKey)
            )

            callPrefs.edit()
                .putStringSet("whitelisted_family_numbers", normalized.toSet())
                .putString("family_pin_sha256", pinHash)
                .putString("parent_totp_secret", generatedTotpSecret)
                .putString("parent_recovery_sha256", recoveryHash)
                .putBoolean("family_numbers_locked", true)
                .commit()

            Toast.makeText(this, "Family Lock, PIN & Offline Authenticator Sealed.", Toast.LENGTH_LONG).show()
            finish()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(slot1)
        root.addView(slot2)
        root.addView(slot3)
        root.addView(pinField)
        root.addView(confirmPinField)
        root.addView(totpCard)
        root.addView(saveButton)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun normalizeIndianNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent skipping one-time family lock setup
    }
}
