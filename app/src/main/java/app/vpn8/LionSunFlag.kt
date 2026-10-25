package app.vpn8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val GREEN = Color(0xFF239F40)
private val RED = Color(0xFFDA0000)
private val GOLD = Color(0xFFF2B000)
private val AMBER = Color(0xFF965C14)
private val FACE = Color(0xFFBE8228)
private val SWORD = Color(0xFF5A5A5A)

/**
 * Flag of Iran with the Lion and Sun (the historical flag). There is no emoji for it, so it is drawn:
 * the green/white/red bands with a sun and a lion carrying a sword.
 */
@Composable
fun LionSunFlag(modifier: Modifier = Modifier.size(width = 26.dp, height = 18.dp)) {
    Canvas(modifier.clip(RoundedCornerShape(3.dp))) {
        val w = size.width
        val h = size.height
        val bh = h / 3f
        drawRect(Color.White, size = size)
        drawRect(GREEN, size = Size(w, bh))
        drawRect(RED, topLeft = Offset(0f, 2 * bh), size = Size(w, bh))

        val r = 0.34f * h
        val cx = w / 2f
        val cy = h / 2f
        fun p(x: Float, y: Float) = Offset(cx + x * r, cy + y * r)

        // sun and its rays
        val sx = 0.28f
        val sy = -0.18f
        for (i in 0 until 16) {
            val a = i * Math.PI.toFloat() / 8f
            drawLine(
                GOLD, p(sx + 0.62f * cos(a), sy + 0.62f * sin(a)), p(sx + 0.88f * cos(a), sy + 0.88f * sin(a)),
                strokeWidth = r * 0.07f, cap = StrokeCap.Butt
            )
        }
        drawCircle(GOLD, r * 0.55f, p(sx, sy))

        // lion: body, legs, mane, face, tail, sword
        drawRoundRect(AMBER, p(-0.6f, 0f), Size(1.15f * r, 0.45f * r), CornerRadius(0.2f * r))
        for (lx in floatArrayOf(-0.5f, -0.28f, 0.22f, 0.42f)) {
            drawRect(AMBER, p(lx, 0.35f), Size(0.13f * r, 0.43f * r))
        }
        drawCircle(AMBER, r * 0.38f, p(-0.62f, -0.08f))
        drawCircle(FACE, r * 0.2f, p(-0.72f, -0.02f))
        val tail = Path().apply {
            moveTo(p(0.55f, 0.1f).x, p(0.55f, 0.1f).y)
            lineTo(p(0.78f, -0.05f).x, p(0.78f, -0.05f).y)
            lineTo(p(0.72f, -0.35f).x, p(0.72f, -0.35f).y)
        }
        drawPath(tail, AMBER, style = Stroke(width = r * 0.08f, cap = StrokeCap.Round))
        drawLine(SWORD, p(-0.45f, -0.15f), p(-0.2f, -0.85f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
    }
}

/** The flag for a country code; Iran gets the Lion and Sun, every other country its emoji flag. */
@Composable
fun CountryFlag(cc: String) {
    if (cc.equals("IR", ignoreCase = true)) {
        LionSunFlag()
    } else {
        androidx.compose.material3.Text(PsiphonRegions.flag(cc), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}
