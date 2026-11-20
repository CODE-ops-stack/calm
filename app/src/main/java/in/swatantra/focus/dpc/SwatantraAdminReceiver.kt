package `in`.swatantra.focus.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import `in`.swatantra.focus.ui.MainActivity
import `in`.swatantra.focus.watchdog.SwatantraWatchdogService

class SwatantraAdminReceiver : DeviceAdminReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val deContext = context.createDeviceProtectedStorageContext()
        // T-06: Start systemExempted WatchdogService on Line 1 before the 20s boot timeout
        runCatching {
            deContext.startForegroundService(Intent(deContext, SwatantraWatchdogService::class.java))
        }

        val action = intent.action
        val pendingResult = goAsync()
        Thread {
            try {
                val dpm = deContext.getSystemService(DevicePolicyManager::class.java) ?: return@Thread
                val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

                if (dpm.isDeviceOwnerApp(deContext.packageName)) {
                    runCatching {
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_NETWORK_RESET)
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
                    }

                    if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == "in.swatantra.focus.ACTION_OTA_STATUS") {
                        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS) }
                        val homeIntent = Intent(deContext, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        runCatching { deContext.startActivity(homeIntent) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
