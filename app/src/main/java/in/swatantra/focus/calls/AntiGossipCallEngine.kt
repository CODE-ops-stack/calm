package `in`.swatantra.focus.calls

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import `in`.swatantra.focus.dpc.SwatantraAdminReceiver

class AntiGossipCallEngine(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val prefs = deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
    private val telecomManager = deContext.getSystemService(TelecomManager::class.java)
    private val telephonyManager = deContext.getSystemService(TelephonyManager::class.java)
    private val audioManager = deContext.getSystemService(AudioManager::class.java)
    private val dpm = deContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(deContext, SwatantraAdminReceiver::class.java)

    private val emergencyNumbers = setOf(
        "112", "100", "101", "102", "108", "1091", "1098", "181", "1930", "911", "999"
    )

    fun isEmergencyOrCallbackImmune(rawNumber: String?): Boolean {
        val inEmergencyCall = runCatching { telecomManager?.javaClass?.getMethod("isInEmergencyCall")?.invoke(telecomManager) as? Boolean == true }.getOrDefault(false)
        if (inEmergencyCall) return true

        val lastEmergencyElapsed = prefs.getLong("last_emergency_dial_elapsed", -1L)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (lastEmergencyElapsed > 0L && (nowElapsed - lastEmergencyElapsed) in 0..1_800_000L) {
            return true
        }

        if (rawNumber.isNullOrBlank()) return false
        val stripped = PhoneNumberUtils.extractNetworkPortion(rawNumber).trim()
        if (stripped in emergencyNumbers) {
            recordEmergencyDialWindow()
            return true
        }
        val sysEmergency = runCatching { telephonyManager?.isEmergencyNumber(stripped) == true }.getOrDefault(false)
        if (sysEmergency) {
            recordEmergencyDialWindow()
            return true
        }
        return false
    }

    fun recordEmergencyDialWindow() {
        prefs.edit().putLong("last_emergency_dial_elapsed", SystemClock.elapsedRealtime()).commit()
    }

    fun isNumberWhitelisted(rawNumber: String?, whitelistedE164Set: Set<String>): Boolean {
        if (isEmergencyOrCallbackImmune(rawNumber)) return true
        if (rawNumber.isNullOrBlank()) return false

        val cleanNetworkNum = PhoneNumberUtils.extractNetworkPortion(rawNumber)
        val normalizedE164 = PhoneNumberUtils.formatNumberToE164(cleanNetworkNum, "IN") ?: cleanNetworkNum

        if (normalizedE164 in whitelistedE164Set) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return whitelistedE164Set.any { whitelisted ->
                PhoneNumberUtils.areSamePhoneNumber(cleanNetworkNum, whitelisted, "IN")
            }
        }
        return false
    }

    fun shouldMeterOrDisconnectCall(call: Call, whitelistedE164Set: Set<String>, quotaExhausted: Boolean): Boolean {
        val details = call.details ?: return false
        val handleNum = details.handle?.schemeSpecificPart
        if (isEmergencyOrCallbackImmune(handleNum)) return false

        val isConference = (details.callProperties and Call.Details.PROPERTY_CONFERENCE) != 0
        if (isConference) {
            val children = call.children
            val anyNonWhitelistedLeg = children.isEmpty() || children.any { leg ->
                val legNum = leg.details?.handle?.schemeSpecificPart
                !isNumberWhitelisted(legNum, whitelistedE164Set)
            }
            if (anyNonWhitelistedLeg && quotaExhausted) {
                call.disconnect()
                return true
            }
            return anyNonWhitelistedLeg
        }

        val whitelisted = isNumberWhitelisted(handleNum, whitelistedE164Set)
        if (!whitelisted && quotaExhausted) {
            call.disconnect()
            return true
        }
        return !whitelisted
    }

    fun isVoipCallActive(): Boolean {
        val mode = audioManager?.mode ?: return false
        return mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL
    }

    fun enforceVoipMicrophonePolicy(voipApps: Set<String>, quotaExhausted: Boolean) {
        if (dpm == null || !dpm.isDeviceOwnerApp(deContext.packageName)) return
        val grantState = if (quotaExhausted) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        } else {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        }
        for (pkg in voipApps) {
            runCatching {
                dpm.setPermissionGrantState(admin, pkg, Manifest.permission.RECORD_AUDIO, grantState)
            }
        }
    }
}
