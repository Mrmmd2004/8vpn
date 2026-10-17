package app.vpn8

import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { IDLE, CONNECTING, CONNECTED, RECONNECTING, STOPPING }

data class Status(val phase: Phase = Phase.IDLE, val attempt: Int = 0, val error: String? = null)

/** Process-wide connection state shared by the service, the tile and the UI. */
object Engine {
    val status = MutableStateFlow(Status())
    val pingMs = MutableStateFlow<Int?>(null)
    val rxBps = MutableStateFlow(0L)
    val txBps = MutableStateFlow(0L)
    val exitInfo = MutableStateFlow<String?>(null)
    /** Measured download speed through the tunnel in bytes/s (null = not measured yet). */
    val speedBps = MutableStateFlow<Long?>(null)
    val logs = MutableStateFlow<List<String>>(emptyList())

    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")

    fun log(line: String) {
        val clean = ansi.replace(line, "").trimEnd()
        if (clean.isEmpty()) return
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logs.value = (logs.value + "$stamp  $clean").takeLast(400)
    }

    fun resetLive() {
        pingMs.value = null
        rxBps.value = 0
        txBps.value = 0
        exitInfo.value = null
        speedBps.value = null
    }
}
