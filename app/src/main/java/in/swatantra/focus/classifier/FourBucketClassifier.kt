package `in`.swatantra.focus.classifier

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.webkit.WebView
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver
import `in`.swatantra.focus.network.SafeStudyDnsVpnService
import `in`.swatantra.focus.ui.MainActivity
import `in`.swatantra.focus.ui.SafeUrlInterceptorActivity

class FourBucketClassifier(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

    companion object {
        const val AUDIT_ENFORCED_FACTORY_RESET = "DISALLOW_FACTORY_RESET"
        const val AUDIT_ENFORCED_NETWORK_RESET = "DISALLOW_NETWORK_RESET"
    }

    private val immutableSystemCore = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.providers.settings",
        "com.android.providers.downloads",
        "com.android.providers.telephony",
        "com.android.providers.media",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.webview",
        "com.android.webview",
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
        "com.xiaomi.finddevice",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.vivo.abe",
        "com.vivo.permissionmanager"
    )

    private val oemFloatingWindowEscapes = setOf(
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.sidegesturepad",
        "com.miui.freeform",
        "com.miui.touchassistant",
        "com.google.android.googlequicksearchbox",
        "moe.shizuku.privileged.api",
        "com.draco.ladb",
        "com.rosan.dhizuku"
    )

    fun enforceFocusPolicy(whitelistedStudyApps: Set<String>, utilityApps: Set<String>) {
        if (dpm == null || !dpm.isDeviceOwnerApp(deContext.packageName)) return

        val activeWebViewPkg = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage()?.packageName
            } else null
        }.getOrNull()

        val protectedCore = buildSet {
            addAll(immutableSystemCore)
            add(deContext.packageName)
            if (activeWebViewPkg != null) add(activeWebViewPkg)
        }

        val lockTaskAllowed = (whitelistedStudyApps + utilityApps + setOf(
            deContext.packageName,
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.vending"
        )).distinct().toTypedArray()
        runCatching { dpm.setLockTaskPackages(admin, lockTaskAllowed) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val features = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
                dpm.setLockTaskFeatures(admin, features)
            }
        }

        val installed = runCatching {
            deContext.packageManager.getInstalledPackages(0).map { it.packageName }
        }.getOrDefault(emptyList())

        val toSuspend = installed.filter { pkg ->
            pkg !in protectedCore &&
            pkg !in whitelistedStudyApps &&
            pkg !in utilityApps &&
            (pkg in oemFloatingWindowEscapes || !isEssentialSystemPackage(pkg))
        }
        val toUnsuspend = (whitelistedStudyApps + utilityApps + protectedCore).toList()

        runCatching { dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false) }
        runCatching { dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true) }

        runCatching {
            val webFilter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            }
            dpm.addPersistentPreferredActivity(
                admin,
                webFilter,
                ComponentName(deContext, SafeUrlInterceptorActivity::class.java)
            )
        }

        runCatching {
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin,
                homeFilter,
                ComponentName(deContext, MainActivity::class.java)
            )
        }

        runCatching {
            dpm.setAlwaysOnVpnPackage(admin, deContext.packageName, true)
            deContext.startService(Intent(deContext, SafeStudyDnsVpnService::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { dpm.setUserControlDisabledPackages(admin, listOf(deContext.packageName)) }
        }

        // 4-Layer Zero-Harm Anti-Reset Shield during locked focus
        val focusRestrictions = buildList {
            add(UserManager.DISALLOW_FACTORY_RESET)
            add(UserManager.DISALLOW_NETWORK_RESET)
            add(UserManager.DISALLOW_SAFE_BOOT)
            add(UserManager.DISALLOW_ADD_USER)
            add(UserManager.DISALLOW_CONFIG_DATE_TIME)
            add(UserManager.DISALLOW_PRINTING)
            // Keep ADB alive ONLY on disposable Android Emulator so automated ADB QA can verify LockTask & Stand-Down
            if (!isEmulatorEnvironment()) {
                add(UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        }
        for (restriction in focusRestrictions) {
            runCatching { dpm.addUserRestriction(admin, restriction) }
        }
    }

    private fun isEmulatorEnvironment(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.contains("emulator") ||
               Build.HARDWARE.contains("ranchu") ||
               Build.HARDWARE.contains("goldfish") ||
               Build.MODEL.contains("sdk_gphone") ||
               Build.PRODUCT.contains("sdk_gphone") ||
               Build.PRODUCT.contains("emulator")
    }

    private fun isEssentialSystemPackage(pkg: String): Boolean {
        return pkg.startsWith("com.android.providers.") ||
               pkg.startsWith("com.samsung.android.knox") ||
               pkg.startsWith("com.qualcomm.") ||
               pkg.startsWith("com.mediatek.")
    }
}
