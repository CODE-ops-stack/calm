package `in`.swatantra.focus.ota

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver
import java.io.File
import java.security.MessageDigest

class FastOtaUpdater(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

    @Synchronized
    fun installVerifiedOtaApk(apkFile: File, expectedSha256Hex: String): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) { "OTA APK missing or empty" }

        val actualSha256 = sha256Hex(apkFile)
        require(actualSha256.equals(expectedSha256Hex, ignoreCase = true)) {
            "OTA SHA-256 mismatch: expected=$expectedSha256Hex actual=$actualSha256"
        }

        val pm = deContext.packageManager
        val archiveFlags = PackageManager.GET_SIGNING_CERTIFICATES
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            ?: error("Corrupt APK archive")
        require(archiveInfo.packageName == deContext.packageName) { "Package mismatch" }

        val currentInfo = pm.getPackageInfo(deContext.packageName, archiveFlags)
        require(archiveInfo.longVersionCode > currentInfo.longVersionCode) {
            "Downgrade attack blocked"
        }

        val currentSig = currentInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        val archiveSig = archiveInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        require(currentSig != null && archiveSig != null && currentSig.contentEquals(archiveSig)) {
            "APK signing certificate mismatch"
        }

        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(deContext.packageName)
            setSize(apkFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("swatantra_ota.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input -> input.copyTo(out, bufferSize = 65536) }
                session.fsync(out)
            }

            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            val callbackIntent = Intent(deContext, SwatantraAdminReceiver::class.java).apply {
                action = "in.swatantra.focus.ACTION_OTA_STATUS"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                deContext, sessionId, callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(65536)
            var r: Int
            while (fis.read(buf).also { r = it } != -1) {
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
