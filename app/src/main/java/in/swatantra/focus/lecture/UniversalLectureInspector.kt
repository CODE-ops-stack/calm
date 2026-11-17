package `in`.swatantra.focus.lecture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver

class UniversalLectureInspector : AccessibilityService() {

    private val blockedShortFormViewIds = setOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_watch_player",
        "com.google.android.youtube:id/shorts_pivot_button"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 1500L
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (containsBlockedDistractionNode(root)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {}

    fun isVerifiedActiveLectureSession(studyPackages: Set<String>): Boolean {
        val audioManager = getSystemService(AudioManager::class.java) ?: return false
        if (!audioManager.isMusicActive) return false

        val interactiveWindows = windows ?: return false
        val displayMetrics = resources.displayMetrics
        val totalScreenArea = (displayMetrics.widthPixels * displayMetrics.heightPixels).coerceAtLeast(1)

        var foregroundStudyArea = 0
        for (window in interactiveWindows) {
            if (window.isInPictureInPictureMode) return false
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val pkg = window.root?.packageName?.toString() ?: continue
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()

                if (pkg in studyPackages) {
                    if (window.root != null && containsBlockedDistractionNode(window.root)) {
                        return false
                    }
                    foregroundStudyArea = maxOf(foregroundStudyArea, area)
                }
            }
        }

        val coverageRatio = foregroundStudyArea.toDouble() / totalScreenArea.toDouble()
        return coverageRatio >= 0.35
    }

    private fun containsBlockedDistractionNode(root: AccessibilityNodeInfo): Boolean {
        for (viewId in blockedShortFormViewIds) {
            if (root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()) return true
        }
        return false
    }

    companion object {
        fun ensureAccessibilityServiceBound(context: Context) {
            val deContext = context.createDeviceProtectedStorageContext()
            val dpm = deContext.getSystemService(DevicePolicyManager::class.java) ?: return
            val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)
            if (!dpm.isDeviceOwnerApp(deContext.packageName)) return

            val flatComponent = ComponentName(deContext, UniversalLectureInspector::class.java).flattenToString()
            val current = Settings.Secure.getString(
                deContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            if (!current.contains(flatComponent)) {
                val updated = if (current.isBlank()) flatComponent else "$current:$flatComponent"
                dpm.setSecureSetting(admin, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                dpm.setSecureSetting(admin, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            }
        }
    }
}
