package app.vpn8

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TOR_PORT = 1820
const val PSIPHON_PORT = 1821
const val DEFAULT_MAX_TRIES = 5
const val DEFAULT_RETRY_DELAY_MS = 1000

enum class Family { AETHER, PSIPHON, TOR }

enum class Route(val family: Family) {
    AETHER(Family.AETHER),
    AETHER_PSIPHON(Family.AETHER),
    AETHER_TOR(Family.AETHER),
    PSIPHON_ONLY(Family.PSIPHON),
    PSIPHON_REVERSE(Family.PSIPHON),
    TOR_ONLY(Family.TOR),
    TOR_REVERSE(Family.TOR);

    val usesPsiphon get() = this == AETHER_PSIPHON || this == PSIPHON_ONLY || this == PSIPHON_REVERSE
    val usesTor get() = this == AETHER_TOR || this == TOR_ONLY || this == TOR_REVERSE
    val noWarp get() = this == PSIPHON_ONLY || this == TOR_ONLY
    val reverse get() = this == PSIPHON_REVERSE || this == TOR_REVERSE
    val slow get() = usesPsiphon || usesTor
}

enum class Protocol(val label: String) {
    MASQUE("MASQUE"),
    WIREGUARD("WireGuard"),
    GOOL("WARP-in-MASQUE (Gool)"),
    GOOL_CLASSIC("WARP-in-WARP (Gool classic)"),
    MIM("MASQUE-in-MASQUE")
}

data class Settings(
    val language: String = "en",
    val theme: String = "dark",
    val dynamicColor: Boolean = false,
    val vpnMode: Boolean = true,
    val tunEngine: String = "tun2socks", // "tun2socks" (Go + gVisor, default), "hev" (hev-socks5-tunnel) or "hev-tcp" (hev, TCP only, names resolved remotely)
    val autoStart: Boolean = false,
    val keepAwake: Boolean = true,
    val route: Route = Route.AETHER,
    val protocol: Protocol = Protocol.WIREGUARD,
    val carrier: String = "h2", // MASQUE transport: HTTP/2 by default
    val scan: String = "turbo",
    val noize: String = "default",
    val quickReconnect: Boolean = true,
    val maxTries: Int = DEFAULT_MAX_TRIES,           // connection attempts before giving up
    val retryDelayMs: Int = DEFAULT_RETRY_DELAY_MS,  // pause between two attempts
    val pingFails: Int = 4,        // failed probes in a row before the link counts as dead
    val pingIntervalSec: Int = 5,  // seconds between two probes
    val tunMtu: Int = 1500,        // MTU of the local TUN device (not the tunnel MTU)
    val tcpBufferKb: Int = 256,    // TCP buffer of the TUN engine, in KB
    val freshAfterDrop: Boolean = true, // after a real drop, scan for a new gateway instead of reusing the last one
    val perf: String = "auto",     // Aether resource profile: auto | low | medium | high
    val warpCore: String = "v21",   // core of the plain WARP route: v21 (Aether 2.1.0) or v23 (Aether 2.3.0)
    val extraArgs: String = "",    // extra Aether command line arguments, appended last
    val ipVersion: String = "4",
    val upstream: String = "",
    val endpoint: String = "",  // custom gateway ip:port (outer hop); empty = scan
    val endpoint2: String = "", // second hop (inner) for Gool / Gool classic / MASQUE-in-MASQUE
    val routeBlock: String = "",
    val routeDirect: String = "",
    val dns: String = "",
    val psiphonRegion: String = "",
    val psiphonMode: String = "auto",
    val torBridges: String = "auto",
    val socksPort: Int = 1819,
    val httpEnabled: Boolean = false,
    val httpPort: Int = 1822,
    val splitMode: String = "off",
    val splitApps: Set<String> = emptySet(),
)

/** Custom gateway helpers. Aether wants `ip:port` (IPv6 as `[addr]:port`). */
object Endpoints {
    private val re = Regex("""^(\d{1,3}(\.\d{1,3}){3}|\[[0-9a-fA-F:.]+\]):(\d{1,5})$""")

    /** Returns the trimmed endpoint if it is a valid ip:port, else null. */
    fun clean(v: String): String? {
        val t = v.trim()
        val m = re.matchEntire(t) ?: return null
        val port = m.groupValues[3].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        if (!t.startsWith("[") && t.substringBefore(':').split('.').any { (it.toIntOrNull() ?: 256) > 255 }) return null
        return t
    }

