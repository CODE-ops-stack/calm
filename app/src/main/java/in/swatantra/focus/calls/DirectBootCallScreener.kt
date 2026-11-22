package `in`.swatantra.focus.calls

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService

class DirectBootCallScreener : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val engine = AntiGossipCallEngine(this)
        val rawNum = callDetails.handle?.schemeSpecificPart

        if (engine.isEmergencyOrCallbackImmune(rawNum)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val dePrefs = createDeviceProtectedStorageContext()
            .getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        val whitelist = dePrefs.getStringSet("whitelisted_family_numbers", null)
            ?: dePrefs.getStringSet("whitelisted_e164", emptySet()) ?: emptySet()
        val quotaExhausted = dePrefs.getBoolean("daily_call_quota_exhausted", false)

        if (quotaExhausted && !engine.isNumberWhitelisted(rawNum, whitelist)) {
            val rejectResponse = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .build()
            respondToCall(callDetails, rejectResponse)
        } else {
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}
