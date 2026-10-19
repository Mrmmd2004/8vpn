@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.vpn8

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun up(f: (Settings) -> Settings) = SettingsStore.update(f)

private var askedBattery = false

private fun fmtRate(b: Long): String = when {
    b < 0 -> "—"
    b >= 1_048_576 -> String.format(Locale.US, "%.1f MB/s", b / 1_048_576.0)
    b >= 1024 -> String.format(Locale.US, "%.0f KB/s", b / 1024.0)
    else -> "$b B/s"
}

@Composable
fun HomeScreen(openSettings: () -> Unit, openLogs: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    val st by Engine.status.collectAsStateWithLifecycle()
    val ping by Engine.pingMs.collectAsStateWithLifecycle()
    val rx by Engine.rxBps.collectAsStateWithLifecycle()
    val tx by Engine.txBps.collectAsStateWithLifecycle()
    val exit by Engine.exitInfo.collectAsStateWithLifecycle()
    val speed by Engine.speedBps.collectAsStateWithLifecycle()
    val logs by Engine.logs.collectAsStateWithLifecycle()

    val idle = st.phase == Phase.IDLE
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var showBattery by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(R.string.ping_failed)
    val ctxRes = LocalContext.current

    fun runPing() {
        if (testing) return
        testing = true
        testResult = null
        // Only meaningful through the tunnel: with the VPN off there is nothing to measure.
        if (st.phase != Phase.CONNECTED) { testing = false; return }
        val port = ArgBuilder.exitPort(s)
        scope.launch {
            val ms: Long? = withContext(Dispatchers.IO) {
                Socks.httpGet(port, "cp.cloudflare.com", "/generate_204", 8000)?.first
            }
            if (ms != null) Engine.pingMs.value = ms.toInt()
            testResult = ms?.let { ctxRes.getString(R.string.ms_fmt, it.toInt()) } ?: failedText
            testing = false
        }
    }
    LaunchedEffect(st.phase) { if (st.phase != Phase.CONNECTED) testResult = null }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) TunnelService.start(ctx)
    }

    fun toggle() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (idle) {
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            if (!askedBattery && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                showBattery = true
                return
            }
            val i = if (s.vpnMode) VpnService.prepare(ctx) else null
            if (i != null) vpnLauncher.launch(i) else TunnelService.start(ctx)
        } else if (st.phase != Phase.STOPPING) {
            TunnelService.stop(ctx)
        }
    }

    if (showBattery) {
        AlertDialog(
            onDismissRequest = { showBattery = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.battery_title)) },
            text = { Text(stringResource(R.string.battery_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showBattery = false
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    }
                }) { Text(stringResource(R.string.battery_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBattery = false
                    askedBattery = true
                    toggle()
                }) { Text(stringResource(R.string.battery_skip)) }
            }
        )
    }

    val c = MaterialTheme.colorScheme
    val x = LocalExtra.current
    val title = when (st.phase) {
        Phase.IDLE -> R.string.disconnected
        Phase.CONNECTING -> R.string.connecting
        Phase.CONNECTED -> R.string.connected
        Phase.RECONNECTING -> R.string.reconnecting
        Phase.STOPPING -> R.string.stopping
    }
    val statusColor = when (st.phase) {
        Phase.CONNECTED -> x.a
        Phase.IDLE -> c.onSurfaceVariant
        else -> x.busy
    }
    val live = st.phase == Phase.CONNECTED

    var panelOpen by remember { mutableStateOf(false) }
    val panelShown = panelOpen && idle

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- Top bar
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(36.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name), Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            IconBtn(Ico.LOGS, openLogs)
            Spacer(Modifier.width(8.dp))
            IconBtn(Ico.SETTINGS, openSettings)
        }

        // ---- Hero: connect button + status
        Spacer(Modifier.height(8.dp))
        ConnectButton(st.phase, ::toggle, 158.dp)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(statusColor.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = statusColor)
        }
        Spacer(Modifier.height(8.dp))
        val showAttempt = st.phase == Phase.RECONNECTING || (st.phase == Phase.CONNECTING && st.attempt > 1)
        if (showAttempt) {
            Text(stringResource(R.string.attempt_of, st.attempt, s.tries()), style = MaterialTheme.typography.bodySmall, color = x.busy)
        } else if (idle && st.error != null) {
            Text(st.error!!, style = MaterialTheme.typography.bodySmall, color = c.error, textAlign = TextAlign.Center)
        } else if (idle) {
            Text(stringResource(R.string.tap_to_connect), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        } else if (live) {
            Text(
                s.activeLabel() + (exit?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
        val reason by Engine.lastReason.collectAsStateWithLifecycle()
        reason?.let {
            Text(
                stringResource(R.string.last_drop, it), style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ---- Stats
        Spacer(Modifier.height(18.dp))
        AppCard {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 16.dp)) {
                Stat(Ico.BOLT, stringResource(R.string.ping), ping?.let { stringResource(R.string.ms_fmt, it) } ?: "—", Modifier.weight(1f))
                Stat(Ico.DOWN, stringResource(R.string.download), if (s.vpnMode && live) fmtRate(rx) else "—", Modifier.weight(1f))
                Stat(Ico.UP, stringResource(R.string.upload), if (s.vpnMode && live) fmtRate(tx) else "—", Modifier.weight(1f))
                Stat(Ico.GLOBE, stringResource(R.string.link_speed), if (live) speed?.let { fmtRate(it) } ?: "…" else "—", Modifier.weight(1f))
            }
        }
        if (!s.vpnMode) {
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text(
                    stringResource(R.string.proxy_info, "127.0.0.1:${s.socksPort}") +
                        if (s.httpEnabled) "   " + stringResource(R.string.http_info, "127.0.0.1:${s.httpPort}") else "",
                    modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                )
            }
        }

        // ---- Actions: only while connected, so there are no dead buttons
        if (live) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TonalPill(Ico.BOLT, if (testing) stringResource(R.string.ping_testing) else stringResource(R.string.ping_test), enabled = !testing) { runPing() }
                if (s.route.family == Family.AETHER) {
                    TonalPill(Ico.REFRESH, stringResource(R.string.rescan)) { TunnelService.rescan(ctx) }
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            }
        }

        // ---- Connection setup: mode, core, collapsible options
        Spacer(Modifier.height(14.dp))
        AppCard(Modifier.animateContentSize()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(
                    listOf(true to stringResource(R.string.mode_vpn), false to stringResource(R.string.mode_proxy)),
                    s.vpnMode, idle
                ) { v -> if (s.vpnMode != v) up { it.copy(vpnMode = v) } }
                Segmented(
                    listOf(
                        Family.AETHER to stringResource(R.string.core_aether),
                        Family.PSIPHON to stringResource(R.string.core_psiphon),
                        Family.TOR to stringResource(R.string.core_tor)
                    ),
                    s.route.family, idle
                ) { fam ->
                    if (s.route.family != fam) up {
                        it.copy(route = when (fam) { Family.AETHER -> Route.AETHER; Family.PSIPHON -> Route.PSIPHON_ONLY; Family.TOR -> Route.TOR_ONLY })
                    }
                }
                val summary = stringResource(if (s.vpnMode) R.string.mode_vpn else R.string.mode_proxy) + "  ·  " + s.activeLabel()
                val chevron by animateFloatAsState(if (panelShown) 180f else 0f, label = "chev")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh)
                        .clickable(enabled = idle) { panelOpen = !panelOpen }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!idle) Icon8(Ico.LOCK, c.onSurfaceVariant, 18.dp)
                    else Icon8(Ico.CHEVRON, c.onSurfaceVariant, 20.dp, Modifier.rotate(chevron))
                }
                if (!idle) Hint(stringResource(R.string.locked_hint))
                if (panelShown) {
                    Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FamilyPanel(s.route.family, s, idle)
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = openLogs)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    logs.takeLast(2).forEach {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(ico: Ico, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon8(ico, LocalExtra.current.a, 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FamilyPanel(fam: Family, s: Settings, enabled: Boolean) {
    var regionDialog by remember { mutableStateOf(false) }
    when (fam) {
        Family.AETHER -> {
            Field(stringResource(R.string.route)) {
                Chips(
                    listOf(Route.AETHER to "Aether", Route.AETHER_PSIPHON to "Aether → Psiphon", Route.AETHER_TOR to "Aether → Tor"),
                    s.route, enabled
                ) { v -> up { it.copy(route = v) } }
            }
            // The Psiphon region belongs to the route choice, so it opens right under it.
            if (s.route == Route.AETHER_PSIPHON) RegionButton(s, enabled) { regionDialog = true }
            Field(stringResource(R.string.protocol)) {
                Chips(Protocol.values().map { it to it.label }, s.protocol, enabled) { v -> up { it.copy(protocol = v) } }
            }
            if (s.protocol == Protocol.MASQUE || s.protocol == Protocol.MIM) {
                Field(stringResource(R.string.transport)) {
                    Chips(listOf("h3" to "HTTP/3 (QUIC)", "h2" to "HTTP/2 (TCP)"), s.carrier, enabled) { v -> up { it.copy(carrier = v) } }
                }
            }
        }
        Family.PSIPHON -> {
            Field(stringResource(R.string.route)) {
                Chips(
                    listOf(Route.PSIPHON_ONLY to "Psiphon only", Route.PSIPHON_REVERSE to "Psiphon → Aether"),
                    s.route, enabled
                ) { v -> up { it.copy(route = v) } }
            }
            RegionButton(s, enabled) { regionDialog = true }
            Field(stringResource(R.string.psiphon_mode)) {
                Chips(listOf("auto" to "Auto", "cdn" to "CDN"), s.psiphonMode, enabled) { v -> up { it.copy(psiphonMode = v) } }
            }
        }
        Family.TOR -> {
            Field(stringResource(R.string.route)) {
                Chips(
                    listOf(Route.TOR_ONLY to "Tor only", Route.TOR_REVERSE to "Tor → Aether"),
                    s.route, enabled
                ) { v -> up { it.copy(route = v) } }
            }
            Field(stringResource(R.string.bridges)) {
                Chips(
                    listOf(
                        "auto" to stringResource(R.string.bridges_auto),
                        "always" to stringResource(R.string.bridges_always),
                        "never" to stringResource(R.string.bridges_never)
                    ),
                    s.torBridges, enabled
                ) { v -> up { it.copy(torBridges = v) } }
            }
        }
    }
    if (regionDialog) RegionDialog(s.psiphonRegion, onPick = { cc -> up { it.copy(psiphonRegion = cc) }; regionDialog = false }) { regionDialog = false }
}

@Composable
fun RegionButton(s: Settings, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val text = if (s.psiphonRegion.isBlank()) stringResource(R.string.region_auto)
    else PsiphonRegions.name(s.psiphonRegion, s.language)
    val flag = if (s.psiphonRegion.isBlank()) "🌐" else PsiphonRegions.flag(s.psiphonRegion)
    Field(stringResource(R.string.region)) {
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh)
                .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flag, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon8(Ico.CHEVRON, c.onSurfaceVariant, 20.dp, Modifier.rotate(-90f))
        }
    }
}

/** Big round power button with soft expanding rings while the link is up or being set up. */
@Composable
fun ConnectButton(phase: Phase, onClick: () -> Unit, diameter: Dp = 158.dp) {
    val c = MaterialTheme.colorScheme
    val x = LocalExtra.current
    val live = phase == Phase.CONNECTED
    val busy = phase == Phase.CONNECTING || phase == Phase.RECONNECTING || phase == Phase.STOPPING

    val inf = rememberInfiniteTransition(label = "hero")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "rings")
    val breathe by inf.animateFloat(
        0.95f, 1.04f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    val glow by animateFloatAsState(if (live) 1f else if (busy) 0.75f else 0f, tween(500), label = "glow")
    val liveA by animateFloatAsState(if (live) 1f else 0f, tween(450), label = "liveA")
    val busyA by animateFloatAsState(if (busy) 1f else 0f, tween(300), label = "busyA")
    val ring = if (busy) x.busy else x.a
    val fg by animateColorAsState(
        when { live -> Color.White; busy -> Color(0xFF2B1E00); else -> c.onSurfaceVariant }, label = "fg"
    )

    Box(Modifier.size(diameter * 1.5f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (glow > 0.01f) {
                val outer = this.size.minDimension / 2f
                val core = outer / 1.5f
                drawCircle(
                    Brush.radialGradient(listOf(ring.copy(alpha = 0.26f * glow), Color.Transparent), radius = outer),
                    radius = outer
                )
                for (i in 0 until 3) {
                    val p = (t + i / 3f) % 1f
                    drawCircle(
                        color = ring.copy(alpha = (1f - p) * 0.30f * glow),
                        radius = core + (outer - core) * p,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
        Box(
            Modifier.size(diameter).scale(if (busy) breathe else 1f).clip(CircleShape)
                .background(c.surfaceContainerHigh).border(1.dp, c.outlineVariant, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize().alpha(liveA).background(x.gradient))
            Box(Modifier.fillMaxSize().alpha(busyA).background(x.busy))
            Icon8(Ico.POWER, fg, diameter * 0.40f, strokeW = 2.3f)
        }
    }
}

@Composable
fun RegionDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val lang = SettingsStore.current.language
    var q by remember { mutableStateOf("") }
    val all = remember(lang) { PsiphonRegions.codes.map { it to PsiphonRegions.name(it, lang) } }
    val list = all.filter { q.isBlank() || it.second.contains(q, true) || it.first.contains(q, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.region), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SearchField(q, { q = it }, stringResource(R.string.search))
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    item { RegionRow("🌐", stringResource(R.string.region_auto), current.isBlank()) { onPick("") } }
                    items(list.size) { i ->
                        val (cc, name) = list[i]
                        RegionRow(PsiphonRegions.flag(cc), "$name  ($cc)", cc == current) { onPick(cc) }
                    }
                }
            }
        }
    )
}

@Composable
private fun RegionRow(flag: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(flag, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text(name, Modifier.weight(1f), color = if (selected) c.primary else c.onSurface)
        if (selected) Icon8(Ico.CHECK, c.primary, 18.dp, strokeW = 2.6f)
    }
}
