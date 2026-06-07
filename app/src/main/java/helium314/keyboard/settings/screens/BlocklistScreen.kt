// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.settings.SearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed class BlocklistItem {
    data class Header(val tag: String, val displayName: String) : BlocklistItem()
    data class Word(val locale: String, val word: String) : BlocklistItem()
}

@Composable
fun BlocklistScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<BlocklistItem>()) }

    fun loadItems(): List<BlocklistItem> {
        val blacklistsDir = File(ctx.filesDir, "blacklists")
        val result = mutableListOf<BlocklistItem>()
        if (!blacklistsDir.exists()) return result
        blacklistsDir.listFiles { f -> f.extension == "txt" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.forEach { file ->
                val tag = file.nameWithoutExtension
                val words = file.readLines().map { it.trim() }.filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    val displayName = tag.constructLocale().localizedDisplayName(ctx.resources)
                    result.add(BlocklistItem.Header(tag, displayName))
                    words.forEach { word -> result.add(BlocklistItem.Word(tag, word)) }
                }
            }
        return result
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadItems() }
        items = loaded
    }

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.blocklist)) },
        filteredItems = { term ->
            if (term.isBlank()) items
            else items.filterIsInstance<BlocklistItem.Word>()
                .filter { it.word.startsWith(term, ignoreCase = true) }
        },
        itemContent = { item ->
            when (item) {
                is BlocklistItem.Header -> {
                    androidx.compose.material3.Divider()
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                is BlocklistItem.Word -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Text(item.word, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val file = File(File(ctx.filesDir, "blacklists"), "${item.locale}.txt")
                                val newLines = file.readLines()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && it != item.word }
                                file.writeText(newLines.joinToString("\n"))
                                val updated = loadItems()
                                withContext(Dispatchers.Main) { items = updated }
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.blocklist_remove)
                            )
                        }
                    }
                }
            }
        },
        content = if (items.isEmpty()) ({
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.blocklist_empty))
            }
        }) else null
    )
}
