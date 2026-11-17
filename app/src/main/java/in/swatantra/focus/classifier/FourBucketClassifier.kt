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
        if (!dpm.isDeviceOwnerApp(deContext.packageName)) return

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
            "com.samsung.android.incallui"
        )).distinct().toTypedArray()
        dpm.setLockTaskPackages(admin, lockTaskAllowed)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val features = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
            dpm.setLockTaskFeatures(admin, features)
        }

        val installed = deContext.packageManager.getInstalledPackages(0).map { it.packageName }
        val toSuspend = installed.filter { pkg ->
            pkg !in protectedCore &&
            pkg !in whitelistedStudyApps &&
            pkg !in utilityApps &&
            (pkg in oemFloatingWindowEscapes || !isEssentialSystemPackage(pkg))
        }
        val toUnsuspend = (whitelistedStudyApps + utilityApps + protectedCore).toList()

        runCatching { dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false) }
        runCatching { dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true) }

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

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            homeFilter,
            ComponentName(deContext, MainActivity::class.java)
        )

        runCatching {
            dpm.setAlwaysOnVpnPackage(admin, deContext.packageName, true)
            deContext.startService(Intent(deContext, SafeStudyDnsVpnService::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm.setUserControlDisabledPackages(admin, listOf(deContext.packageName))
        }
        dpm.addUserRestriction(admin, UserManager.DISALLOW_PRINTING)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
    }

    private fun isEssentialSystemPackage(pkg: String): Boolean {
        return pkg.startsWith("com.android.providers.") ||
               pkg.startsWith("com.samsung.android.knox") ||
               pkg.startsWith("com.qualcomm.") ||
               pkg.startsWith("com.mediatek.")
    }
}
