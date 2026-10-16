package app.vpn8

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB4FF), onPrimary = Color(0xFF002B6B),
    primaryContainer = Color(0xFF1F3F8F), onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFFB9A8FF), tertiary = Color(0xFFFFC66B), onTertiary = Color(0xFF3B2800),
    background = Color(0xFF0E1020), onBackground = Color(0xFFE6E7F2),
    surface = Color(0xFF0E1020), onSurface = Color(0xFFE6E7F2),
    surfaceVariant = Color(0xFF23263D), onSurfaceVariant = Color(0xFFB8BAD0),
    surfaceContainer = Color(0xFF171A2E), surfaceContainerHigh = Color(0xFF1E2238),
    outline = Color(0xFF6C6F8C), error = Color(0xFFFF8A8A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F4FD0), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF), onPrimaryContainer = Color(0xFF001A5C),
    secondary = Color(0xFF6A4FC8), tertiary = Color(0xFFB36A00), onTertiary = Color.White,
    background = Color(0xFFF7F7FD), surface = Color(0xFFF7F7FD),
    surfaceVariant = Color(0xFFE3E4F3), surfaceContainer = Color(0xFFEDEDF8), surfaceContainerHigh = Color(0xFFE6E7F4),
)

@Composable
fun AppTheme(s: Settings, dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        s.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun isDark(s: Settings): Boolean = when (s.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
