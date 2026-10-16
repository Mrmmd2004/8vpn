package app.vpn8

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by SettingsStore.flow.collectAsStateWithLifecycle()
            val dark = isDark(s)
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { dark }
                )
                onDispose { }
            }
            AppTheme(s, dark) { Root(onLanguageChanged = { recreate() }) }
        }
    }
}

@Composable
private fun Root(onLanguageChanged: () -> Unit) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableIntStateOf(HOME) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = screen != HOME) {
        screen = if (screen == SPLIT || screen == ABOUT) SETTINGS else HOME
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            HOME -> HomeScreen(openSettings = { screen = SETTINGS }, openLogs = { screen = LOGS })
            SETTINGS -> SettingsScreen(
                back = { screen = HOME }, openLogs = { screen = LOGS }, openAbout = { screen = ABOUT },
                openSplit = { screen = SPLIT }, onLanguageChanged = onLanguageChanged
            )
            LOGS -> LogsScreen(back = { screen = HOME })
            ABOUT -> AboutScreen(back = { screen = SETTINGS })
            SPLIT -> SplitScreen(back = { screen = SETTINGS })
        }
    }
}

private const val HOME = 0
private const val SETTINGS = 1
private const val LOGS = 2
private const val ABOUT = 3
private const val SPLIT = 4
