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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    var editingTemplate by remember { mutableStateOf("") }
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
            filteredItems = { emptyList<Int>() },
            itemContent = { },
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
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
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
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "How it works:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "1. Set a Prefix (e.g. '.' or ';') to avoid accidental triggers.\n" +
                                                    "2. Add a Shortcut keyword (e.g. 'brb') and its Template expansion.\n" +
                                                    "3. Type your Prefix + Shortcut on the keyboard (e.g. '.brb') and press Space or Punctuation to expand instantly!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "How Template Placeholders Work:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Placeholders are special tags you can write in your templates. When you type the shortcut, LeanType automatically replaces them with real-time values (like the current date, time, or your clipboard content) before inserting the text.\n\n" +
                                                    "Example Template: 'Hi, let's meet on %day% at %time%! My clipboard says: %clipboard%'\n" +
                                                    "Expands to: 'Hi, let's meet on Monday at 14:30! My clipboard says: [copied text]'",
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Supported Template Placeholders:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                PlaceholderChip(tag = "%date%", desc = "Date (YYYY-MM-DD)")
                                                PlaceholderChip(tag = "%time%", desc = "Time (24h, HH:MM)")
                                                PlaceholderChip(tag = "%time12%", desc = "Time (12h, hh:mm AM/PM)")
                                            }
                                            Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                PlaceholderChip(tag = "%clipboard%", desc = "Clipboard content")
                                                PlaceholderChip(tag = "%day%", desc = "Day (e.g. Monday)")
                                                PlaceholderChip(tag = "%day_short%", desc = "Day short (e.g. Mon)")
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
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No shortcuts configured yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Tap the '+' floating action button below to add your first text template!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
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
                                    editingTemplate = template
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
                    editingTemplate = ""
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
                updated[editingShortcut.trim()] = editingTemplate
                shortcutsMap = updated
                TextExpanderUtils.saveShortcuts(context, updated)
                showAddDialog = false
            },
            checkOk = { editingShortcut.trim().isNotEmpty() && editingTemplate.isNotEmpty() },
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
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$prefix$shortcut",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = template,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_bin),
                    contentDescription = "Delete shortcut",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
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
