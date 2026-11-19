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

    companion object {
        const val MISSION_THEME_ID = "ON_A_MISSION_COMMAND_DECK_V7_1"
        const val TACTICAL_IF_THEN_DIRECTIVE_V7_1 = "TACTICAL_IF_THEN_DIRECTIVE_V7_1"
        const val PREFS_DIRECTIVES = "mission_directives_de"
        const val KEY_ACTIVE_DIRECTIVE = "active_if_then_directive"
        val UNIVERSAL_IF_THEN_DIRECTIVES = listOf(
            "IF urge to browse hits -> THEN solve 2 practice questions now. Your big goal is waiting.",
            "IF stuck on a problem > 5m -> THEN write 1 known formula & test a simple case. Every mark counts.",
            "IF focus drops mid-lecture -> THEN pause & summarize the last 3 minutes on paper. Stay on mission."
        )
    }

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

        // Live Mission Telemetry Status Bar
        val (studyMs, callMs) = runCatching {
            `in`.swatantra.focus.ledger.MonotonicDualClockLedger(deContext)
                .recordHeartbeatTick(isStudying = false, isOnNonWhitelistedCall = false, verifiedTlsOrGnssTimeMs = null)
        }.getOrDefault(Pair(0L, 0L))
        val studyMins = studyMs / 60_000L
        val callMins = callMs / 60_000L

        // Flight-Deck Deep-Space Obsidian (#0B0F17) background
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(56, 56, 56, 56)
        }

        val header = TextView(this).apply {
            text = "SWATANTRA // ON A MISSION"
            textSize = 22f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val subHeader = TextView(this).apply {
            text = "MISSION OBJECTIVE: DEEP WORK COMMAND \u2022 ZERO ATTENTION RESIDUE"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 24)
        }

        val telemetryBanner = TextView(this).apply {
            text = "TELEMETRY \u2022 SORTIE LOGGED: ${studyMins}m | COMMS SHIELD: ${callMins}m / 60m (112 IMMUNE)"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 28) }
            layoutParams = lp
        }

        val directivePrefs = deContext.getSharedPreferences(PREFS_DIRECTIVES, Context.MODE_PRIVATE)
        var activeDirective = directivePrefs.getString(KEY_ACTIVE_DIRECTIVE, UNIVERSAL_IF_THEN_DIRECTIVES[0])
            ?: UNIVERSAL_IF_THEN_DIRECTIVES[0]

        val directiveBanner = TextView(this).apply {
            text = "ARMED IF-THEN DIRECTIVE:\n$activeDirective"
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(24, 20, 24, 20)
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = lp
        }

        val cycleDirectiveTile = Button(this).apply {
            text = "[ARM IF-THEN DIRECTIVE] \u2022 Cycle Motivating Rule"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.parseColor("#F59E0B"))
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = lp
            setOnClickListener {
                val currentIdx = UNIVERSAL_IF_THEN_DIRECTIVES.indexOf(activeDirective)
                val nextIdx = (currentIdx + 1) % UNIVERSAL_IF_THEN_DIRECTIVES.size
                activeDirective = UNIVERSAL_IF_THEN_DIRECTIVES[nextIdx]
                directivePrefs.edit().putString(KEY_ACTIVE_DIRECTIVE, activeDirective).commit()
                directiveBanner.text = "ARMED IF-THEN DIRECTIVE:\n$activeDirective"
                Toast.makeText(this@MainActivity, "If-Then Directive Armed", Toast.LENGTH_SHORT).show()
            }
        }

        fun createTile(label: String, bgHex: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 15f
                setBackgroundColor(Color.parseColor(bgHex))
                setTextColor(Color.WHITE)
                setPadding(32, 32, 32, 32)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 20) }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        val studyTile = createTile("[ENGAGE MISSION LOCK] \u2022 Launch Deep Study Sortie", "#059669") {
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

        val parkingTile = createTile("[TACTICAL INTERCEPT] \u2022 Park Urge / Doubt (10-Min Rule)", "#D97706") {
            startActivity(Intent(this, SafeUrlInterceptorActivity::class.java))
        }

        val wifiSettingsTile = createTile("[COMMS & UPLINK] \u2022 Safe Wi-Fi & Network Panel", "#1E293B") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } else {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }

        val emergencyDialTile = createTile("[PRIORITY CHANNEL] \u2022 Family (3-Slot) & 112 Emergency", "#0F766E") {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        }

        val safeDeprovisionTile = createTile("[MISSION STAND-DOWN] \u2022 Dual-Key Deprovision Guard", "#334155") {
            showDeprovisionGuard(deContext)
        }

        root.addView(header)
        root.addView(subHeader)
        root.addView(telemetryBanner)
        root.addView(directiveBanner)
        root.addView(cycleDirectiveTile)
        root.addView(studyTile)
        root.addView(parkingTile)
        root.addView(wifiSettingsTile)
        root.addView(emergencyDialTile)
        root.addView(safeDeprovisionTile)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val deContext = createDeviceProtectedStorageContext()
        if (!deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE).getBoolean("family_numbers_locked", false)) {
            startActivity(Intent(this, FamilySetupActivity::class.java))
            return
        }
        // Keep pinned when Swatantra is the active LockTask home
        runCatching { startLockTask() }
    }

    // ── Dual-Safe Deprovision Guard ───────────────────────────────────────────
    /**
     * Shows an inline AlertDialog offering two safe paths to deprovision:
     *   Option A: Immediate 6-digit Family PIN verification (SHA-256)
     *   Option B: 24-hour cooling-off timer fallback (survives reboots)
     * Both paths require Device-Encrypted SharedPreferences.
     */
    private fun showDeprovisionGuard(deContext: Context) {
        val walPrefs = deContext.getSharedPreferences("deprovision_wal", Context.MODE_PRIVATE)
        val callPrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)

        // Elapsed duration calculation that survives reboots
        val requestedElapsedMs = walPrefs.getLong("deprovision_requested_elapsed_ms", -1L)
        val requestedWallMs = walPrefs.getLong("deprovision_requested_wall_ms", -1L)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val nowWallMs = System.currentTimeMillis()

        val elapsedSinceRequestMs = if (requestedElapsedMs > 0L && nowElapsedMs >= requestedElapsedMs) {
            nowElapsedMs - requestedElapsedMs
        } else if (requestedWallMs > 0L && nowWallMs >= requestedWallMs) {
            // Fallback across reboot when elapsedRealtime reset to 0
            nowWallMs - requestedWallMs
        } else {
            -1L
        }

        val isTimerRunning = elapsedSinceRequestMs >= 0L
        val coolingOffComplete = isTimerRunning && elapsedSinceRequestMs >= 86_400_000L

        if (coolingOffComplete) {
            // 24 hours elapsed — clear WAL timestamps and allow PIN-free deprovisioning
            walPrefs.edit()
                .remove("deprovision_requested_elapsed_ms")
                .remove("deprovision_requested_wall_ms")
                .commit()
            runCatching { stopLockTask() }
            AtomicDeprovisionManager(deContext).executeAtomicDeprovision()
            return
        }

        // Build the guard dialog with Command Deck dark theme
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 32)
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }

        val guardTitle = TextView(this).apply {
            text = "Deprovision Guard"
            textSize = 18f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val guardSubtitle = TextView(this).apply {
            text = "Choose a safe path to restore this phone:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        // ── Option A: PIN entry ───────────────────────────────────────────────
        val optionALabel = TextView(this).apply {
            text = "Option A — Immediate: Enter 6-Digit Family Unlock PIN"
            textSize = 13f
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 0, 0, 6)
        }

        val pinInput = EditText(this).apply {
            hint = "6-digit PIN"
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#161F30"))
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
            setBackgroundColor(Color.parseColor("#059669"))
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
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val optionBLabel = TextView(this).apply {
            text = "Option B — Start 24-Hour Anti-Impulse Cooling-Off Timer"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 6)
        }

        val timerStatusText = TextView(this).apply {
            if (isTimerRunning) {
                val remainingMs = maxOf(0L, 86_400_000L - elapsedSinceRequestMs)
                val remainingHours = remainingMs / 3_600_000L
                val remainingMins = (remainingMs % 3_600_000L) / 60_000L
                text = "Timer running: ~${remainingHours}h ${remainingMins}m remaining"
            } else {
                text = "No timer started yet."
            }
            textSize = 12f
            setTextColor(Color.parseColor("#F59E0B"))
            setPadding(0, 0, 0, 10)
        }

        val startTimerButton = Button(this).apply {
            text = "Start 24-Hour Cooling-Off Timer"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            isEnabled = !isTimerRunning   // only allow starting once
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
                // Clear WAL timestamps before deprovisioning
                walPrefs.edit()
                    .remove("deprovision_requested_elapsed_ms")
                    .remove("deprovision_requested_wall_ms")
                    .commit()
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
            val nowWallMsForTimer = System.currentTimeMillis()
            walPrefs.edit()
                .putLong("deprovision_requested_elapsed_ms", nowElapsedForTimer)
                .putLong("deprovision_requested_wall_ms", nowWallMsForTimer)
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
}
