// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.TouchModelDao
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsWithoutKey
import kotlin.math.sqrt

/**
 * "Learned typing model" page: shows what the adaptive-key-geometry feature has learned per key
 * (mean landing offset, spread/consistency, sample count) and lets the user reset it. Reuses the
 * standard settings scaffold by rendering one content [Setting]; see [AdaptiveTypingStatsContent].
 */
@Composable
fun AdaptiveTypingStatsScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.adaptive_key_geometry_stats_title),
        settings = listOf(SettingsWithoutKey.ADAPTIVE_TYPING_STATS_CONTENT)
    )
}

private fun loadStats(context: Context): List<TouchModelDao.Stat> {
    val dao = TouchModelDao.getInstance(context) ?: return emptyList()
    return dao.all().sortedByDescending { it.count }
}

/** A mock QWERTY keyboard with, on each key, a dot showing where the user tends to land
 *  (offset from the key center, as a fraction of the key) and a faint ring for the spread.
 *  Confident keys (enough samples) are drawn in the accent color, still-learning keys faded. */
@Composable
private fun MockKeyboardHeatmap(stats: List<TouchModelDao.Stat>) {
    val orientation = LocalConfiguration.current.orientation
    val pref = stats.filter { it.orientation == orientation }
    val byCode = (if (pref.isNotEmpty()) pref else stats).associateBy { it.keyCode }

    val keyBg = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val faded = MaterialTheme.colorScheme.outline
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(10f / (3f * 1.35f))
            .padding(vertical = 8.dp)
    ) {
        val cellW = size.width / 10f
        val cellH = cellW * 1.35f
        labelPaint.color = labelColor.toArgb()
        labelPaint.textSize = cellW * 0.42f
        rows.forEachIndexed { r, row ->
            val startX = (size.width - row.length * cellW) / 2f
            val top = r * cellH
            row.forEachIndexed { i, ch ->
                val left = startX + i * cellW
                val cx = left + cellW / 2f
                val cy = top + cellH / 2f
                drawRoundRect(
                    color = keyBg,
                    topLeft = Offset(left + 3f, top + 3f),
                    size = Size(cellW - 6f, cellH - 6f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    ch.uppercase(), cx,
                    cy - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint
                )
                val s = byCode[ch.code]
                if (s != null && s.keyWidth > 0 && s.keyHeight > 0) {
                    val fx = (s.meanDx / s.keyWidth).coerceIn(-0.5f, 0.5f)
                    val fy = (s.meanDy / s.keyHeight).coerceIn(-0.5f, 0.5f)
                    val dotX = cx + fx * cellW
                    val dotY = cy + fy * cellH
                    val col = if (s.count >= TouchModelDao.MIN_CONFIDENT_SAMPLES) accent else faded
                    val spreadFrac = sqrt(
                        (((s.varDx / (s.keyWidth.toFloat() * s.keyWidth)) +
                          (s.varDy / (s.keyHeight.toFloat() * s.keyHeight))) / 2f).coerceAtLeast(0f)
                    ).coerceIn(0f, 0.5f)
                    if (spreadFrac > 0f)
                        drawCircle(col.copy(alpha = 0.15f), spreadFrac * cellW, Offset(dotX, dotY))
                    drawLine(col, Offset(cx, cy), Offset(dotX, dotY), strokeWidth = 2.5f)
                    drawCircle(col, cellW * 0.07f, Offset(dotX, dotY))
                }
            }
        }
    }
}

/** The dynamic stats body. Rendered as the content of a single registered Setting. */
@Composable
fun AdaptiveTypingStatsContent() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(loadStats(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.adaptive_key_geometry_stats_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (stats.isEmpty()) {
            Text(
                stringResource(R.string.adaptive_key_geometry_stats_empty),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            MockKeyboardHeatmap(stats)
            Text(
                stringResource(R.string.adaptive_key_geometry_stats_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            stats.forEach { s ->
                val letter = try { String(Character.toChars(s.keyCode)) } catch (e: Throwable) { "?" }
                val orient = if (s.orientation == 2) "L" else "P" // 2 == landscape
                val spread = sqrt(((s.varDx + s.varDy) / 2f).coerceAtLeast(0f)).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$letter ($orient)", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Δ(${s.meanDx.toInt()}, ${s.meanDy.toInt()})px  ±${spread}px  n=${s.count}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Button(
            onClick = {
                TouchModelDao.getInstance(context)?.clear()
                stats = emptyList()
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.adaptive_key_geometry_reset))
        }
    }
}
