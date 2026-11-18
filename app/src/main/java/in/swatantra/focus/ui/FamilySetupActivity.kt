package `in`.swatantra.focus.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

class FamilySetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deContext = createDeviceProtectedStorageContext()
        val prefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)

        // If already locked, do not allow modifying slots
        if (prefs.getBoolean("family_numbers_locked", false)) {
            finish()
            return
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 64, 56, 64)
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }

        val titleView = TextView(this).apply {
            text = "Family Call Whitelist"
            textSize = 22f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val subtitleView = TextView(this).apply {
            text = "Permanent 3-Slot Whitelist\nOnce verified, these slots are permanently locked to protect deep study. Emergency services (112, 100, 108) remain immune by law."
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 16, 0, 40)
        }

        fun createSlotInput(slotLabel: String, hintText: String): Pair<TextView, EditText> {
            val label = TextView(this).apply {
                text = slotLabel
                textSize = 14f
                setTextColor(Color.parseColor("#E2E8F0"))
                setPadding(0, 12, 0, 6)
            }
            val input = EditText(this).apply {
                hint = hintText
                setHintTextColor(Color.parseColor("#64748B"))
                inputType = InputType.TYPE_CLASS_PHONE
                setTextColor(Color.parseColor("#E2E8F0"))
                setBackgroundColor(Color.parseColor("#161F30"))
                setPadding(32, 24, 32, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 20) }
                layoutParams = lp
            }
            return Pair(label, input)
        }

        // Strictly 3 phone number slots
        val (label1, slot1) = createSlotInput("Slot 1: Mother / Primary Guardian", "Enter 10-digit mobile number...")
        val (label2, slot2) = createSlotInput("Slot 2: Father / Guardian", "Enter 10-digit mobile number (optional)...")
        val (label3, slot3) = createSlotInput("Slot 3: Brother / Family Member", "Enter 10-digit mobile number (optional)...")

        // ── 6-Digit Family Unlock PIN ─────────────────────────────────────────
        val pinLabel = TextView(this).apply {
            text = "6-Digit Family Unlock PIN"
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 20, 0, 6)
        }
        val pinInput = EditText(this).apply {
            hint = "Enter 6-digit PIN..."
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(32, 24, 32, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = lp
        }
        // ─────────────────────────────────────────────────────────────────────

        val lockButton = Button(this).apply {
            text = "Verify & Permanently Lock Family Numbers"
            textSize = 15f
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 24, 0, 0) }
            layoutParams = lp

            setOnClickListener {
                // ── Validate PIN ──────────────────────────────────────────────
                val pin = pinInput.text?.toString()?.trim().orEmpty()
                if (pin.length != 6 || !pin.all { it.isDigit() }) {
                    Toast.makeText(
                        this@FamilySetupActivity,
                        "Please enter a valid 6-digit numeric PIN.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Compute SHA-256 hex of the PIN using Device-Encrypted bootclasspath only
                val pinBytes = pin.toByteArray(Charsets.UTF_8)
                val pinSha256 = MessageDigest.getInstance("SHA-256")
                    .digest(pinBytes)
                    .joinToString("") { "%02x".format(it) }
                // ─────────────────────────────────────────────────────────────

                val rawInputs = listOf(
                    slot1.text?.toString()?.trim().orEmpty(),
                    slot2.text?.toString()?.trim().orEmpty(),
                    slot3.text?.toString()?.trim().orEmpty()
                )

                val normalizedE164List = mutableSetOf<String>()
                for (raw in rawInputs) {
                    if (raw.isNotBlank()) {
                        val networkPortion = PhoneNumberUtils.extractNetworkPortion(raw).trim()
                        val formattedE164 = PhoneNumberUtils.formatNumberToE164(networkPortion, "IN")
                            ?: if (networkPortion.startsWith("+")) {
                                networkPortion
                            } else if (networkPortion.length == 10) {
                                "+91$networkPortion"
                            } else {
                                networkPortion
                            }

                        if (formattedE164.isNotBlank() && formattedE164.length >= 10) {
                            normalizedE164List.add(formattedE164)
                        } else {
                            Toast.makeText(
                                this@FamilySetupActivity,
                                "Invalid phone number entered: $raw",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                    }
                }

                if (normalizedE164List.isEmpty()) {
                    Toast.makeText(
                        this@FamilySetupActivity,
                        "Please provide at least 1 valid family phone number.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Permanently seal the 3 slots + PIN hash in Device-Encrypted storage
                prefs.edit()
                    .putStringSet("whitelisted_e164", normalizedE164List)
                    .putString("family_pin_sha256", pinSha256)
                    .putBoolean("family_numbers_locked", true)
                    .commit()

                Toast.makeText(
                    this@FamilySetupActivity,
                    "Family numbers permanently locked (${normalizedE164List.size} slots active).",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }

        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(label1)
        root.addView(slot1)
        root.addView(label2)
        root.addView(slot2)
        root.addView(label3)
        root.addView(slot3)
        root.addView(pinLabel)
        root.addView(pinInput)
        root.addView(lockButton)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val dePrefs = createDeviceProtectedStorageContext().getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        if (dePrefs.getBoolean("family_numbers_locked", false)) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "You must lock 1-3 family numbers and a 6-digit PIN first.", Toast.LENGTH_SHORT).show()
        }
    }
}
