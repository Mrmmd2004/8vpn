package app.vpn8

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SettingsStore.init(context)
        val s = SettingsStore.current
        if (!s.autoStart) return
        if (s.vpnMode && VpnService.prepare(context) != null) return // permission not granted yet
        TunnelService.start(context)
    }
}
