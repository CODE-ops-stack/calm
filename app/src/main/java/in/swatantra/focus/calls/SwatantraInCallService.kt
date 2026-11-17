package `in`.swatantra.focus.calls

import android.content.Context
import android.telecom.Call
import android.telecom.InCallService

class SwatantraInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        evaluateCallPolicy(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                evaluateCallPolicy(call)
            }
            override fun onChildrenChanged(call: Call, children: MutableList<Call>?) {
                evaluateCallPolicy(call)
            }
        })
    }

    private fun evaluateCallPolicy(call: Call) {
        val dePrefs = createDeviceProtectedStorageContext()
            .getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
        val whitelist = dePrefs.getStringSet("whitelisted_e164", emptySet()) ?: emptySet()
        val quotaExhausted = dePrefs.getBoolean("daily_call_quota_exhausted", false)
        val engine = AntiGossipCallEngine(this)
        val isNonWhitelistedActive = call.state == Call.STATE_ACTIVE &&
            engine.shouldMeterOrDisconnectCall(call, whitelist, quotaExhausted)
        dePrefs.edit().putBoolean("active_metered_pstn_call", isNonWhitelistedActive).commit()
    }
}
