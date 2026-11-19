package `in`.swatantra.focus.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import `in`.swatantra.focus.classifier.FourBucketClassifier
import `in`.swatantra.focus.dpc.AtomicDeprovisionManager
import `in`.swatantra.focus.dpc.ParentTotpAuthenticator
import `in`.swatantra.focus.ledger.MonotonicDualClockLedger
import `in`.swatantra.focus.watchdog.SwatantraWatchdogService

class MainActivity : Activity() {

    companion object {
        const val MISSION_THEME_ID = "ON_A_MISSION_COMMAND_DECK_V7_1"
        const val TACTICAL_IF_THEN_DIRECTIVE_V7_1 = "TACTICAL_IF_THEN_DIRECTIVE_V7_1"
        const val PREFS_DIRECTIVES = "mission_directives_de"
        const val KEY_ACTIVE_DIRECTIVE = "active_if_then_directive"
        const val PREFS_STUDY = "dynamic_study_whitelist_de"
        const val KEY_STUDY_PKGS = "study_pkgs"
        const val KEY_STUDY_CONFIGURED = "study_apps_configured"
        const val KEY_MISSION_LOCK_ACTIVE = "mission_lock_active"

        val UNIVERSAL_IF_THEN_DIRECTIVES = listOf(
            "IF urge to browse hits -> THEN solve 2 practice questions now. Your big goal is waiting.",
            "IF stuck on a problem > 5m -> THEN write 1 known formula & test a simple case. Every mark counts.",
            "IF focus drops mid-lecture -> THEN pause & summarize the last 3 minutes on paper. Stay on mission."
        )
    }

