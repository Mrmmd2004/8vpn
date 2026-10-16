package app.vpn8

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QsTile : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = CoroutineScope(Dispatchers.Main).launch {
            Engine.status.collect { render(it.phase) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
    }

    private fun render(p: Phase) {
        val t = qsTile ?: return
        t.state = if (p == Phase.IDLE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.label = getString(R.string.app_name)
        t.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        if (Engine.status.value.phase != Phase.IDLE) {
            TunnelService.stop(this)
            return
        }
        if (SettingsStore.current.vpnMode && VpnService.prepare(this) != null) {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            TunnelService.start(this)
        }
    }
}
