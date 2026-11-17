package `in`.swatantra.focus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import `in`.swatantra.focus.classifier.FourBucketClassifier
import `in`.swatantra.focus.dpc.AtomicDeprovisionManager
import `in`.swatantra.focus.watchdog.SwatantraWatchdogService

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
            text = "Deep Work Focus OS • Zero Attention Residue"
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

        val safeDeprovisionTile = createTile("5. Safe Atomic 8-Step Deprovision (Restore Phone)", "#6B7280") {
            runCatching { stopLockTask() }
            AtomicDeprovisionManager(deContext).executeAtomicDeprovision()
        }

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
}
