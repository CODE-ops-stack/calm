package `in`.swatantra.focus

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import `in`.swatantra.focus.dpc.AtomicDeprovisionManager
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver

class SwatantraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val deContext = createDeviceProtectedStorageContext()
        installCrashLoopCircuitBreaker(deContext)
        ensureSafeFrpAndResumeInterruptedDeprovision(deContext)
    }

    private fun installCrashLoopCircuitBreaker(deContext: Context) {
        val prefs = deContext.getSharedPreferences("crash_circuit_breaker", Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val now = SystemClock.elapsedRealtime()
                val firstCrash = prefs.getLong("first_crash_elapsed", 0L)
                val count = if (now - firstCrash in 0..60_000L) {
                    prefs.getInt("crash_count", 0) + 1
                } else {
                    1
                }
                prefs.edit()
                    .putLong("first_crash_elapsed", if (count == 1) now else firstCrash)
                    .putInt("crash_count", count)
                    .commit()

                if (count >= 3) {
                    AtomicDeprovisionManager(deContext).emergencyLiftLockdownKeepDeviceOwner()
                }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun ensureSafeFrpAndResumeInterruptedDeprovision(deContext: Context) {
        val dpm = deContext.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(packageName)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val openFrp = FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionEnabled(false)
                    .build()
                dpm.setFactoryResetProtectionPolicy(admin, openFrp)
            }
        }

        val deprovManager = AtomicDeprovisionManager(deContext)
        if (deprovManager.isDeprovisionPending()) {
            deprovManager.executeAtomicDeprovision()
        }
    }
}
