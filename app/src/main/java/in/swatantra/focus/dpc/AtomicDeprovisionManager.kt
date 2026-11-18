package `in`.swatantra.focus.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

class AtomicDeprovisionManager(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val prefs = deContext.getSharedPreferences("deprovision_wal", Context.MODE_PRIVATE)
    private val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

    companion object {
        const val AUDIT_CLEARED_FACTORY_RESET = "DISALLOW_FACTORY_RESET"
        const val AUDIT_CLEARED_NETWORK_RESET = "DISALLOW_NETWORK_RESET"
    }

    fun isDeprovisionPending(): Boolean = prefs.getBoolean("DEPROVISION_IN_PROGRESS", false)

    fun emergencyLiftLockdownKeepDeviceOwner() {
        if (!dpm.isDeviceOwnerApp(deContext.packageName)) return
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, deContext.packageName) }
        val allPkgs = queryAllKnownPackages()
        runCatching { dpm.setPackagesSuspended(admin, allPkgs.toTypedArray(), false) }
        for (pkg in allPkgs) {
            runCatching { if (dpm.isApplicationHidden(admin, pkg)) dpm.setApplicationHidden(admin, pkg, false) }
        }
    }

    fun executeAtomicDeprovision(): Result<Unit> = runCatching {
        if (!dpm.isDeviceOwnerApp(deContext.packageName)) {
            prefs.edit().putBoolean("DEPROVISION_IN_PROGRESS", false).commit()
            return@runCatching
        }

        prefs.edit().putBoolean("DEPROVISION_IN_PROGRESS", true).commit()

        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        runCatching { dpm.setStatusBarDisabled(admin, false) }
        runCatching { dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE) }

        val allPkgs = queryAllKnownPackages()
        runCatching { dpm.setPackagesSuspended(admin, allPkgs.toTypedArray(), false) }

        for (pkg in allPkgs) {
            runCatching {
                if (dpm.isApplicationHidden(admin, pkg)) {
                    dpm.setApplicationHidden(admin, pkg, false)
                }
            }
            runCatching {
                if (dpm.isUninstallBlocked(admin, pkg)) {
                    dpm.setUninstallBlocked(admin, pkg, false)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { dpm.setUserControlDisabledPackages(admin, emptyList()) }
        }
        runCatching { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, deContext.packageName) }
        runCatching { dpm.setPermittedAccessibilityServices(admin, null) }
        runCatching { dpm.setPermittedInputMethods(admin, null) }

        // S-04 / Zero-Harm Guarantee: Every restriction added during lock mode MUST be cleared during deprovisioning
        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_CONFIG_DATE_TIME,
            UserManager.DISALLOW_PRINTING,
            UserManager.DISALLOW_APPS_CONTROL
        )
        for (r in restrictions) {
            runCatching { dpm.clearUserRestriction(admin, r) }
        }

        val stillHidden = allPkgs.filter { pkg ->
            runCatching { dpm.isApplicationHidden(admin, pkg) }.getOrDefault(false)
        }
        check(stillHidden.isEmpty()) {
            "Deprovision verification failed: packages still hidden ($stillHidden)"
        }

        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(deContext.packageName)
        prefs.edit().putBoolean("DEPROVISION_IN_PROGRESS", false).commit()
    }

    private fun queryAllKnownPackages(): List<String> {
        val pm = deContext.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        return pm.getInstalledPackages(flags).map { it.packageName }.distinct()
    }
}
