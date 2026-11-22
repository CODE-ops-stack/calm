package `in`.swatantra.focus.watchdog

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import `in`.swatantra.focus.calls.AntiGossipCallEngine
import `in`.swatantra.focus.classifier.applyMissionLockAdultAndAntiBypassShield
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver
import `in`.swatantra.focus.lecture.UniversalLectureInspector
import `in`.swatantra.focus.ledger.MonotonicDualClockLedger

class SwatantraWatchdogService : Service(), AlarmManager.OnAlarmListener {
    private lateinit var ledger: MonotonicDualClockLedger
    private lateinit var callEngine: AntiGossipCallEngine
    private lateinit var alarmManager: AlarmManager
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startSystemExemptForeground()
        val deContext = createDeviceProtectedStorageContext()
        ledger = MonotonicDualClockLedger(deContext)
        callEngine = AntiGossipCallEngine(deContext)
        alarmManager = deContext.getSystemService(AlarmManager::class.java)
        scheduleNextHeartbeat()
    }

    private fun startSystemExemptForeground() {
        val channelId = "swatantra_focus_channel"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(channelId) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(channelId, "Swatantra Deep Work Shield", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Swatantra Deep Work Active")
            .setContentText("Zero-lag focus protection & emergency call shield enabled")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(1001, notif)
        }
    }

    override fun onAlarm() {
        runCatching {
            UniversalLectureInspector.ensureAccessibilityServiceBound(this)
            val deContext = createDeviceProtectedStorageContext()
            val dePrefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
            val pstnActive = dePrefs.getBoolean("active_metered_pstn_call", false)
            val voipActive = callEngine.isVoipCallActive()
            val (_, callMs) = ledger.recordHeartbeatTick(
                isStudying = false,
                isOnNonWhitelistedCall = pstnActive || voipActive,
                verifiedTlsOrGnssTimeMs = null
            )
            if (callMs >= 3_600_000L) {
                callEngine.enforceVoipMicrophonePolicy(
                    setOf("com.whatsapp", "org.telegram.messenger", "com.instagram.android", "com.discord"),
                    quotaExhausted = true
                )
            }

            // ── Mission-Lock-only: re-apply adult-content ban + entertainment cap ──
            val studyPrefs = deContext.getSharedPreferences("dynamic_study_whitelist_de", Context.MODE_PRIVATE)
            val isLocked = studyPrefs.getBoolean("mission_lock_active", false)
            if (isLocked) {
                // Re-apply shield every heartbeat to prevent drift
                applyMissionLockAdultAndAntiBypassShield(deContext)

                // Enforce entertainment budget: suspend all stored entertainment packages
                if (ledger.isEntertainmentExhausted()) {
                    val dpm = deContext.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                    val admin = android.content.ComponentName(deContext, SwatantraAdminReceiver::class.java)
                    if (dpm?.isDeviceOwnerApp(deContext.packageName) == true) {
                        val entertainmentPkgs = deContext
                            .getSharedPreferences("entertainment_prefs_de", Context.MODE_PRIVATE)
                            .getStringSet("entertainment_pkgs_to_suspend", emptySet())
                            ?: emptySet()
                        if (entertainmentPkgs.isNotEmpty()) {
                            runCatching {
                                dpm.setPackagesSuspended(admin, entertainmentPkgs.toTypedArray(), true)
                            }
                        }
                    }
                }
            }
        }
        scheduleNextHeartbeat()
    }

    private fun scheduleNextHeartbeat() {
        val nextTrigger = SystemClock.elapsedRealtime() + 60_000L
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            nextTrigger,
            "SwatantraHeartbeat60s",
            this,
            mainHandler
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
