package app.vpn8

import android.content.Context
import java.io.File

/** Turns the user's settings into Aether command line flags. */
object ArgBuilder {
    fun exitPort(s: Settings): Int = when (s.route) {
        Route.AETHER_PSIPHON -> PSIPHON_PORT
        Route.AETHER_TOR -> TOR_PORT
        else -> s.socksPort
    }

    private fun csv(v: String) = v.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    fun build(ctx: Context, s: Settings, fresh: Boolean): List<String> {
        val dir = File(ctx.filesDir, "aether")
        val nl = ctx.applicationInfo.nativeLibraryDir
        val a = ArrayList<String>()
        val r = s.route

        a += listOf("--config", File(dir, "aether.toml").path)
        a += listOf("--bind", "127.0.0.1:${s.socksPort}")
        if (s.httpEnabled) a += listOf("--http-proxy", "127.0.0.1:${s.httpPort}")

        when (r) {
            Route.AETHER_PSIPHON -> a += "--psiphon"
            Route.PSIPHON_ONLY -> a += "--psiphon-only"
            Route.PSIPHON_REVERSE -> a += "--psiphon-reverse"
            Route.AETHER_TOR -> a += "--tor"
            Route.TOR_ONLY -> a += "--tor-only"
            Route.TOR_REVERSE -> a += "--tor-reverse"
            Route.AETHER -> {}
        }

        if (!r.noWarp) {
            // Reverse routes carry MASQUE over HTTP/2 only (Tor and Psiphon are TCP only).
            val proto = if (r.reverse) Protocol.MASQUE else s.protocol
            when (proto) {
                Protocol.MASQUE -> a += listOf("--protocol", "masque")
                Protocol.WIREGUARD -> a += listOf("--protocol", "wg")
                Protocol.GOOL -> a += listOf("--protocol", "gool")
                Protocol.GOOL_CLASSIC -> a += "--gool-classic"
                Protocol.MIM -> a += listOf("--protocol", "mim")
            }
            if (r.reverse) a += "--h2"
            else if (proto == Protocol.MASQUE || proto == Protocol.MIM) a += if (s.carrier == "h2") "--h2" else "--h3"

            a += listOf("--scan", s.scan)
            if (s.noize != "default") a += listOf("--noize", s.noize)
            a += when (s.ipVersion) { "6" -> "-6"; "dual" -> "--dual"; else -> "-4" }
            a += if (fresh || !s.quickReconnect) "--no-quick-reconnect" else "--quick-reconnect"
            if (s.dns.isNotBlank()) a += listOf("--dns", csv(s.dns))
        }

        if (s.routeBlock.isNotBlank()) a += listOf("--route-block", csv(s.routeBlock))
        if (s.routeDirect.isNotBlank()) a += listOf("--route-direct", csv(s.routeDirect))
        if (s.upstream.isNotBlank()) a += listOf("--upstream", s.upstream.trim())

        if (r.usesPsiphon) {
            a += listOf("--psiphon-bin", "$nl/libpsiphon.so")
            a += listOf("--psiphon-dir", File(dir, "psiphon").path)
            if (r != Route.PSIPHON_ONLY) a += listOf("--psiphon-bind", "127.0.0.1:$PSIPHON_PORT")
            a += listOf("--psiphon-mode", s.psiphonMode)
            if (s.psiphonRegion.isNotBlank()) a += listOf("--psiphon-region", s.psiphonRegion.uppercase())
        }
        if (r.usesTor) {
            a += listOf("--tor-dir", File(dir, "tor").path)
            if (r != Route.TOR_ONLY) a += listOf("--tor-bind", "127.0.0.1:$TOR_PORT")
            when (s.torBridges) {
                "always" -> a += "--tor-bridges"
                "never" -> a += "--no-tor-bridges"
            }
        }
        return a
    }
}
