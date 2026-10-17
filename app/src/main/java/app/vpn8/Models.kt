package app.vpn8

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TOR_PORT = 1820
const val PSIPHON_PORT = 1821
const val MAX_TRIES = 3

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
    val autoStart: Boolean = false,
    val keepAwake: Boolean = true,
    val route: Route = Route.AETHER,
    val protocol: Protocol = Protocol.MASQUE,
    val carrier: String = "h3",
    val scan: String = "balanced",
    val noize: String = "default",
    val quickReconnect: Boolean = true,
    val ipVersion: String = "4",
    val upstream: String = "",
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
            autoStart = prefs.getBoolean("autoStart", d.autoStart),
            keepAwake = prefs.getBoolean("keepAwake", d.keepAwake),
            route = Route.values().firstOrNull { it.name == prefs.getString("route", null) } ?: d.route,
            protocol = Protocol.values().firstOrNull { it.name == prefs.getString("protocol", null) } ?: d.protocol,
            carrier = str("carrier", d.carrier),
            scan = str("scan", d.scan),
            noize = str("noize", d.noize),
            quickReconnect = prefs.getBoolean("quickReconnect", d.quickReconnect),
            ipVersion = str("ipVersion", d.ipVersion),
            upstream = str("upstream", d.upstream),
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
            .putBoolean("autoStart", s.autoStart).putBoolean("keepAwake", s.keepAwake)
            .putString("route", s.route.name).putString("protocol", s.protocol.name)
            .putString("carrier", s.carrier).putString("scan", s.scan).putString("noize", s.noize)
            .putBoolean("quickReconnect", s.quickReconnect).putString("ipVersion", s.ipVersion)
            .putString("upstream", s.upstream).putString("routeBlock", s.routeBlock)
            .putString("routeDirect", s.routeDirect).putString("dns", s.dns)
            .putString("psiphonRegion", s.psiphonRegion).putString("psiphonMode", s.psiphonMode)
            .putString("torBridges", s.torBridges).putInt("socksPort", s.socksPort)
            .putBoolean("httpEnabled", s.httpEnabled).putInt("httpPort", s.httpPort)
            .putString("splitMode", s.splitMode).putStringSet("splitApps", HashSet(s.splitApps))
            .apply()
    }
}
