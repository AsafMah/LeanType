// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.withHtmlLink
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.DictionaryDialog
import helium314.keyboard.settings.dictionaryFilePicker
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.Locale

@Composable
fun DictionaryScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val enabledLanguages = SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language }
    val cachedDictFolders = DictionaryInfoUtils.getCacheDirectories(ctx).map { it.name }
    val comparer = compareBy<Locale>({ it.language !in enabledLanguages }, { it.toLanguageTag() !in cachedDictFolders }, { it.displayName })
    val dictionaryLocales = listOf(Locale(SubtypeLocaleUtils.NO_LANGUAGE)) + getDictionaryLocales(ctx)
        .filter { it.language != SubtypeLocaleUtils.NO_LANGUAGE }
        .sortedWith(comparer)
    var selectedLocale: Locale? by remember { mutableStateOf(null) }
    var showAddDictDialog by remember { mutableStateOf(false) }
    val dictPicker = dictionaryFilePicker(selectedLocale)

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.dictionary_settings_category)) },
        filteredItems = { term ->
            if (term.isBlank()) dictionaryLocales
            else dictionaryLocales.filter { loc ->
                    loc.language != SubtypeLocaleUtils.NO_LANGUAGE
                            && loc.localizedDisplayName(ctx.resources).replace("(", "")
                                .splitOnWhitespace().any { it.startsWith(term, true) }
                }
        },
        itemContent = { locale ->
            if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) {
                // Card for general actions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Add Dictionary Entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddDictDialog = true }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_plus),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                                )
                                Text(
                                    stringResource(R.string.add_new_dictionary_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            NextScreenIcon()
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Personal Dictionary Entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SettingsDestination.navigateTo(SettingsDestination.PersonalDictionaries) }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_dictionary),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                                )
                                Text(
                                    stringResource(R.string.edit_personal_dictionary),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            NextScreenIcon()
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Blocked Words Entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SettingsDestination.navigateTo(SettingsDestination.BlockedWords) }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bin),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                                )
                                Text(
                                    stringResource(R.string.edit_blocked_words),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            NextScreenIcon()
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Dictionary Source Entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(helium314.keyboard.latin.common.Links.DICTIONARY_URL))
                                    ctx.startActivity(intent)
                                }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_about_github),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                                )
                                Column {
                                    Text(
                                        stringResource(R.string.dictionary_source_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.dictionary_source_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            NextScreenIcon()
                        }
                    }
                }

                // Card for Personal Dictionary Switch Setting
                val prefs = ctx.prefs()
                var personalDictEnabled by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY)) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .clickable {
                                    val newValue = !personalDictEnabled
                                    personalDictEnabled = newValue
                                    ctx.prefs().edit { putBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, newValue) }
                                }
                                .padding(all = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    stringResource(R.string.add_to_personal_dictionary),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.add_to_personal_dictionary_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = personalDictEnabled,
                                onCheckedChange = { 
                                    personalDictEnabled = it
                                    ctx.prefs().edit { putBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, it) } 
                                }
                            )
                        }
                        if (personalDictEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SliderPreference(
                                name = stringResource(R.string.add_to_personal_dict_threshold),
                                key = Settings.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD,
                                default = Defaults.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD,
                                range = 1f..5f,
                                stepSize = 1,
                                description = {
                                    if (it == 1) {
                                        stringResource(R.string.add_to_personal_dict_threshold_times_1)
                                    } else {
                                        stringResource(R.string.add_to_personal_dict_threshold_times_many, it)
                                    }
                                }
                            )
                        }
                    }
                }

                // Add a "Languages" Section Header
                Text(
                    text = stringResource(R.string.language_and_layouts_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                )
            } else {
                // Premium Language Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { selectedLocale = locale },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = locale.localizedDisplayName(LocalResources.current),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val (dicts, hasInternal) = getUserAndInternalDictionaries(ctx, locale)
                            val mainDictLabel = stringResource(R.string.main_dictionary)
                            val internalDictLabel = stringResource(R.string.internal_dictionary_summary)
                            val types = dicts.mapTo(mutableListOf()) { file ->
                                if (file.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME) {
                                    mainDictLabel
                                } else {
                                    file.name.substringBefore("_${DictionaryInfoUtils.USER_DICTIONARY_SUFFIX}")
                                }
                            }
                            if (hasInternal && !types.contains(Dictionary.TYPE_MAIN) && !types.contains(mainDictLabel))
                                types.add(0, internalDictLabel)
                            
                            // Render active dictionaries as stylized badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (types.isEmpty()) {
                                    Text(
                                        text = "No active dictionaries",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                } else {
                                    types.forEach { type ->
                                        val badgeColor = when (type.lowercase()) {
                                            "main", internalDictLabel.lowercase(), mainDictLabel.lowercase() -> MaterialTheme.colorScheme.primaryContainer
                                            "user" -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                        val badgeTextColor = when (type.lowercase()) {
                                            "main", internalDictLabel.lowercase(), mainDictLabel.lowercase() -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "user" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = type,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = badgeTextColor,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        NextScreenIcon()
                    }
                }
            }
        }
    )
    if (showAddDictDialog) {
        ConfirmationDialog(
            onDismissRequest = { showAddDictDialog = false },
            onConfirmed = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream")
                dictPicker.launch(intent)
            },
            confirmButtonText = stringResource(R.string.load_gesture_library_button_load),
            title = { Text(stringResource(R.string.add_new_dictionary_title)) },
            content = {
                val link = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL)
                val addDictString = stringResource(R.string.add_dictionary, link)
                Text(addDictString.htmlToAnnotated())
            }
        )
    }
    if (selectedLocale != null) {
        DictionaryDialog(
            onDismissRequest = { selectedLocale = null },
            locale = selectedLocale!!
        )
    }
}

