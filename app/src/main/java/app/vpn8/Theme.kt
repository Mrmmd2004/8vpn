package app.vpn8

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Extra brand colors that Material's color scheme has no slot for. */
@Immutable
class Extra(val a: Color, val b: Color, val busy: Color) {
    val gradient: Brush get() = Brush.linearGradient(listOf(a, b))
}

val LocalExtra = staticCompositionLocalOf { Extra(Color(0xFF3DDBC4), Color(0xFF7C8CFF), Color(0xFFFFB84D)) }

private val Dark = darkColorScheme(
    primary = Color(0xFF8D9BFF), onPrimary = Color(0xFF0B1240),
    primaryContainer = Color(0xFF2A3487), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF3DDBC4), onSecondary = Color(0xFF00382F),
    tertiary = Color(0xFFFFB84D), onTertiary = Color(0xFF3B2800),
    background = Color(0xFF0A0E1A), onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF0A0E1A), onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF222B42), onSurfaceVariant = Color(0xFFA9B1CC),
    surfaceContainer = Color(0xFF131A2B), surfaceContainerHigh = Color(0xFF1B2438),
    surfaceContainerHighest = Color(0xFF232E47),
    outline = Color(0xFF55607F), outlineVariant = Color(0xFF263050),
    error = Color(0xFFFF8A8A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4F5BD5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF0B1470),
    secondary = Color(0xFF0FA58F), onSecondary = Color.White,
    tertiary = Color(0xFFD98200), onTertiary = Color.White,
    background = Color(0xFFF4F6FC), onBackground = Color(0xFF151A2C),
    surface = Color(0xFFF4F6FC), onSurface = Color(0xFF151A2C),
    surfaceVariant = Color(0xFFE4E8F5), onSurfaceVariant = Color(0xFF586080),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEEF1FA),
    surfaceContainerHighest = Color(0xFFE6EAF7),
    outline = Color(0xFF8B93B0), outlineVariant = Color(0xFFDDE2F0),
    error = Color(0xFFD93A3A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(s: Settings, dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dynamic = s.dynamicColor && Build.VERSION.SDK_INT >= 31
    val scheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val extra = if (dynamic) Extra(scheme.primary, scheme.tertiary, scheme.tertiary)
    else Extra(scheme.secondary, scheme.primary, scheme.tertiary)
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}

@Composable
fun isDark(s: Settings): Boolean = when (s.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
