package app.vpn8

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val GITHUB_URL = "https://github.com/Mrmmd2004/8vpn"
const val TELEGRAM_URL = "https://t.me/Clubapp8"

@Composable
fun LogsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val logs by Engine.logs.collectAsStateWithLifecycle()
    val text = logs.joinToString("\n")
    Page(stringResource(R.string.logs), back, actions = {
        IconBtn(Ico.COPY, {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("8vpn-log", text))
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
        })
        IconBtn(Ico.SHARE, {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            ctx.startActivity(Intent.createChooser(i, null))
        })
        IconBtn(Ico.TRASH, { Engine.logs.value = emptyList() }, tint = c.error)
    }) {
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon8(Ico.LOGS, c.outline, 44.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.no_logs), color = c.onSurfaceVariant)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(c.surfaceContainer)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
                    items(logs.size) { i ->
                        LogLine(logs[logs.size - 1 - i])
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val c = MaterialTheme.colorScheme
    val hasStamp = line.length > 9 && line[2] == ':' && line[5] == ':'
    val bad = line.contains("error", true) || line.contains("fail", true)
    val body = if (bad) c.error else c.onSurface
    val annotated = buildAnnotatedString {
        if (hasStamp) {
            withStyle(SpanStyle(color = c.onSurfaceVariant)) { append(line.substring(0, 8)) }
            withStyle(SpanStyle(color = body)) { append(line.substring(8)) }
        } else {
            withStyle(SpanStyle(color = body)) { append(line) }
        }
    }
    Text(annotated, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var upd by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    Page(stringResource(R.string.about), back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(84.dp)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(c.primary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("v" + BuildConfig.VERSION_NAME, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
                    InfoRow(stringResource(R.string.core_version), "2.4.0")
                    InfoRow(stringResource(R.string.tunnel_version), "2.19.0")
                }
            }
            UpdateRow(upd) {
                if (upd != UpdateState.Checking) {
                    upd = UpdateState.Checking
                    scope.launch {
                        upd = Updater.check().fold(
                            onSuccess = { if (it.newer) UpdateState.Available(it) else UpdateState.UpToDate },
                            onFailure = { UpdateState.Failed }
                        )
                    }
                }
            }
            (upd as? UpdateState.Available)?.let { st ->
                Button(
                    onClick = { uri.openUri(st.info.url) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.update_open_github), fontWeight = FontWeight.SemiBold) }
            }
            LinkRow(Ico.CODE, stringResource(R.string.github), "Mrmmd2004/8vpn") { uri.openUri(GITHUB_URL) }
            LinkRow(Ico.SEND, stringResource(R.string.telegram), "@Clubapp8") { uri.openUri(TELEGRAM_URL) }
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.license_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.primary)
                    Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    Text("github.com/CluvexStudio/Aether\ngithub.com/heiher/hev-socks5-tunnel", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val info: Updater.Info) : UpdateState
}

@Composable
private fun UpdateRow(st: UpdateState, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val avail = st is UpdateState.Available
    val title = stringResource(R.string.update_check)
    val sub = when (st) {
        UpdateState.Idle -> stringResource(R.string.update_tap)
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_latest)
        UpdateState.Failed -> stringResource(R.string.update_failed)
        is UpdateState.Available -> stringResource(R.string.update_available, st.info.tag)
    }
    AppCard(Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon8(if (avail) Ico.DOWN else Ico.REFRESH, c.primary, 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 12.sp, color = if (st == UpdateState.Failed) c.error else if (avail) c.primary else c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkRow(ico: Ico, title: String, sub: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    AppCard(Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon8(ico, c.primary, 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 12.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
            Icon8(Ico.CHEVRON, c.onSurfaceVariant, 20.dp, Modifier.rotate(-90f))
        }
    }
}

private data class AppItem(val label: String, val pkg: String)

private val iconCache = android.util.LruCache<String, ImageBitmap>(200)

/** The app's real launcher icon (loaded off the main thread and cached); the first letter shows until it is ready. */
@Composable
private fun AppIcon(pkg: String, label: String) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val icon by produceState<ImageBitmap?>(iconCache.get(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }?.also { iconCache.put(pkg, it) }
        }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, contentDescription = label, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), color = c.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SplitScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    Page(stringResource(R.string.split_tunnel), back) {
        SearchField(q, { q = it }, stringResource(R.string.search))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.split_summary, s.splitApps.size), style = MaterialTheme.typography.labelLarge,
            color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        val shown = apps.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.pkg }) { a ->
                val checked = a.pkg in s.splitApps
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (checked) c.primary.copy(alpha = 0.12f) else c.surfaceContainer)
                        .clickable {
                            SettingsStore.update { it.copy(splitApps = if (checked) it.splitApps - a.pkg else it.splitApps + a.pkg) }
                        }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(a.pkg, a.label)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(a.label, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(a.pkg, fontSize = 11.sp, maxLines = 1, color = c.onSurfaceVariant)
                    }
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