    private lateinit var subHeader: TextView
    private lateinit var telemetryBanner: TextView

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

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(48, 48, 48, 48)
        }

        val header = TextView(this).apply {
            text = "SWATANTRA // ON A MISSION"
            textSize = 22f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
        val isDeviceOwner = dpm?.isDeviceOwnerApp(deContext.packageName) == true
        val studyPrefs = deContext.getSharedPreferences(PREFS_STUDY, Context.MODE_PRIVATE)
        val isLocked = studyPrefs.getBoolean(KEY_MISSION_LOCK_ACTIVE, false)

        subHeader = TextView(this).apply {
            text = when {
                isDeviceOwner && isLocked -> "MISSION LOCK ENGAGED \u2022 DISTRACTIONS SUSPENDED"
                isDeviceOwner -> "DEVICE OWNER: ACTIVE \u2022 READY FOR MISSION"
                else -> "DEVICE OWNER: INACTIVE \u2022 RUN ADB PROVISIONING"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 20)
        }

        telemetryBanner = TextView(this).apply {
            text = "TELEMETRY \u2022 SORTIE LOGGED: --m | COMMS SHIELD: --m / 60m (112 IMMUNE)"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(24, 22, 24, 22)
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = lp
        }

        // Load ledger telemetry asynchronously so UI never lags
        Thread {
            val (studyMs, callMs) = runCatching {
                MonotonicDualClockLedger(deContext)
                    .recordHeartbeatTick(isStudying = false, isOnNonWhitelistedCall = false, verifiedTlsOrGnssTimeMs = null)
            }.getOrDefault(Pair(0L, 0L))
            val studyMins = studyMs / 60_000L
            val callMins = callMs / 60_000L
            runOnUiThread {
                telemetryBanner.text = "TELEMETRY \u2022 SORTIE LOGGED: ${studyMins}m | COMMS SHIELD: ${callMins}m / 60m (112 IMMUNE)"
            }
        }.start()

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
            ).apply { setMargins(0, 0, 0, 16) }
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
                textSize = 14f
                setBackgroundColor(Color.parseColor(bgHex))
                setTextColor(Color.WHITE)
                setPadding(28, 28, 28, 28)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        val selectStudyAppsTile = createTile("[SELECT MY STUDY APPS] \u2022 Choose Allowed Apps", "#0F766E") {
            showStudyAppSelector(deContext, engageLockAfterSave = false)
        }

        val studyTile = createTile("[ENGAGE MISSION LOCK] \u2022 Lock Phone & Open Study Apps", "#059669") {
            val configured = studyPrefs.getBoolean(KEY_STUDY_CONFIGURED, false)
            if (!configured) {
                showStudyAppSelector(deContext, engageLockAfterSave = true)
            } else {
                engageMissionLockAsync(deContext)
            }
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

        val safeDeprovisionTile = createTile("[MISSION STAND-DOWN] \u2022 Parent Dual-Key Unlock", "#334155") {
            showDeprovisionGuard(deContext)
        }

        root.addView(header)
        root.addView(subHeader)
        root.addView(telemetryBanner)
        root.addView(directiveBanner)
        root.addView(cycleDirectiveTile)
        root.addView(selectStudyAppsTile)
        root.addView(studyTile)
        root.addView(parkingTile)
        root.addView(wifiSettingsTile)
        root.addView(emergencyDialTile)
        root.addView(safeDeprovisionTile)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val deContext = createDeviceProtectedStorageContext()
        if (!deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE).getBoolean("family_numbers_locked", false)) {
            startActivity(Intent(this, FamilySetupActivity::class.java))
            return
        }
        val studyPrefs = deContext.getSharedPreferences(PREFS_STUDY, Context.MODE_PRIVATE)
        if (studyPrefs.getBoolean(KEY_MISSION_LOCK_ACTIVE, false)) {
            val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
            if (dpm?.isDeviceOwnerApp(deContext.packageName) == true) {
                runCatching { startLockTask() }
            }
        }
    }

    private fun showStudyAppSelector(deContext: Context, engageLockAfterSave: Boolean) {
        Toast.makeText(this, "Loading installed apps...", Toast.LENGTH_SHORT).show()
        Thread {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = runCatching {
                packageManager.queryIntentActivities(launcherIntent, 0)
            }.getOrDefault(emptyList())

            val candidates = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == packageName) return@mapNotNull null
                    val label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(pkg)
                    pkg to label
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }

            val studyPrefs = deContext.getSharedPreferences(PREFS_STUDY, Context.MODE_PRIVATE)
            val savedSet = studyPrefs.getStringSet(KEY_STUDY_PKGS, emptySet())?.toMutableSet() ?: mutableSetOf()

            val labels = candidates.map { it.second }.toTypedArray()
            val checked = BooleanArray(candidates.size) { idx -> candidates[idx].first in savedSet }

            runOnUiThread {
                if (candidates.isEmpty()) {
                    Toast.makeText(this, "No launchable apps found.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                runCatching {
                    AlertDialog.Builder(this)
                        .setTitle("Select Your Allowed Study Apps")
                        .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                            checked[which] = isChecked
                        }
                        .setPositiveButton(if (engageLockAfterSave) "Save & Lock Now" else "Save Study Apps") { _, _ ->
                            val chosen = candidates.indices
                                .filter { checked[it] }
                                .map { candidates[it].first }
                                .toSet()
                            studyPrefs.edit()
                                .putStringSet(KEY_STUDY_PKGS, chosen)
                                .putBoolean(KEY_STUDY_CONFIGURED, true)
                                .commit()
                            Toast.makeText(this, "Saved ${chosen.size} study app(s).", Toast.LENGTH_SHORT).show()
                            if (engageLockAfterSave) {
                                engageMissionLockAsync(deContext)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }.start()
    }

    private fun engageMissionLockAsync(deContext: Context) {
        val studyPrefs = deContext.getSharedPreferences(PREFS_STUDY, Context.MODE_PRIVATE)
        val chosenStudySet = studyPrefs.getStringSet(KEY_STUDY_PKGS, emptySet()) ?: emptySet()
        subHeader.text = "ENGAGING MISSION LOCK..."

        Thread {
            val studySet = chosenStudySet + setOf("com.android.vending")
            runCatching { FourBucketClassifier(deContext).enforceFocusPolicy(studySet, emptySet()) }
            studyPrefs.edit().putBoolean(KEY_MISSION_LOCK_ACTIVE, true).commit()

            val launchable = chosenStudySet.mapNotNull { pkg ->
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                val label = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                label to launchIntent
            }.sortedBy { it.first.lowercase() }

            val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
            val isDeviceOwner = dpm?.isDeviceOwnerApp(deContext.packageName) == true

            runOnUiThread {
                if (isDeviceOwner) {
                    runCatching { startLockTask() }
                    subHeader.text = "MISSION LOCK ENGAGED \u2022 DISTRACTIONS SUSPENDED"
                } else {
                    subHeader.text = "DEVICE OWNER INACTIVE \u2022 RUN ADB PROVISIONING"
                }

                if (launchable.isNotEmpty()) {
                    val labels = launchable.map { it.first }.toTypedArray()
                    runCatching {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Mission Lock Active — Open Study App")
                            .setItems(labels) { _, which ->
                                runCatching { startActivity(launchable[which].second) }
                            }
                            .setNegativeButton("Stay on Command Deck", null)
                            .show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Mission Lock Engaged. Tap [SELECT MY STUDY APPS] to add apps.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showDeprovisionGuard(deContext: Context) {
        val callPrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        val studyPrefs = deContext.getSharedPreferences(PREFS_STUDY, Context.MODE_PRIVATE)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 44, 56, 32)
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }

        val guardTitle = TextView(this).apply {
            text = "PARENT DUAL-KEY UNLOCK"
            textSize = 18f
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val guardSubtitle = TextView(this).apply {
            text = "Unlock requires either the 6-Digit Parent PIN, a live 6-Digit Google Authenticator Code, or the Offline Backup Recovery Key."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 20)
        }

        val codeInput = EditText(this).apply {
            hint = "6-digit Parent PIN or Authenticator Code"
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(28, 20, 28, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = lp
        }

        val verifySixDigitButton = Button(this).apply {
            text = "VERIFY PIN / AUTHENTICATOR & UNLOCK"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = lp
        }

        val recoveryLabel = TextView(this).apply {
            text = "Lost PIN & Parent Phone? Enter Offline Recovery Key:"
            textSize = 12f
            setTextColor(Color.parseColor("#F59E0B"))
            setPadding(0, 0, 0, 6)
        }

        val recoveryInput = EditText(this).apply {
            hint = "SWT-XXXX-XXXX-XXXX"
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#161F30"))
            setPadding(28, 18, 28, 18)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = lp
        }

        val verifyRecoveryButton = Button(this).apply {
            text = "VERIFY RECOVERY KEY & UNLOCK"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setPadding(24, 22, 24, 22)
        }

        dialogLayout.addView(guardTitle)
        dialogLayout.addView(guardSubtitle)
        dialogLayout.addView(codeInput)
        dialogLayout.addView(verifySixDigitButton)
        dialogLayout.addView(recoveryLabel)
        dialogLayout.addView(recoveryInput)
        dialogLayout.addView(verifyRecoveryButton)

        val dialog = runCatching {
            AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setCancelable(true)
                .create()
        }.getOrNull()

        fun performSafeUnlock() {
            dialog?.dismiss()
            studyPrefs.edit().putBoolean(KEY_MISSION_LOCK_ACTIVE, false).commit()
            runCatching { stopLockTask() }
            Thread {
                AtomicDeprovisionManager(deContext).executeAtomicDeprovision()
                runOnUiThread {
                    subHeader.text = "UNLOCKED \u2022 DEVICE RESTORED CLEANLY"
                    Toast.makeText(this@MainActivity, "Phone unlocked and restored.", Toast.LENGTH_LONG).show()
                }
            }.start()
        }

        verifySixDigitButton.setOnClickListener {
            val entered = codeInput.text?.toString()?.trim().orEmpty()
            if (entered.length != 6 || !entered.all { it.isDigit() }) {
                Toast.makeText(this, "Enter a valid 6-digit PIN or Authenticator code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val storedPinHash = callPrefs.getString("family_pin_sha256", null)
            val pinMatches = storedPinHash != null && ParentTotpAuthenticator.sha256Hex(entered) == storedPinHash
            val storedTotpSecret = callPrefs.getString("parent_totp_secret", "").orEmpty()
            val totpMatches = ParentTotpAuthenticator.verifyTotpCode(storedTotpSecret, entered)

            if (pinMatches || totpMatches) {
                performSafeUnlock()
            } else {
                Toast.makeText(this, "Invalid Parent PIN or Authenticator code. Unlock denied.", Toast.LENGTH_SHORT).show()
            }
        }

        verifyRecoveryButton.setOnClickListener {
            val enteredRecovery = ParentTotpAuthenticator.normalizeRecoveryKey(recoveryInput.text?.toString().orEmpty())
            val storedRecoveryHash = callPrefs.getString("parent_recovery_sha256", null)
            if (enteredRecovery.isNotEmpty() && storedRecoveryHash != null &&
                ParentTotpAuthenticator.sha256Hex(enteredRecovery) == storedRecoveryHash
            ) {
                performSafeUnlock()
            } else {
                Toast.makeText(this, "Invalid Recovery Key. Unlock denied.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog?.show()
    }
}
