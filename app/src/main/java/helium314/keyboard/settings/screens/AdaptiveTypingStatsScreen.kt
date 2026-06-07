// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

private data class StatRow(val label: String, val dx: Int, val dy: Int, val spread: Int, val count: Int)

private fun loadStatRows(context: Context): List<StatRow> {
    val dao = TouchModelDao.getInstance(context) ?: return emptyList()
    return dao.all()
        .sortedByDescending { it.count }
        .map { s ->
            val letter = try { String(Character.toChars(s.keyCode)) } catch (e: Throwable) { "?" }
            val orient = if (s.orientation == 2) "L" else "P" // 2 == landscape
            val spread = sqrt(((s.varDx + s.varDy) / 2f).coerceAtLeast(0f)).toInt()
            StatRow("$letter ($orient)", s.meanDx.toInt(), s.meanDy.toInt(), spread, s.count)
        }
}

/** The dynamic stats body. Rendered as the content of a single registered Setting. */
@Composable
fun AdaptiveTypingStatsContent() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(loadStatRows(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.adaptive_key_geometry_stats_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.adaptive_key_geometry_stats_empty),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            rows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(r.label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Δ(${r.dx}, ${r.dy})px  ±${r.spread}px  n=${r.count}",
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
                rows = emptyList()
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.adaptive_key_geometry_reset))
        }
    }
}
