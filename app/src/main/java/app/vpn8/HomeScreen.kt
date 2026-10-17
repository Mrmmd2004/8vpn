@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.vpn8

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
        val connected = st.phase == Phase.CONNECTED
        val port = ArgBuilder.exitPort(s)
        scope.launch {
            val ms: Long? = withContext(Dispatchers.IO) {
                if (connected) Socks.httpGet(port, "cp.cloudflare.com", "/generate_204", 8000)?.first
                else Socks.tcpPing("1.1.1.1", 80, 5000)
            }
            if (ms != null && connected) Engine.pingMs.value = ms.toInt()
            testResult = ms?.let { ctxRes.getString(R.string.ms_fmt, it.toInt()) } ?: failedText
            testing = false
        }
    }
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBattery = false },
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

    Column(
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = openLogs) { Text(stringResource(R.string.logs)) }
            TextButton(onClick = openSettings) { Text("⚙ " + stringResource(R.string.settings)) }
        }

        // Aether / Psiphon / Tor switcher
        val families = listOf(
            Family.AETHER to R.string.core_aether, Family.PSIPHON to R.string.core_psiphon, Family.TOR to R.string.core_tor
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            families.forEachIndexed { idx, (fam, label) ->
                SegmentedButton(
                    selected = s.route.family == fam,
                    enabled = idle,
                    onClick = {
                        if (s.route.family != fam) up {
                            it.copy(route = when (fam) { Family.AETHER -> Route.AETHER; Family.PSIPHON -> Route.PSIPHON_ONLY; Family.TOR -> Route.TOR_ONLY })
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(idx, families.size)
                ) { Text(stringResource(label)) }
            }
        }

        // Expandable settings panel for the selected family
        Card(
            Modifier.fillMaxWidth().padding(top = 12.dp).animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AnimatedContent(
                targetState = s.route.family,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "panel"
            ) { fam ->
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FamilyPanel(fam, s, idle)
                    if (!idle) Text(stringResource(R.string.locked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        ConnectButton(st.phase, ::toggle)
        Spacer(Modifier.height(16.dp))

        val title = when (st.phase) {
            Phase.IDLE -> R.string.disconnected
            Phase.CONNECTING -> R.string.connecting
            Phase.CONNECTED -> R.string.connected
            Phase.RECONNECTING -> R.string.reconnecting
            Phase.STOPPING -> R.string.stopping
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        val showAttempt = st.phase == Phase.RECONNECTING || (st.phase == Phase.CONNECTING && st.attempt > 1)
        if (showAttempt) {
            Text(stringResource(R.string.attempt_of, st.attempt, MAX_TRIES), color = MaterialTheme.colorScheme.tertiary)
        } else if (idle && st.error != null) {
            Text(st.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        } else if (idle) {
            Text(stringResource(R.string.tap_to_connect), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(onClick = ::runPing, enabled = !testing && st.phase != Phase.STOPPING) {
            Text(if (testing) stringResource(R.string.ping_testing) else stringResource(R.string.ping_test))
        }
        testResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.ping), ping?.let { stringResource(R.string.ms_fmt, it) } ?: "—", Modifier.weight(1f))
                    Stat(stringResource(R.string.download), if (s.vpnMode && st.phase == Phase.CONNECTED) fmtRate(rx) else "—", Modifier.weight(1f))
                    Stat(stringResource(R.string.upload), if (s.vpnMode && st.phase == Phase.CONNECTED) fmtRate(tx) else "—", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.protocol), if (st.phase == Phase.CONNECTED) s.activeLabel() else "—", Modifier.weight(1f))
                    Stat(stringResource(R.string.exit_ip), exit ?: "—", Modifier.weight(1f))
                }
                if (!s.vpnMode) {
                    Text(
                        stringResource(R.string.proxy_info, "127.0.0.1:${s.socksPort}") +
                            if (s.httpEnabled) "   " + stringResource(R.string.http_info, "127.0.0.1:${s.httpPort}") else "",
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clickable(onClick = openLogs)) {
                logs.takeLast(3).forEach {
                    Text(
                        it, maxLines = 1, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

@Composable
private fun FamilyPanel(fam: Family, s: Settings, enabled: Boolean) {
    var regionDialog by remember { mutableStateOf(false) }
    when (fam) {
        Family.AETHER -> {
            Label(stringResource(R.string.route))
            Chips(
                listOf(Route.AETHER to "Aether", Route.AETHER_PSIPHON to "Aether → Psiphon", Route.AETHER_TOR to "Aether → Tor"),
                s.route, enabled
            ) { v -> up { it.copy(route = v) } }
            Label(stringResource(R.string.protocol))
            Chips(Protocol.values().map { it to it.label }, s.protocol, enabled) { v -> up { it.copy(protocol = v) } }
            if (s.protocol == Protocol.MASQUE || s.protocol == Protocol.MIM) {
                Label(stringResource(R.string.transport))
                Chips(listOf("h3" to "HTTP/3 (QUIC)", "h2" to "HTTP/2 (TCP)"), s.carrier, enabled) { v -> up { it.copy(carrier = v) } }
            }
            if (s.route == Route.AETHER_PSIPHON) RegionButton(s, enabled) { regionDialog = true }
        }
        Family.PSIPHON -> {
            Label(stringResource(R.string.route))
            Chips(
                listOf(Route.PSIPHON_ONLY to "Psiphon", Route.PSIPHON_REVERSE to "Psiphon → Aether"),
                s.route, enabled
            ) { v -> up { it.copy(route = v) } }
            RegionButton(s, enabled) { regionDialog = true }
            Label(stringResource(R.string.psiphon_mode))
            Chips(listOf("auto" to "Auto", "cdn" to "CDN"), s.psiphonMode, enabled) { v -> up { it.copy(psiphonMode = v) } }
        }
        Family.TOR -> {
            Label(stringResource(R.string.route))
            Chips(
                listOf(Route.TOR_ONLY to "Tor", Route.TOR_REVERSE to "Tor → Aether"),
                s.route, enabled
            ) { v -> up { it.copy(route = v) } }
            Label(stringResource(R.string.bridges))
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
    if (regionDialog) RegionDialog(s.psiphonRegion, onPick = { cc -> up { it.copy(psiphonRegion = cc) }; regionDialog = false }) { regionDialog = false }
}

@Composable
fun RegionButton(s: Settings, enabled: Boolean, onClick: () -> Unit) {
    Label(stringResource(R.string.region))
    val text = if (s.psiphonRegion.isBlank()) "🌐  " + stringResource(R.string.region_auto)
    else PsiphonRegions.flag(s.psiphonRegion) + "  " + PsiphonRegions.name(s.psiphonRegion, s.language)
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun ConnectButton(phase: Phase, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val busy = phase == Phase.CONNECTING || phase == Phase.RECONNECTING || phase == Phase.STOPPING
    val bg by animateColorAsState(
        when (phase) {
            Phase.IDLE -> c.surfaceVariant
            Phase.CONNECTED -> c.primary
            else -> c.tertiary
        }, label = "bg"
    )
    val fg = when (phase) {
        Phase.IDLE -> c.onSurfaceVariant
        Phase.CONNECTED -> c.onPrimary
        else -> c.onTertiary
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale"
    )
    Box(
        Modifier.size(180.dp).scale(if (busy) pulse else 1f).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PowerIcon(fg) }
}

@Composable
private fun PowerIcon(color: Color) {
    Canvas(Modifier.size(72.dp)) {
        val w = 7.dp.toPx()
        val inset = w
        drawArc(
            color = color, startAngle = -50f, sweepAngle = 280f, useCenter = false,
            topLeft = Offset(inset, inset + 6.dp.toPx()),
            size = Size(size.width - 2 * inset, size.height - 2 * inset - 6.dp.toPx()),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(size.width / 2, 0f + w / 2), Offset(size.width / 2, size.height * 0.5f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun RegionDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val lang = SettingsStore.current.language
    var q by remember { mutableStateOf("") }
    val all = remember(lang) { PsiphonRegions.codes.map { it to PsiphonRegions.name(it, lang) } }
    val list = all.filter { q.isBlank() || it.second.contains(q, true) || it.first.contains(q, true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.region)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp)) {
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(flag, fontSize = 22.sp)
        Spacer(Modifier.size(12.dp))
        Text(name, Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}
