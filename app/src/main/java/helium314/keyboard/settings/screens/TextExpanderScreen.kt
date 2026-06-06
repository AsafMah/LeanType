/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.SwitchPreference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextExpanderScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()

    var prefixText by remember {
        mutableStateOf(TextExpanderUtils.getPrefix(context))
    }
    
    var isExpanderEnabled by remember {
        mutableStateOf(TextExpanderUtils.isEnabled(context))
    }

    var shortcutsMap by remember {
        mutableStateOf(TextExpanderUtils.getShortcuts(context))
    }

    var isGuideExpanded by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableStateOf(TextFieldValue("")) }
    var originalShortcutToEdit by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchScreen(
            onClickBack = onClickBack,
            title = {
                Text(
                    text = "Text Expander",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            filteredItems = { term ->
                shortcutsMap.entries
                    .filter { (shortcut, template) ->
                        shortcut.contains(term, ignoreCase = true) ||
                        template.contains(term, ignoreCase = true)
                    }
                    .map { Pair(it.key, it.value) }
            },
            itemContent = { (shortcut, template) ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ShortcutItem(
                        shortcut = shortcut,
                        template = template,
                        prefix = prefixText,
                        onEdit = {
                            editingShortcut = shortcut
                            editingTemplate = TextFieldValue(template)
                            originalShortcutToEdit = shortcut
                            showAddDialog = true
                        },
                        onDelete = {
                            val updated = shortcutsMap.toMutableMap()
                            updated.remove(shortcut)
                            shortcutsMap = updated
                            TextExpanderUtils.saveShortcuts(context, updated)
                        }
                    )
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Premium Collapsible Feature Guide Card
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Clickable Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isGuideExpanded = !isGuideExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "💡 Quick Feature Guide",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_left),
                                    contentDescription = if (isGuideExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate(if (isGuideExpanded) -90f else 180f)
                                )
                            }
                            
                            AnimatedVisibility(visible = isGuideExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "How it works:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    // Step 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "1")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Set a Shortcut Prefix",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Choose a prefix like '.' or ';' under prefix configuration to prevent accidental expansions.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Step 2
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "2")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Add Custom Shortcuts",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Define triggers (e.g. 'brb') and their expanded templates (e.g. 'Be right back!').",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Step 3
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "3")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Type Prefix + Shortcut",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Type your prefix followed by the shortcut keyword (e.g., '.brb') and press Space or punctuation on the keyboard to expand instantly.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Supported Template Placeholders:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                PlaceholderChip(tag = "%date%", desc = "Date (YYYY-MM-DD)")
                                                PlaceholderChip(tag = "%time%", desc = "Time (24h, HH:MM)")
                                                PlaceholderChip(tag = "%time12%", desc = "Time (12h, hh:mm AM/PM)")
                                                PlaceholderChip(tag = "%year%", desc = "Year (YYYY)")
                                                PlaceholderChip(tag = "%week%", desc = "Week of year (1-53)")
                                                PlaceholderChip(tag = "%battery%", desc = "Battery level (e.g. 85%)")
                                                PlaceholderChip(tag = "%greeting%", desc = "Time-gated greeting")
                                                PlaceholderChip(tag = "%tomorrow%", desc = "Tomorrow's date (YYYY-MM-DD)")
                                            }
                                            Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                PlaceholderChip(tag = "%clipboard%", desc = "Clipboard content")
                                                PlaceholderChip(tag = "%day%", desc = "Day name (e.g. Monday)")
                                                PlaceholderChip(tag = "%month%", desc = "Month (e.g. June)")
                                                PlaceholderChip(tag = "%language%", desc = "Keyboard language (e.g. English)")
                                                PlaceholderChip(tag = "%cursor%", desc = "Cursor position after expansion")
                                                PlaceholderChip(tag = "%bullets%", desc = "Bullet list (supports e.g. %bullets_5%)")
                                                PlaceholderChip(tag = "%list%", desc = "Numbered list (supports e.g. %list_5%)")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1. Master Switch Toggle
                    SwitchPreference(
                        name = "Enable Text Expander",
                        key = TextExpanderUtils.PREF_ENABLED,
                        default = false,
                        description = "Auto-expand shortcuts on space or punctuation natively and securely.",
                        onCheckedChange = { isExpanderEnabled = it }
                    )

                    // 2. Custom Prefix Configuration
                    OutlinedTextField(
                        value = prefixText,
                        onValueChange = {
                            prefixText = it
                            prefs.edit { putString(TextExpanderUtils.PREF_PREFIX, it) }
                        },
                        label = { Text("Shortcut Prefix (e.g. '..', '.', ';', or blank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = isExpanderEnabled
                    )

                    // 3. Section Title / Header for shortcuts
                    Text(
                        text = "Custom Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // 4. List of saved shortcuts
                    if (shortcutsMap.isEmpty()) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit),
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "No shortcuts configured yet.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Tap the 'Add Shortcut' floating button in the bottom corner to quickly create your first smart text expansion template.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f
                                )
                            }
                        }
                    } else {
                        shortcutsMap.forEach { (shortcut, template) ->
                            ShortcutItem(
                                shortcut = shortcut,
                                template = template,
                                prefix = prefixText,
                                onEdit = {
                                    editingShortcut = shortcut
                                    editingTemplate = TextFieldValue(template)
                                    originalShortcutToEdit = shortcut
                                    showAddDialog = true
                                },
                                onDelete = {
                                    val updated = shortcutsMap.toMutableMap()
                                    updated.remove(shortcut)
                                    shortcutsMap = updated
                                    TextExpanderUtils.saveShortcuts(context, updated)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        )

        // Floating Action Button to Add New Shortcut
        if (isExpanderEnabled && !WindowInsets.isImeVisible) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingShortcut = ""
                    editingTemplate = TextFieldValue("")
                    originalShortcutToEdit = null
                    showAddDialog = true
                },
                text = { Text("Add Shortcut") },
                icon = { Icon(painter = painterResource(R.drawable.ic_edit), "Add Shortcut") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(all = 16.dp)
                    .then(Modifier.safeDrawingPadding())
            )
        }
    }

    // Add / Edit Shortcut Dialog
    if (showAddDialog) {
        val focusRequester = remember { FocusRequester() }
        val isEditMode = originalShortcutToEdit != null
        
        ThreeButtonAlertDialog(
            onDismissRequest = { showAddDialog = false },
            onConfirmed = {
                val updated = shortcutsMap.toMutableMap()
                if (isEditMode && originalShortcutToEdit != editingShortcut) {
                    updated.remove(originalShortcutToEdit)
                }
                updated[editingShortcut.trim()] = editingTemplate.text
                shortcutsMap = updated
                TextExpanderUtils.saveShortcuts(context, updated)
                showAddDialog = false
            },
            checkOk = { editingShortcut.trim().isNotEmpty() && editingTemplate.text.isNotEmpty() },
            confirmButtonText = if (isEditMode) "Save" else "Add",
            neutralButtonText = if (isEditMode) "Delete" else null,
            onNeutral = {
                if (isEditMode) {
                    val updated = shortcutsMap.toMutableMap()
                    updated.remove(originalShortcutToEdit)
                    shortcutsMap = updated
                    TextExpanderUtils.saveShortcuts(context, updated)
                }
                showAddDialog = false
            },
            title = {
                Text(text = if (isEditMode) "Edit Shortcut" else "Add Shortcut")
            },
            content = {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = editingShortcut,
                        onValueChange = { editingShortcut = it.replace(" ", "") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        label = { Text("Shortcut (e.g. 'brb', 'em')") }
                    )
                    
                    OutlinedTextField(
                        value = editingTemplate,
                        onValueChange = { editingTemplate = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("Template Expansion") },
                        placeholder = { Text("Be right back! or My email is %clipboard%") }
                    )

                    Text(
                        text = "Quick Placeholders (tap to insert at cursor):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tags = listOf(
                            "%date%", "%time%", "%time12%", "%clipboard%",
                            "%day%", "%month%", "%year%", "%week%",
                            "%battery%", "%language%", "%cursor%", "%greeting%",
                            "%tomorrow%", "%bullets%", "%list%"
                        )
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable {
                                        val text = editingTemplate.text
                                        val selection = editingTemplate.selection
                                        val start = selection.start
                                        val end = selection.end
                                        val newText = text.substring(0, start) + tag + text.substring(end)
                                        val newSelectionRange = androidx.compose.ui.text.TextRange(start + tag.length)
                                        editingTemplate = TextFieldValue(text = newText, selection = newSelectionRange)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ShortcutItem(
    shortcut: String,
    template: String,
    prefix: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$prefix$shortcut",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = template,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    fontFamily = if (template.contains("%")) androidx.compose.ui.text.font.FontFamily.Monospace else null
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bin),
                        contentDescription = "Delete shortcut",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left),
                    contentDescription = "Edit shortcut",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.rotate(180f)
                )
            }
        }
    }
}

@Composable
private fun StepBadge(num: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = num,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlaceholderChip(tag: String, desc: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.9f
            )
        }
    }
}
