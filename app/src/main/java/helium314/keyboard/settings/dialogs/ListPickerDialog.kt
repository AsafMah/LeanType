// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchIcon
import helium314.keyboard.settings.CloseIcon
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.previewDark

// taken from StreetComplete
@Composable
fun <T: Any> ListPickerDialog(
    onDismissRequest: () -> Unit,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    selectedItem: T? = null,
    getItemName: (@Composable (T) -> String) = { it.toString() },
    confirmImmediately: Boolean = true,
    showRadioButtons: Boolean = true,
    allowSearch: Boolean = items.size > 10,
) {
    var selected by remember { mutableStateOf(selectedItem) }
    var searchQuery by remember { mutableStateOf("") }
    val state = rememberLazyListState()

    val itemNames = items.associateWith { getItemName(it) }
    val filteredItems = remember(items, searchQuery, itemNames) {
        if (searchQuery.isBlank()) items
        else items.filter { (itemNames[it] ?: "").contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(selectedItem) {
        val index = filteredItems.indexOf(selectedItem)
        if (index != -1) state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { selected?.let { onItemSelected(it) } },
        confirmButtonText = if (confirmImmediately) null else stringResource(android.R.string.ok),
        checkOk = { selected != null },
        modifier = modifier,
        title = title,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                Column {
                    if (allowSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            leadingIcon = { SearchIcon() },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        CloseIcon(android.R.string.cancel)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                    }
                    LazyColumn(state = state) {
                        items(filteredItems, key = { it.toString() }) { item ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        if (confirmImmediately) {
                                            onDismissRequest()
                                            onItemSelected(item)
                                        }
                                        selected = item
                                    }
                                    .padding(horizontal = if (showRadioButtons) 8.dp else 16.dp)
                                    .heightIn(min = 56.dp)
                            ) {
                                if (showRadioButtons)
                                    RadioButton(
                                        selected = selected == item,
                                        onClick = {
                                            if (confirmImmediately) {
                                                onDismissRequest()
                                                onItemSelected(item)
                                            }
                                            selected = item
                                        }
                                    )
                                Text(
                                    text = getItemName(item),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewListPickerDialog() {
    val items = remember { (0..<5).toList() }
    Theme(previewDark) {
        ListPickerDialog(
            onDismissRequest = {},
            items = items,
            onItemSelected = {},
            title = { Text("Select something") },
            selectedItem = 2,
            getItemName = { "Item $it" },
        )
    }
}
