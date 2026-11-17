package `in`.swatantra.focus.classifier

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver

class StrictPlayStoreAppVerifier(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val pm = deContext.packageManager
    private val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

    private val curatedStudyPackages = setOf(
        "xyz.penpencil.physicswallah",
        "com.unacademyapp",
        "com.testbook.tbapp",
        "com.adda247.app",
        "in.oliveboard.prep",
        "com.parmaracademy",
        "com.infeepedia",
        "org.telegram.messenger",
        "com.google.android.youtube",
        "com.adobe.reader",
        "com.microsoft.office.onenote",
        "com.google.android.apps.docs",
        "com.google.android.apps.nbu.files",
        "com.ichi2.anki"
    )

    private val blacklistedCategories = setOf(
        ApplicationInfo.CATEGORY_GAME,
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_IMAGE,
        ApplicationInfo.CATEGORY_NEWS,
        ApplicationInfo.CATEGORY_MAPS
    )

    private val entertainmentTokens = listOf(
        "game", "gaming", "ludo", "bgmi", "pubg", "freefire", "codm", "chess", "rummy", "mpl", "dream11",
        "instagram", "facebook", "snapchat", "whatsapp", "twitter", "threads", "reddit", "discord", "tinder", "bumble",
        "netflix", "hotstar", "jiocinema", "primevideo", "sonyliv", "zee5", "mxplayer", "vlc", "spotify", "wynk", "gaana",
        "josh", "moj", "sharechat", "hipi", "chingari", "tiktok", "truecaller", "browser", "opera", "brave", "ucbrowser", "vpn", "proxy"
    )

    private val educationalTokens = listOf(
        "study", "exam", "gate", "upsc", "bpsc", "ssc", "rrb", "ncert", "test", "mock", "prep",
        "academy", "classes", "lecture", "notes", "pdf", "dictionary", "flashcard", "anki", "learn", "coding", "python", "c++"
    )

    fun handlePackageEvent(pkg: String) {
        if (pkg == deContext.packageName || pkg == "com.android.vending") return

        val isDeviceOwner = dpm?.isDeviceOwnerApp(deContext.packageName) == true

        // 1. Instant Default-Deny Quarantine (<50ms)
        if (isDeviceOwner) {
            runCatching { dpm.setPackagesSuspended(admin, arrayOf(pkg), true) }
        }

        // 2. 5-Gate Anti-Cheat Educational Verification
        val isEducational = evaluateStrictEducationalCriteria(pkg)

        // 3. Enforcement Action
        if (isEducational) {
            if (isDeviceOwner) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(pkg), false) }
            }
            val prefs = deContext.getSharedPreferences("dynamic_study_whitelist_de", Context.MODE_PRIVATE)
            val existing = prefs.getStringSet("study_pkgs", emptySet())?.toMutableSet() ?: mutableSetOf()
            existing.add(pkg)
            prefs.edit().putStringSet("study_pkgs", existing).commit()
        } else {
            if (isDeviceOwner) {
                runCatching { dpm.setApplicationHidden(admin, pkg, true) }
            }
            val callbackIntent = Intent(deContext, PackageInstallReceiver::class.java).apply {
                action = "in.swatantra.focus.ACTION_UNINSTALL_STATUS"
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                deContext,
                pkg.hashCode(),
                callbackIntent,
                flags
            )
            runCatching {
                pm.packageInstaller.uninstall(pkg, pendingIntent.intentSender)
            }
        }
    }

    fun evaluateStrictEducationalCriteria(pkg: String): Boolean {
        // Gate A: Curated Study Registry
        if (pkg in curatedStudyPackages) {
            return true
        }

        // Gate B: Official Play Store Source Check
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
        }.getOrNull()

        if (installer != "com.android.vending") {
            return false
        }

        // Gate C: Anti-Browser & Anti-VPN Intent Probe
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            `package` = pkg
        }
        val browserActivities = runCatching { pm.queryIntentActivities(browserIntent, 0) }.getOrDefault(emptyList())
        if (browserActivities.isNotEmpty()) {
            return false
        }

        val vpnIntent = Intent("android.net.VpnService").apply {
            `package` = pkg
        }
        val vpnServices = runCatching { pm.queryIntentServices(vpnIntent, 0) }.getOrDefault(emptyList())
        if (vpnServices.isNotEmpty()) {
            return false
        }

        // Gate D: OS Category & Entertainment Keyword Blacklist
        val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category in blacklistedCategories) {
                return false
            }
        }

        val appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault("")
        val lowerPkg = pkg.lowercase()
        val lowerLabel = appLabel.lowercase()

        if (entertainmentTokens.any { lowerPkg.contains(it) || lowerLabel.contains(it) }) {
            return false
        }

        // Gate E: Educational Keyword Requirement
        return educationalTokens.any { lowerPkg.contains(it) || lowerLabel.contains(it) }
    }
}

class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            val verifier = StrictPlayStoreAppVerifier(context)
            verifier.handlePackageEvent(pkg)
        }
    }
}