    fun valid(v: String) = v.isBlank() || clean(v) != null
}

fun Settings.activeLabel(): String = when (route) {
    Route.AETHER -> protocol.label
    Route.AETHER_PSIPHON -> "${protocol.label} → Psiphon"
    Route.AETHER_TOR -> "${protocol.label} → Tor"
    Route.PSIPHON_ONLY -> "Psiphon"
    Route.PSIPHON_REVERSE -> "Psiphon → MASQUE (HTTP/2)"
    Route.TOR_ONLY -> "Tor"
    Route.TOR_REVERSE -> "Tor → MASQUE (HTTP/2)"
}

object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _flow = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> = _flow
    val current: Settings get() = _flow.value

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _flow.value = load()
        val schema = prefs.getInt("schema", 0)
        if (schema < 5) {
            // One-time reset of the defaults: Aether + WireGuard first, MASQUE over HTTP/2,
            // quick reconnect on, and hev as the TUN engine.
            val d = Settings()
            val next = _flow.value.copy(
                route = d.route, protocol = d.protocol, carrier = d.carrier,
                quickReconnect = d.quickReconnect, tunEngine = d.tunEngine,
            )
            _flow.value = next
            save(next)
            prefs.edit().putInt("schema", 5).apply()
        }
        if (prefs.getInt("schema", 0) < 6) {
            // Base engine is hev again; tun2socks stays selectable in Settings.
            val next = _flow.value.copy(tunEngine = Settings().tunEngine)
            _flow.value = next
            save(next)
            prefs.edit().putInt("schema", 6).apply()
        }
        if (prefs.getInt("schema", 0) < 8) {
            // One-time tuning reset for weak / unstable networks. Language, theme, ports and
            // split-tunnel apps are kept; everything that shapes the connection is reset.
            val d = Settings()
            val next = _flow.value.copy(
                route = d.route, protocol = d.protocol, carrier = d.carrier,
                scan = d.scan, noize = d.noize, ipVersion = d.ipVersion,
                quickReconnect = d.quickReconnect, freshAfterDrop = d.freshAfterDrop,
                maxTries = d.maxTries, retryDelayMs = d.retryDelayMs,
                pingFails = d.pingFails, pingIntervalSec = d.pingIntervalSec,
                tunEngine = d.tunEngine, tunMtu = d.tunMtu, tcpBufferKb = d.tcpBufferKb,
                perf = d.perf, dns = d.dns, endpoint = d.endpoint, endpoint2 = d.endpoint2,
                extraArgs = d.extraArgs,
            )
            _flow.value = next
            save(next)
            prefs.edit().putInt("schema", 8).apply()
        }
        if (prefs.getInt("schema", 0) < 9) {
            // The default TUN engine is tun2socks (the same data path FCAE VPN uses), with an
            // automatic fallback to hev when it cannot start.
            val next = _flow.value.copy(tunEngine = Settings().tunEngine)
            _flow.value = next
            save(next)
            prefs.edit().putInt("schema", 9).apply()
        }
    }

    fun update(f: (Settings) -> Settings) {
        val next = sanitize(f(_flow.value))
        _flow.value = next
        save(next)
    }

    private fun sanitize(s: Settings): Settings {
        val socks = if (s.socksPort in 1024..65535 && s.socksPort != TOR_PORT && s.socksPort != PSIPHON_PORT) s.socksPort else 1819
        var http = if (s.httpPort in 1024..65535 && s.httpPort != TOR_PORT && s.httpPort != PSIPHON_PORT) s.httpPort else 1822
        if (http == socks) http = if (socks == 1822) 1823 else 1822
        return s.copy(socksPort = socks, httpPort = http)
    }

    private fun load(): Settings {
        val d = Settings()
        fun str(k: String, def: String) = prefs.getString(k, def) ?: def
        return Settings(
            language = str("language", d.language),
            theme = str("theme", d.theme),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            vpnMode = prefs.getBoolean("vpnMode", d.vpnMode),
            tunEngine = str("tunEngine", d.tunEngine).takeIf { it == "tun2socks" || it == "hev" || it == "hev-tcp" } ?: d.tunEngine,
            autoStart = prefs.getBoolean("autoStart", d.autoStart),
            keepAwake = prefs.getBoolean("keepAwake", d.keepAwake),
            route = Route.values().firstOrNull { it.name == prefs.getString("route", null) } ?: d.route,
            protocol = Protocol.values().firstOrNull { it.name == prefs.getString("protocol", null) } ?: d.protocol,
            carrier = str("carrier", d.carrier),
            scan = str("scan", d.scan),
            noize = str("noize", d.noize),
            quickReconnect = prefs.getBoolean("quickReconnect", d.quickReconnect),
            maxTries = prefs.getInt("maxTries", d.maxTries),
            retryDelayMs = prefs.getInt("retryDelayMs", d.retryDelayMs),
            pingFails = prefs.getInt("pingFails", d.pingFails),
            pingIntervalSec = prefs.getInt("pingIntervalSec", d.pingIntervalSec),
            tunMtu = prefs.getInt("tunMtu", d.tunMtu),
            tcpBufferKb = prefs.getInt("tcpBufferKb", d.tcpBufferKb),
            freshAfterDrop = prefs.getBoolean("freshAfterDrop", d.freshAfterDrop),
            perf = str("perf", d.perf).takeIf { it in setOf("auto", "low", "medium", "high") } ?: d.perf,
            warpCore = str("warpCore", d.warpCore).takeIf { it in setOf("v21", "v23") } ?: d.warpCore,
            extraArgs = str("extraArgs", d.extraArgs),
            ipVersion = str("ipVersion", d.ipVersion),
            upstream = str("upstream", d.upstream),
            endpoint = str("endpoint", d.endpoint),
            endpoint2 = str("endpoint2", d.endpoint2),
            routeBlock = str("routeBlock", d.routeBlock),
            routeDirect = str("routeDirect", d.routeDirect),
            dns = str("dns", d.dns),
            psiphonRegion = str("psiphonRegion", d.psiphonRegion),
            psiphonMode = str("psiphonMode", d.psiphonMode),
            torBridges = str("torBridges", d.torBridges),
            socksPort = prefs.getInt("socksPort", d.socksPort),
            httpEnabled = prefs.getBoolean("httpEnabled", d.httpEnabled),
            httpPort = prefs.getInt("httpPort", d.httpPort),
            splitMode = str("splitMode", d.splitMode),
            splitApps = prefs.getStringSet("splitApps", emptySet()) ?: emptySet(),
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putString("language", s.language).putString("theme", s.theme)
            .putBoolean("dynamicColor", s.dynamicColor).putBoolean("vpnMode", s.vpnMode)
            .putString("tunEngine", s.tunEngine)
            .putBoolean("autoStart", s.autoStart).putBoolean("keepAwake", s.keepAwake)
            .putString("route", s.route.name).putString("protocol", s.protocol.name)
            .putString("carrier", s.carrier).putString("scan", s.scan).putString("noize", s.noize)
            .putBoolean("quickReconnect", s.quickReconnect).putString("ipVersion", s.ipVersion)
            .putInt("maxTries", s.maxTries).putInt("retryDelayMs", s.retryDelayMs)
            .putInt("pingFails", s.pingFails).putInt("pingIntervalSec", s.pingIntervalSec)
            .putInt("tunMtu", s.tunMtu).putInt("tcpBufferKb", s.tcpBufferKb)
            .putBoolean("freshAfterDrop", s.freshAfterDrop).putString("perf", s.perf).putString("extraArgs", s.extraArgs).putString("warpCore", s.warpCore)
            .putString("upstream", s.upstream).putString("endpoint", s.endpoint).putString("endpoint2", s.endpoint2)
            .putString("routeBlock", s.routeBlock)
            .putString("routeDirect", s.routeDirect).putString("dns", s.dns)
            .putString("psiphonRegion", s.psiphonRegion).putString("psiphonMode", s.psiphonMode)
            .putString("torBridges", s.torBridges).putInt("socksPort", s.socksPort)
            .putBoolean("httpEnabled", s.httpEnabled).putInt("httpPort", s.httpPort)
            .putString("splitMode", s.splitMode).putStringSet("splitApps", HashSet(s.splitApps))
            .apply()
    }
}

// Values typed in Settings are kept as typed (so the field is not rewritten while the user types);
// they are clamped to a safe range only where they are used.
fun Settings.tries() = maxTries.coerceIn(1, 999)
fun Settings.retryMs(): Long = retryDelayMs.coerceIn(100, 60_000).toLong()
fun Settings.pingLimit() = pingFails.coerceIn(1, 20)
fun Settings.pingEverySec() = pingIntervalSec.coerceIn(1, 60)
fun Settings.mtu() = tunMtu.coerceIn(1280, 9000)
fun Settings.tcpKb() = tcpBufferKb.coerceIn(16, 4096)
