package `in`.swatantra.focus.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import `in`.swatantra.focus.classifier.FourBucketClassifier
import `in`.swatantra.focus.dpc.AtomicDeprovisionManager
import `in`.swatantra.focus.watchdog.SwatantraWatchdogService
import java.security.MessageDigest

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deContext = createDeviceProtectedStorageContext()
        runCatching {
            deContext.startForegroundService(Intent(deContext, SwatantraWatchdogService::class.java))
        }

        val callPrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        if (!callPrefs.getBoolean("family_numbers_locked", false)) {
            startActivity(Intent(this, FamilySetupActivity::class.java))
        }

        // Warm-Paper Monochrome 4-Tile Home Screen (James Clear Atomic Habits: zero visual dopamine triggers)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F7F5F0"))
            setPadding(56, 72, 56, 72)
        }

        val header = TextView(this).apply {
            text = "SWATANTRA v7.1"
            textSize = 24f
            setTextColor(Color.parseColor("#1E242B"))
        }

        val subHeader = TextView(this).apply {
            text = "Deep Work Focus OS \u2022 Zero Attention Residue"
            textSize = 14f
            setTextColor(Color.parseColor("#4A5568"))
            setPadding(0, 8, 0, 48)
        }

        fun createTile(label: String, bgHex: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 16f
                setBackgroundColor(Color.parseColor(bgHex))
                setTextColor(Color.WHITE)
                setPadding(32, 36, 32, 36)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 28) }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        val studyTile = createTile("1. Start Deep Study Block (Lock Focus)", "#2B4C7E") {
            val baseStudySet = setOf(
                "xyz.penpencil.physicswallah",
                "com.unacademyapp",
                "org.telegram.messenger",
                "com.adobe.reader"
            )
            val dynamicPrefs = deContext.getSharedPreferences("dynamic_study_whitelist_de", Context.MODE_PRIVATE)
            val dynamicStudySet = dynamicPrefs.getStringSet("study_pkgs", emptySet()) ?: emptySet()
            val studySet = baseStudySet + dynamicStudySet
            FourBucketClassifier(deContext).enforceFocusPolicy(studySet, emptySet())
            runCatching { startLockTask() }
        }

        val parkingTile = createTile("2. Thought & Urge Parking Lot (10-Min Rule)", "#3B5998") {
            startActivity(Intent(this, SafeUrlInterceptorActivity::class.java))
        }

        val wifiSettingsTile = createTile("3. Safe Settings Hub (Wi-Fi & Internet)", "#4A5568") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } else {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }

        val emergencyDialTile = createTile("4. Parent & Emergency Dialer (112 Immune)", "#2D6A4F") {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        }

        // ── Tile 5: Dual-Safe Anti-Impulse Deprovision Guard ─────────────────
        val safeDeprovisionTile = createTile("5. Safe Atomic 8-Step Deprovision (Restore Phone)", "#6B7280") {
            showDeprovisionGuard(deContext)
        }
        // ─────────────────────────────────────────────────────────────────────

        root.addView(header)
        root.addView(subHeader)
        root.addView(studyTile)
        root.addView(parkingTile)
        root.addView(wifiSettingsTile)
        root.addView(emergencyDialTile)
        root.addView(safeDeprovisionTile)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Keep pinned when Swatantra is the active LockTask home
        runCatching { startLockTask() }
    }

    // ── Dual-Safe Deprovision Guard ───────────────────────────────────────────
    /**
     * Shows an inline AlertDialog offering two safe paths to deprovision:
     *   Option A: Immediate 6-digit Family PIN verification (SHA-256)
     *   Option B: 24-hour cooling-off timer fallback
     * Both paths require Device-Encrypted SharedPreferences.
     */
    private fun showDeprovisionGuard(deContext: Context) {
        val walPrefs = deContext.getSharedPreferences("deprovision_wal", Context.MODE_PRIVATE)
        val callPrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)

        // Check if the 24-hour cooling-off timer has already elapsed
        val requestedElapsedMs = walPrefs.getLong("deprovision_requested_elapsed_ms", -1L)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val coolingOffComplete = requestedElapsedMs > 0L &&
                (nowElapsedMs - requestedElapsedMs) >= 86_400_000L

        if (coolingOffComplete) {
            // 24 hours elapsed — allow PIN-free deprovisioning
            runCatching { stopLockTask() }
            AtomicDeprovisionManager(deContext).executeAtomicDeprovision()
            return
        }

        // Build the guard dialog
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 32)
            setBackgroundColor(Color.parseColor("#F7F5F0"))
        }

        val guardTitle = TextView(this).apply {
            text = "Deprovision Guard"
            textSize = 18f
            setTextColor(Color.parseColor("#1E242B"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val guardSubtitle = TextView(this).apply {
            text = "Choose a safe path to restore this phone:"
            textSize = 13f
            setTextColor(Color.parseColor("#4A5568"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        // ── Option A: PIN entry ───────────────────────────────────────────────
        val optionALabel = TextView(this).apply {
            text = "Option A — Immediate: Enter 6-Digit Family Unlock PIN"
            textSize = 13f
            setTextColor(Color.parseColor("#2B4C7E"))
            setPadding(0, 0, 0, 6)
        }

        val pinInput = EditText(this).apply {
            hint = "6-digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(Color.parseColor("#1E242B"))
            setBackgroundColor(Color.parseColor("#EAE6DF"))
            setPadding(32, 20, 32, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = lp
        }

        val verifyPinButton = Button(this).apply {
            text = "Verify PIN & Deprovision Now"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#2B4C7E"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            layoutParams = lp
        }

        // ── Option B: 24-hour cooling-off ─────────────────────────────────────
        val divider = TextView(this).apply {
            text = "— or —"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val optionBLabel = TextView(this).apply {
            text = "Option B — Start 24-Hour Anti-Impulse Cooling-Off Timer"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 6)
        }

        val timerStatusText = TextView(this).apply {
            if (requestedElapsedMs > 0L) {
                val remainingMs = 86_400_000L - (nowElapsedMs - requestedElapsedMs)
                val remainingHours = remainingMs / 3_600_000L
                val remainingMins = (remainingMs % 3_600_000L) / 60_000L
                text = "Timer running: ~${remainingHours}h ${remainingMins}m remaining"
            } else {
                text = "No timer started yet."
            }
            textSize = 12f
            setTextColor(Color.parseColor("#4A5568"))
            setPadding(0, 0, 0, 10)
        }

        val startTimerButton = Button(this).apply {
            text = "Start 24-Hour Cooling-Off Timer"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            isEnabled = requestedElapsedMs <= 0L   // only allow starting once
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 0) }
            layoutParams = lp
        }

        dialogLayout.addView(guardTitle)
        dialogLayout.addView(guardSubtitle)
        dialogLayout.addView(optionALabel)
        dialogLayout.addView(pinInput)
        dialogLayout.addView(verifyPinButton)
        dialogLayout.addView(divider)
        dialogLayout.addView(optionBLabel)
        dialogLayout.addView(timerStatusText)
        dialogLayout.addView(startTimerButton)

        val dialog = runCatching {
            AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setCancelable(true)
                .create()
        }.getOrNull()

        // Wire Option A — PIN verify
        verifyPinButton.setOnClickListener {
            val enteredPin = pinInput.text?.toString()?.trim().orEmpty()
            if (enteredPin.length != 6 || !enteredPin.all { it.isDigit() }) {
                Toast.makeText(this, "Enter a valid 6-digit PIN.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredHash = MessageDigest.getInstance("SHA-256")
                .digest(enteredPin.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val storedHash = callPrefs.getString("family_pin_sha256", null)
            if (storedHash != null && enteredHash == storedHash) {
                dialog?.dismiss()
                runCatching { stopLockTask() }
                AtomicDeprovisionManager(deContext).executeAtomicDeprovision()
            } else {
                Toast.makeText(this, "Incorrect PIN. Deprovision denied.", Toast.LENGTH_SHORT).show()
            }
        }

        // Wire Option B — 24-hour timer
        startTimerButton.setOnClickListener {
            val nowElapsedForTimer = SystemClock.elapsedRealtime()
            val nowWallMs = System.currentTimeMillis()
            walPrefs.edit()
                .putLong("deprovision_requested_elapsed_ms", nowElapsedForTimer)
                .putLong("deprovision_requested_wall_ms", nowWallMs)
                .commit()
            Toast.makeText(
                this,
                "24-hour cooling-off timer started. Return tomorrow to deprovision without PIN.",
                Toast.LENGTH_LONG
            ).show()
            startTimerButton.isEnabled = false
            timerStatusText.text = "Timer running: ~24h 0m remaining"
            dialog?.dismiss()
        }

        dialog?.show()
    }
    // ─────────────────────────────────────────────────────────────────────────
}