fun getUserAndInternalDictionaries(context: Context, locale: Locale): Pair<List<File>, Boolean> {
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false

    val candidateDirs = mutableListOf<File>()
    DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)?.let { candidateDirs.add(File(it)) }
    if (locale.country.isNotEmpty() || locale.variant.isNotEmpty()) {
        val fallbackLocale = Locale(locale.language)
        DictionaryInfoUtils.getCacheDirectoryForLocale(fallbackLocale, context)?.let { candidateDirs.add(File(it)) }
    }
    DictionaryInfoUtils.getFallbackVariantDirectory(locale, context)?.let { candidateDirs.add(it) }

    val internalDicts = DictionaryInfoUtils.getAssetsDictionaryList(context)
    val best = internalDicts?.let {
        LocaleUtils.getBestMatch(locale, it.toList()) { dict ->
            DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dict)
        }
    }
    val hasAsset = best != null

    val seenFiles = mutableSetOf<String>()
    candidateDirs.filter { it.exists() && it.isDirectory }.forEach { dir ->
        dir.listFiles()?.forEach { file ->
            if (seenFiles.add(file.name)) {
                if (file.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)) {
                    userDicts.add(file)
                } else if (file.name.startsWith(DictionaryInfoUtils.MAIN_DICT_PREFIX)) {
                    hasInternalDict = true
                } else if (file.name.endsWith(".dict")) {
                    if (file.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME) {
                        if (!hasAsset) {
                            userDicts.add(file)
                        } else {
                            hasInternalDict = true
                        }
                    } else if (file.name == "emoji.dict") {
                        val hasEmojiAsset = internalDicts?.any { asset -> asset.startsWith("emoji") } == true
                        if (!hasEmojiAsset) {
                            userDicts.add(file)
                        } else {
                            hasInternalDict = true
                        }
                    } else {
                        userDicts.add(file)
                    }
                }
            }
        }
    }

    return userDicts to (hasInternalDict || hasAsset)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            DictionaryScreen { }
        }
    }
}
