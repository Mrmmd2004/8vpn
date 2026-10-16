package app.vpn8

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val GITHUB_URL = "https://github.com/Mrmmd2004/8vpn"
const val TELEGRAM_URL = "https://t.me/Clubapp8"

@Composable
fun LogsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val logs by Engine.logs.collectAsStateWithLifecycle()
    val text = logs.joinToString("\n")
    Page(stringResource(R.string.logs), back, actions = {
        TextButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("8vpn-log", text))
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.copy)) }
        TextButton(onClick = {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            ctx.startActivity(Intent.createChooser(i, null))
        }) { Text(stringResource(R.string.share)) }
        TextButton(onClick = { Engine.logs.value = emptyList() }) { Text(stringResource(R.string.clear)) }
    }) {
        if (logs.isEmpty()) {
            Text(stringResource(R.string.no_logs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(logs.size) { i ->
                    Text(logs[logs.size - 1 - i], fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val uri = LocalUriHandler.current
    Page(stringResource(R.string.about), back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
            InfoRow(stringResource(R.string.core_version), "2.3.0")
            InfoRow(stringResource(R.string.tunnel_version), "2.19.0")
            OutlinedButton(onClick = { uri.openUri(GITHUB_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.github) + "  ·  Mrmmd2004/8vpn")
            }
            OutlinedButton(onClick = { uri.openUri(TELEGRAM_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.telegram) + "  ·  @Clubapp8")
            }
            Text(stringResource(R.string.license_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodyMedium)
            Text("github.com/CluvexStudio/Aether\ngithub.com/heiher/hev-socks5-tunnel", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private data class AppItem(val label: String, val pkg: String)

@Composable
fun SplitScreen(back: () -> Unit) {
    val ctx = LocalContext.current
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
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val shown = apps.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { a ->
                val checked = a.pkg in s.splitApps
                Row(
                    Modifier.fillMaxWidth().clickable {
                        SettingsStore.update { it.copy(splitApps = if (checked) it.splitApps - a.pkg else it.splitApps + a.pkg) }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(a.label)
                        Text(a.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
