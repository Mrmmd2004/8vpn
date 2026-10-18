package app.vpn8

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface
import io.nekohasekai.libbox.Notification as LibboxNotification

/**
 * sing-box (libbox) as the TUN engine:
 *   apps -> TUN -> sing-box (gVisor stack, DNS hijack) -> SOCKS5 on 127.0.0.1 -> Aether / Psiphon / Tor
 *
 * The TUN itself is built by TunnelService (split tunneling, DNS, the core's own app excluded);
 * sing-box is handed that same descriptor from openTun(). Only the pieces of the platform
 * interface that a loopback-only SOCKS outbound needs are real; the rest are inert.
 */
object SingBox {
    /** False when libbox.aar (or its native library for this ABI) is missing; the caller falls back to hev. */
    val available: Boolean by lazy {
        try {
            Class.forName("io.nekohasekai.libbox.Libbox")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private var server: CommandServer? = null
    private var platform: Platform? = null
    private var setupDone = false

    /** Starts sing-box on [tun]. Returns null on success, or an error message. */
    @Synchronized
    fun start(ctx: Context, tun: ParcelFileDescriptor, config: String, protect: (Int) -> Boolean): String? {
        stop()
        return try {
            val app = ctx.applicationContext
            if (!setupDone) {
                val base = File(app.filesDir, "singbox").apply { mkdirs() }
                val work = File(base, "work").apply { mkdirs() }
                Libbox.setup(
                    SetupOptions().also {
                        it.basePath = base.path
                        it.workingPath = work.path
                        it.tempPath = app.cacheDir.path
                        it.fixAndroidStack = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        it.logMaxLines = 500
                        it.debug = false
                    }
                )
                setupDone = true
            }
            val p = Platform(app, tun, protect)
            platform = p
            val s = CommandServer(Handler, p)
            server = s
            // The gRPC command socket is for a UI client; nothing here needs it, so it is not started.
            s.startOrReloadService(config, OverrideOptions())
            null
        } catch (t: Throwable) {
            stop()
            t.message ?: t.javaClass.simpleName
        }
    }

    @Synchronized
    fun stop() {
        val s = server
        server = null
        if (s != null) {
            runCatching { s.closeService() }
            runCatching { s.close() }
        }
        platform?.release()
        platform = null
    }

    /** sing-box configuration for one session. [dns] is the resolver reached through the proxy over TCP. */
    fun buildConfig(s: Settings, port: Int, v6: Boolean, dns: String): String {
        val addresses = JSONArray().put("198.18.0.1/32")
        if (v6) addresses.put("fc00::1/128")
        val rules = JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        // Psiphon and Tor exits carry TCP only: refuse other UDP at once so apps drop QUIC and use TCP.
        if (s.route.slow) rules.put(JSONObject().put("network", "udp").put("action", "reject"))
        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject().put("type", "tcp").put("tag", "remote")
                                .put("server", dns).put("detour", "proxy")
                        )
                    )
                    .put("strategy", if (v6) "prefer_ipv4" else "ipv4_only")
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject().put("type", "tun").put("tag", "tun-in")
                        .put("address", addresses).put("mtu", s.mtu())
                        .put("auto_route", true).put("stack", "gvisor")
                )
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(
                        JSONObject().put("type", "socks").put("tag", "proxy")
                            .put("server", "127.0.0.1").put("server_port", port).put("version", "5")
                    )
                    .put(JSONObject().put("type", "direct").put("tag", "direct"))
            )
            .put("route", JSONObject().put("rules", rules).put("final", "proxy"))
        return root.toString()
    }

    private object Handler : CommandServerHandler {
        override fun serviceStop() {}
        override fun serviceReload() {}
        override fun getSystemProxyStatus(): SystemProxyStatus? = SystemProxyStatus()
        override fun setSystemProxyEnabled(isEnabled: Boolean) {}
        override fun writeDebugMessage(message: String?) {
            if (message != null) Log.d("sing-box", message)
        }
    }

    private class Platform(
        private val ctx: Context,
        private val tun: ParcelFileDescriptor,
        private val protectFd: (Int) -> Boolean,
    ) : PlatformInterface {
        private val cm = ctx.getSystemService(ConnectivityManager::class.java)
        private var callback: ConnectivityManager.NetworkCallback? = null

        fun release() {
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
            callback = null
        }

        override fun localDNSTransport(): LocalDNSTransport? = null

        // Sockets to the local SOCKS port are protected so they can never loop into the TUN.
        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
        override fun autoDetectInterfaceControl(fd: Int) {
            protectFd(fd)
        }

        // The TUN was already established by TunnelService; hand its descriptor over.
        override fun openTun(options: TunOptions): Int = tun.fd

        override fun useProcFS(): Boolean = false

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String,
            sourcePort: Int,
            destinationAddress: String,
            destinationPort: Int,
        ): ConnectionOwner = error("not supported")

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            release()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = report(network, listener)
                override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) = report(network, listener)
                override fun onLost(network: Network) {
                    runCatching { listener.updateDefaultInterface("", -1, false, false) }
                }
            }
            callback = cb
            runCatching { cm.registerDefaultNetworkCallback(cb) }
        }

        private fun report(network: Network, listener: InterfaceUpdateListener) {
            runCatching {
                val name = cm.getLinkProperties(network)?.interfaceName ?: return
                val index = NetworkInterface.getByName(name)?.index ?: return
                listener.updateDefaultInterface(name, index, false, false)
            }
        }

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = release()

        override fun getInterfaces(): NetworkInterfaceIterator {
            val interfaces = mutableListOf<LibboxNetworkInterface>()
            val all = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            for (network in cm.allNetworks) {
                val lp = cm.getLinkProperties(network) ?: continue
                val caps = cm.getNetworkCapabilities(network) ?: continue
                val ni = all.find { it.name == lp.interfaceName } ?: continue
                val box = LibboxNetworkInterface()
                box.name = lp.interfaceName
                box.dnsServer = Strings(lp.dnsServers.mapNotNull { it.hostAddress }.iterator())
                box.type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                box.index = ni.index
                runCatching { box.mtu = ni.mtu }
                box.addresses = Strings(ni.interfaceAddresses.map { it.toPrefix() }.iterator())
                var flags = 0
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                }
                if (ni.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                if (ni.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                if (ni.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
                box.flags = flags
                box.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                interfaces.add(box)
            }
            return Interfaces(interfaces.iterator())
        }

        override fun underNetworkExtension(): Boolean = false
        override fun includeAllNetworks(): Boolean = false
        override fun readWIFIState(): WIFIState? = null
        override fun clearDNSCache() {}
        override fun sendNotification(notification: LibboxNotification) {}

        override fun systemCertificates(): StringIterator {
            val certs = mutableListOf<String>()
            runCatching {
                val ks = KeyStore.getInstance("AndroidCAStore")
                ks.load(null, null)
                val aliases = ks.aliases()
                while (aliases.hasMoreElements()) {
                    val cert = ks.getCertificate(aliases.nextElement()) ?: continue
                    certs.add(
                        "-----BEGIN CERTIFICATE-----\n" +
                            Base64.encodeToString(cert.encoded, Base64.NO_WRAP) +
                            "\n-----END CERTIFICATE-----"
                    )
                }
            }
            return Strings(certs.iterator())
        }

        private fun InterfaceAddress.toPrefix(): String =
            if (address is Inet6Address) {
                "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
            } else {
                "${address.hostAddress}/$networkPrefixLength"
            }
    }

    private class Interfaces(private val it: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): LibboxNetworkInterface = it.next()
    }

    private class Strings(private val it: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): String = it.next()
    }
}
