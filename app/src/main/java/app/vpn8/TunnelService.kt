package app.vpn8

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Foreground service that owns the whole session:
 *   apps -> TUN -> hev-socks5-tunnel -> local SOCKS5 -> Aether (+ Psiphon / Tor) -> internet
 * In proxy-only mode the TUN and hev are skipped.
 */
class TunnelService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var statsJob: Job? = null
    private var core: CoreProcess? = null
    private var tun: ParcelFileDescriptor? = null
    private var hevOn = false
    private var foreground = false
    private var stopping = false
    @Volatile private var rescanRequested = false
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun attachBaseContext(base: Context) = super.attachBaseContext(LocaleHelper.wrap(base))

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        scope.launch {
            Engine.status.collect { st ->
                if (foreground) nm.notify(NOTIF_ID, notification(textFor(st)))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSession()
            // Only meaningful while a link is up; the monitor loop picks the flag up.
            ACTION_RESCAN -> if (Engine.status.value.phase == Phase.CONNECTED) rescanRequested = true
            else -> startSession()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopSession()
    }

    override fun onDestroy() {
        scope.cancel()
        teardownBlocking()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- session

    private fun startSession() {
        promote()
        if (stopping || job?.isActive == true) return
        val s = SettingsStore.current
        Engine.logs.value = emptyList()
        Engine.resetLive()
        Engine.status.value = Status(Phase.CONNECTING, 1)
        job = scope.launch { run(s) }
    }

    private fun stopSession() {
        if (stopping) return
        stopping = true
        Engine.status.value = Status(Phase.STOPPING)
        scope.launch {
            job?.cancelAndJoin()
            finish(null)
            stopping = false
        }
    }

    private suspend fun finish(error: String?) {
        teardown()
        Engine.resetLive()
        Engine.status.value = Status(Phase.IDLE, error = error)
        foreground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun run(base: Settings) {
        var s = base
        val port = ArgBuilder.exitPort(s)
        var failures = 0
        var cycle = 0
        var everUp = false
        var autoRescans = 0
        var forceFresh = false
        acquireWake(s)

        while (currentCoroutineContext().isActive) {
            val attempt = failures + 1
            Engine.status.value = Status(if (everUp) Phase.RECONNECTING else Phase.CONNECTING, attempt)
            val fresh = cycle > 0 || forceFresh
            forceFresh = false
            Engine.log("Starting core, attempt $attempt of $MAX_TRIES" + if (fresh) " (fresh scan)" else "")

            if (startCore(s, port, fresh = fresh)) {
                if (s.vpnMode && tun == null && !bringUpTun(s, port)) {
                    finish(getString(R.string.vpn_iface_failed))
                    return
                }
                everUp = true
                failures = 0
                cycle = 0
                Engine.status.value = Status(Phase.CONNECTED)
                Engine.log("Connected via ${s.activeLabel()}")
                scope.launch { fetchExit(port) }
                val speedJob = scope.launch {
                    val canRescan = s.route.family == Family.AETHER && !s.route.noWarp && autoRescans < MAX_AUTO_RESCANS
                    if (checkSpeed(port) && canRescan) {
                        autoRescans++
                        Engine.log("Link is too slow, scanning for a better gateway ($autoRescans/$MAX_AUTO_RESCANS)")
                        rescanRequested = true
                    }
                }
                monitor(s, port)
                speedJob.cancel()
                Engine.pingMs.value = null
                Engine.exitInfo.value = null
                Engine.speedBps.value = null
                if (rescanRequested) {
                    rescanRequested = false
                    forceFresh = true
                    Engine.log("Rescan requested: restarting the core with a fresh scan")
                    stopCore()
                    delay(1000)
                    continue
                }
                // The link died after being up. QUIC (h3) is often cut by the network after a few
                // seconds, so alternate between h3 and h2 on each drop to find one that stays up.
                if (s.route.family == Family.AETHER && !s.route.noWarp &&
                    (s.protocol == Protocol.MASQUE || s.protocol == Protocol.MIM)
                ) {
                    val next = if (s.carrier == "h3") "h2" else "h3"
                    Engine.log("Link dropped. Trying ${next.uppercase()} on the next attempt")
                    s = s.copy(carrier = next)
                }
            } else {
                failures++
                if (failures >= MAX_TRIES) {
                    failures = 0
                    cycle++
                    if (cycle >= 2) {
                        stopCore()
                        finish(getString(R.string.connect_failed))
                        return
                    }
                    Engine.log("No luck. Dropping everything and starting over with a fresh scan")
                }
            }
            stopCore()
            delay(2000)
        }
    }

    // ------------------------------------------------------------------- core

    private suspend fun startCore(s: Settings, port: Int, fresh: Boolean): Boolean {
        val c = CoreProcess(this)
        core = c
        try {
            c.start(ArgBuilder.build(this, s, fresh))
        } catch (e: Exception) {
            Engine.log("Could not start core: ${e.message}")
            return false
        }
        val limit = if (s.route.slow) 240_000L else 120_000L
        val deadline = SystemClock.elapsedRealtime() + limit
        while (currentCoroutineContext().isActive && SystemClock.elapsedRealtime() < deadline) {
            if (!c.alive) return false
            val waited = SystemClock.elapsedRealtime() - (deadline - limit)
            // Aether opens the Psiphon/Tor port immediately and only starts the SOCKS port once the
            // WARP tunnel is validated, so wait for both, plus Psiphon's own "ready" line.
            val ready = withContext(Dispatchers.IO) {
                portOpen(port) && (port == s.socksPort || portOpen(s.socksPort))
            } && (!s.route.usesPsiphon || c.psiphonReady || waited > 60_000)
            if (ready) return true
            delay(500)
        }
        return false
    }

    private suspend fun stopCore() {
        val c = core ?: return
        core = null
        withContext(Dispatchers.IO) { c.stop() }
    }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    } catch (_: Exception) {
        false
    }

    /** Returns when the link is considered dead: core exited, or three pings in a row failed. */
    private suspend fun monitor(s: Settings, port: Int) {
        val timeout = if (s.route.slow) 20_000 else 8_000
        var fails = 0
        while (currentCoroutineContext().isActive) {
            if (rescanRequested) return
            if (core?.alive != true) {
                Engine.log("Core is not running anymore")
                return
            }
            val r = withContext(Dispatchers.IO) {
                Socks.httpGet(port, "cp.cloudflare.com", "/generate_204", timeout)
                    ?: Socks.httpGet(port, "www.gstatic.com", "/generate_204", timeout)
            }
            if (r != null) {
                fails = 0
                Engine.pingMs.value = r.first.toInt()
            } else {
                fails++
                Engine.pingMs.value = null
                Engine.log("Ping failed ($fails/3)")
                if (fails >= 3) return
            }
            // Wait 5 s between probes, but react to a rescan request within half a second.
            repeat(10) {
                if (rescanRequested) return
                delay(500)
            }
        }
    }

    /**
     * Downloads a small file through the tunnel and publishes the speed. Returns true when the link
     * is clearly too slow (a reason to look for a better gateway); false when it is fine or when
     * the test could not run at all.
     */
    private suspend fun checkSpeed(port: Int): Boolean {
        delay(2000) // let the tunnel settle
        val hosts = listOf(
            "speed.cloudflare.com" to "/__down?bytes=1000000",
            "cachefly.cachefly.net" to "/1mb.test",
            "ipv4.download.thinkbroadband.com" to "/1MB.zip",
        )
        for ((host, path) in hosts) {
            val r = withContext(Dispatchers.IO) { Socks.download(port, host, path, 1_000_000, 10_000) } ?: continue
            val (status, bytes, ms) = r
            if (status != 200 || bytes <= 0 || ms <= 0) continue
            val bps = bytes * 1000 / ms
            Engine.speedBps.value = bps
            Engine.log("Speed test: ${bps / 1024} KB/s ($bytes bytes in $ms ms via $host)")
            return bps < SLOW_BPS
        }
        Engine.log("Speed test: no result")
        return false
    }

    private suspend fun fetchExit(port: Int) {
        val r = withContext(Dispatchers.IO) { Socks.httpGet(port, "www.cloudflare.com", "/cdn-cgi/trace", 15_000) } ?: return
        val map = r.second.lines().mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 } }.associate { it[0] to it[1].trim() }
        val ip = map["ip"] ?: return
        Engine.exitInfo.value = if (map["loc"] != null) "$ip (${map["loc"]})" else ip
    }

    // -------------------------------------------------------------------- tun

    private fun bringUpTun(s: Settings, port: Int): Boolean {
        val b = Builder()
            .setSession("8 VPN")
            .setMtu(1500)
            .addAddress("198.18.0.1", 32)
            .addAddress("fc00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        // Real resolvers, reached through the tunnel (UDP/53 -> hev -> SOCKS UDP ASSOCIATE -> core).
        // Apps and Android then cache the answers and connect by IP, instead of every new TCP
        // connection making the core do its own uncached DNS lookup over the slow link.
        tunDnsServers(s).forEach { runCatching { b.addDnsServer(it) } }

        val apps = s.splitApps
        try {
            if (s.splitMode == "include" && apps.isNotEmpty()) {
                apps.forEach { runCatching { b.addAllowedApplication(it) } }
            } else {
                // The core's own sockets must never loop back into the tunnel.
                b.addDisallowedApplication(packageName)
                if (s.splitMode == "exclude") apps.forEach { runCatching { b.addDisallowedApplication(it) } }
            }
        } catch (e: Exception) {
            Engine.log("Split tunneling error: ${e.message}")
        }

        val fd = b.establish() ?: return false
        tun = fd

        val cfg = File(filesDir, "hev.yml")
        cfg.writeText(
            """
            tunnel:
              mtu: 1500
              ipv4: 198.18.0.1
              ipv6: 'fc00::1'
            socks5:
              port: $port
              address: 127.0.0.1
              udp: 'udp'
            misc:
              log-level: warn
            """.trimIndent()
        )
        hevOn = try {
            TProxyService.TProxyStartService(cfg.path, fd.fd)
        } catch (e: Throwable) {
            Engine.log("hev-socks5-tunnel failed to start: ${e.message}")
            false
        }
        if (!hevOn) {
            fd.close()
            tun = null
            return false
        }
        startStats()
        return true
    }

    /** IPv4 resolver addresses for the TUN: the user's DNS setting if it has any, else Cloudflare. */
    private fun tunDnsServers(s: Settings): List<String> {
        val custom = s.dns.split(',', ' ', ';', '\n')
            .map { it.trim().removeSuffix(":53") }
            .filter { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' } }
        return custom.ifEmpty { listOf("1.1.1.1", "1.0.0.1") }
    }

    private fun startStats() {
        statsJob = scope.launch {
            var lastTx = 0L
            var lastRx = 0L
            while (isActive) {
                val st = try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null }
                if (st != null && st.size >= 4) {
                    Engine.txBps.value = (st[1] - lastTx).coerceAtLeast(0)
                    Engine.rxBps.value = (st[3] - lastRx).coerceAtLeast(0)
                    lastTx = st[1]
                    lastRx = st[3]
                }
                delay(1000)
            }
        }
    }

    // --------------------------------------------------------------- teardown

    private suspend fun teardown() {
        withContext(Dispatchers.IO) { teardownBlocking() }
    }

    private fun teardownBlocking() {
        statsJob?.cancel()
        statsJob = null
        if (hevOn) {
            runCatching { TProxyService.TProxyStopService() }
            hevOn = false
        }
        runCatching { tun?.close() }
        tun = null
        core?.stop()
        core = null
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        wifi?.let { if (it.isHeld) it.release() }
        wifi = null
    }

    private fun acquireWake(s: Settings) {
        if (!s.keepAwake) return
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "8vpn:core").apply {
            setReferenceCounted(false)
            acquire()
        }
        // Without this, Wi-Fi may go to power save when the screen is off and the tunnel stalls.
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "8vpn:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    // ----------------------------------------------------------- notification

    private fun textFor(st: Status): String = when (st.phase) {
        Phase.CONNECTED -> getString(R.string.connected) + " · " + SettingsStore.current.activeLabel()
        Phase.CONNECTING -> getString(R.string.connecting)
        Phase.RECONNECTING -> getString(R.string.reconnecting) + " " + getString(R.string.attempt_of, st.attempt, MAX_TRIES)
        Phase.STOPPING -> getString(R.string.stopping)
        Phase.IDLE -> getString(R.string.disconnected)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TunnelService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat), getString(R.string.disconnect), stop
                ).build()
            )
            .build()
    }

    private fun promote() {
        if (foreground) return
        val n = notification(getString(R.string.connecting))
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
        foreground = true
    }

    companion object {
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 8
        const val ACTION_STOP = "app.vpn8.STOP"
        const val ACTION_RESCAN = "app.vpn8.RESCAN"
        private const val MAX_AUTO_RESCANS = 2
        private const val SLOW_BPS = 150_000L // below ~150 KB/s the link is treated as unusable

        fun rescan(ctx: Context) {
            ctx.startService(Intent(ctx, TunnelService::class.java).setAction(ACTION_RESCAN))
        }

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, TunnelService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TunnelService::class.java).setAction(ACTION_STOP))
        }
    }
}
