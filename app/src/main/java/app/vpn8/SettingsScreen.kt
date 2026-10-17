package app.vpn8

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

private fun up(f: (Settings) -> Settings) = SettingsStore.update(f)

@Composable
fun SettingsScreen(
    back: () -> Unit,
    openLogs: () -> Unit,
    openAbout: () -> Unit,
    openSplit: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    val st by Engine.status.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var regionDialog by remember { mutableStateOf(false) }

    Page(stringResource(R.string.settings), back, actions = {
        TextButton(onClick = openAbout) { Text(stringResource(R.string.about)) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.restart_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ---- General
            SectionTitle(stringResource(R.string.sec_general))
            Label(stringResource(R.string.language))
            Chips(listOf("en" to "English", "fa" to "فارسی"), s.language) { v ->
                if (v != s.language) { up { it.copy(language = v) }; onLanguageChanged() }
            }
            Label(stringResource(R.string.theme))
            Chips(
                listOf("system" to stringResource(R.string.theme_system), "dark" to stringResource(R.string.theme_dark), "light" to stringResource(R.string.theme_light)),
                s.theme
            ) { v -> up { it.copy(theme = v) } }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow(stringResource(R.string.dynamic_color), s.dynamicColor) { v -> up { it.copy(dynamicColor = v) } }
            Label(stringResource(R.string.default_mode))
            Chips(listOf(true to stringResource(R.string.mode_vpn), false to stringResource(R.string.mode_proxy)), s.vpnMode) { v -> up { it.copy(vpnMode = v) } }
            Label(stringResource(R.string.tun_engine))
            Chips(listOf("tun2socks" to "tun2socks (Go)", "hev" to "hev-socks5-tunnel", "hev-tcp" to "hev (TCP only)"), s.tunEngine) { v -> up { it.copy(tunEngine = v) } }
            SwitchRow(stringResource(R.string.auto_start), s.autoStart) { v -> up { it.copy(autoStart = v) } }
            SwitchRow(stringResource(R.string.keep_awake), s.keepAwake) { v -> up { it.copy(keepAwake = v) } }
            val batteryOk = ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            OutlinedButton(
                onClick = {
                    if (batteryOk) {
                        Toast.makeText(ctx, ctx.getString(R.string.battery_already), Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:" + ctx.packageName)
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (batteryOk) R.string.battery_row_ok else R.string.battery_row_ask))
            }
            Label(stringResource(R.string.split_tunnel))
            Chips(
                listOf("off" to stringResource(R.string.split_off), "include" to stringResource(R.string.split_include), "exclude" to stringResource(R.string.split_exclude)),
                s.splitMode
            ) { v -> up { it.copy(splitMode = v) } }
            if (s.splitMode != "off") {
                OutlinedButton(onClick = openSplit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.split_summary, s.splitApps.size))
                }
            }

            // ---- Core and protocol
            SectionTitle(stringResource(R.string.sec_core))
            Label(stringResource(R.string.protocol))
            Chips(Protocol.values().map { it to it.label }, s.protocol) { v -> up { it.copy(protocol = v) } }
            Label(stringResource(R.string.transport))
            Chips(listOf("h3" to "HTTP/3 (QUIC)", "h2" to "HTTP/2 (TCP)"), s.carrier) { v -> up { it.copy(carrier = v) } }
            Label(stringResource(R.string.scan_level))
            Chips(listOf("turbo", "balanced", "thorough", "verified", "ironclad").map { it to it }, s.scan) { v -> up { it.copy(scan = v) } }
            Label(stringResource(R.string.noize))
            Chips(
                listOf("default" to stringResource(R.string.noize_default)) + listOf("off", "light", "firewall", "balanced", "gfw", "aggressive").map { it to it },
                s.noize
            ) { v -> up { it.copy(noize = v) } }
            Label(stringResource(R.string.ip_version))
            Chips(listOf("4" to "IPv4", "6" to "IPv6", "dual" to "IPv4 + IPv6"), s.ipVersion) { v -> up { it.copy(ipVersion = v) } }
            SwitchRow(stringResource(R.string.quick_reconnect), s.quickReconnect) { v -> up { it.copy(quickReconnect = v) } }
            SwitchRow(stringResource(R.string.fresh_after_drop), s.freshAfterDrop) { v -> up { it.copy(freshAfterDrop = v) } }
            NumberRow(stringResource(R.string.max_tries), s.maxTries) { v -> up { it.copy(maxTries = v) } }
            NumberRow(stringResource(R.string.retry_delay), s.retryDelayMs) { v -> up { it.copy(retryDelayMs = v) } }
            NumberRow(stringResource(R.string.ping_fails), s.pingFails) { v -> up { it.copy(pingFails = v) } }
            NumberRow(stringResource(R.string.ping_interval), s.pingIntervalSec) { v -> up { it.copy(pingIntervalSec = v) } }
            TextRow(
                stringResource(if (s.protocol == Protocol.GOOL || s.protocol == Protocol.GOOL_CLASSIC || s.protocol == Protocol.MIM) R.string.endpoint_outer else R.string.endpoint),
                s.endpoint, stringResource(R.string.endpoint_hint), isError = !Endpoints.valid(s.endpoint)
            ) { v -> up { it.copy(endpoint = v.trim()) } }
            if (s.protocol == Protocol.GOOL || s.protocol == Protocol.GOOL_CLASSIC || s.protocol == Protocol.MIM) {
                TextRow(stringResource(R.string.endpoint_inner), s.endpoint2, stringResource(R.string.endpoint_hint), isError = !Endpoints.valid(s.endpoint2)) { v -> up { it.copy(endpoint2 = v.trim()) } }
            }
            if (!Endpoints.valid(s.endpoint) || !Endpoints.valid(s.endpoint2)) {
                Text(stringResource(R.string.endpoint_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(stringResource(R.string.endpoint_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextRow(stringResource(R.string.upstream), s.upstream, stringResource(R.string.upstream_hint)) { v -> up { it.copy(upstream = v) } }

            // ---- Routing
            SectionTitle(stringResource(R.string.sec_routing))
            TextRow(stringResource(R.string.route_block), s.routeBlock, stringResource(R.string.route_hint)) { v -> up { it.copy(routeBlock = v) } }
            TextRow(stringResource(R.string.route_direct), s.routeDirect, stringResource(R.string.route_hint)) { v -> up { it.copy(routeDirect = v) } }
            TextRow(stringResource(R.string.dns), s.dns, stringResource(R.string.dns_hint)) { v -> up { it.copy(dns = v) } }

            // ---- Psiphon
            SectionTitle(stringResource(R.string.sec_psiphon))
            RegionButton(s, true) { regionDialog = true }
            Label(stringResource(R.string.psiphon_mode))
            Chips(listOf("auto" to "Auto", "cdn" to "CDN"), s.psiphonMode) { v -> up { it.copy(psiphonMode = v) } }

            // ---- Tor
            SectionTitle(stringResource(R.string.sec_tor))
            Label(stringResource(R.string.bridges))
            Chips(
                listOf("auto" to stringResource(R.string.bridges_auto), "always" to stringResource(R.string.bridges_always), "never" to stringResource(R.string.bridges_never)),
                s.torBridges
            ) { v -> up { it.copy(torBridges = v) } }

            // ---- Advanced
            SectionTitle(stringResource(R.string.sec_advanced))
            Label(stringResource(R.string.perf_profile))
            Chips(listOf("auto" to "Auto", "low" to "Low", "medium" to "Medium", "high" to "High"), s.perf) { v -> up { it.copy(perf = v) } }
            TextRow(stringResource(R.string.extra_args), s.extraArgs, stringResource(R.string.extra_args_hint)) { v -> up { it.copy(extraArgs = v) } }
            NumberRow(stringResource(R.string.tun_mtu), s.tunMtu) { v -> up { it.copy(tunMtu = v) } }
            NumberRow(stringResource(R.string.tcp_buffer), s.tcpBufferKb) { v -> up { it.copy(tcpBufferKb = v) } }
            Text(stringResource(R.string.tunnel_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NumberRow(stringResource(R.string.socks_port), s.socksPort) { v -> up { it.copy(socksPort = v) } }
            SwitchRow(stringResource(R.string.http_proxy), s.httpEnabled) { v -> up { it.copy(httpEnabled = v) } }
            if (s.httpEnabled) NumberRow(stringResource(R.string.http_port), s.httpPort) { v -> up { it.copy(httpPort = v) } }
            OutlinedButton(onClick = openLogs, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_logs)) }
            OutlinedButton(
                onClick = { confirmClear = true }, enabled = st.phase == Phase.IDLE, modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.clear_cache)) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (regionDialog) RegionDialog(s.psiphonRegion, onPick = { cc -> up { it.copy(psiphonRegion = cc) }; regionDialog = false }) { regionDialog = false }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_warn)) },
            confirmButton = {
                TextButton(onClick = {
                    File(ctx.filesDir, "aether").deleteRecursively()
                    ctx.cacheDir.deleteRecursively()
                    confirmClear = false
                    Toast.makeText(ctx, R.string.cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
