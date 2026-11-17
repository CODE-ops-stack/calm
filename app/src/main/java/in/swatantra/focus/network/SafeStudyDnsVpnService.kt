package `in`.swatantra.focus.network

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class SafeStudyDnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnInterface == null) {
            runCatching {
                val builder = Builder()
                    .setSession("SwatantraFocusDNS")
                    .setMtu(1500)
                    .addAddress("10.254.254.1", 32)
                    // Block Gboard GIF/WebView network traffic by routing only keyboard UID to local sinkhole
                    .addAllowedApplication("com.google.android.inputmethod.latin")
                vpnInterface = builder.establish()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        super.onDestroy()
    }
}
