// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.createDictionaryTextAnnotated
import helium314.keyboard.latin.utils.DownloadableDictionaryRow
import helium314.keyboard.settings.DeleteButton
import helium314.keyboard.settings.ExpandButton
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dictionaryFilePicker
import helium314.keyboard.settings.previewDark
import helium314.keyboard.settings.screens.getUserAndInternalDictionaries
import java.io.File
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DictionaryDialog(
    onDismissRequest: () -> Unit,
    locale: Locale,
) {
    val ctx = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    val (dictionaries, hasInternal) = remember(refreshTrigger) { getUserAndInternalDictionaries(ctx, locale) }
    val mainDict = dictionaries.firstOrNull {
        it.name == Dictionary.TYPE_MAIN + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX
                || it.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME
    }
    val addonDicts = dictionaries.filterNot { it == mainDict }
    val picker = dictionaryFilePicker(locale)
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = stringResource(R.string.dialog_close),
        title = { Text(locale.localizedDisplayName(LocalResources.current)) },
        content = {
            Column {
                val internalDicts = DictionaryInfoUtils.getAssetsDictionaryList(ctx)
                val best = internalDicts?.let {
                    LocaleUtils.getBestMatch(locale, it.toList()) { dict ->
                        DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dict)
                    }
                }
                // ponytail: normalize key to match format used by DictionaryFactory (lowercase, replace - with _)
                val internalId = best?.let { "main:" + it.substringAfter("_").substringBefore(".").lowercase().replace("-", "_") }
                val mainPrefKey = "pref_dict_enabled_" + (internalId ?: "main:${locale.toLanguageTag().lowercase().replace("-", "_")}")

                val prefs = ctx.prefs()
                var enabled by remember { mutableStateOf(prefs.getBoolean(mainPrefKey, true)) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { isChecked ->
                            enabled = isChecked
                            prefs.edit().putBoolean(mainPrefKey, isChecked).apply()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.main_dictionary),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (mainDict != null) {
                    DictionaryDetails(mainDict) { refreshTrigger++ }
                }
                if (addonDicts.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.dictionary_category_title),
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    addonDicts.forEach { DictionaryDetails(it) { refreshTrigger++ } }
                }
                val knownDicts = remember {
                    if (helium314.keyboard.latin.BuildConfig.FLAVOR == "standard" || helium314.keyboard.latin.BuildConfig.FLAVOR == "standardfull") {
                        helium314.keyboard.latin.utils.getKnownDictionariesForLocale(locale, ctx)
                    } else emptyList()
                }
                if (knownDicts.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.dictionary_available),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    knownDicts.forEach { (desc, link) ->
                        DownloadableDictionaryRow(locale, desc, link, refreshTrigger) {
                            refreshTrigger++
                        }
                    }
                } else {
                    val dictString = createDictionaryTextAnnotated(locale)
                    if (dictString.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.dictionary_available),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(dictString, style = LocalTextStyle.current.merge(lineHeight = 1.8.em))
                    }
                }
            }
        },
        scrollContent = true,
        neutralButtonText = stringResource(R.string.add_new_dictionary_title),
        onNeutral = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
            picker.launch(intent)
        }
    )
}

@Composable
private fun DictionaryDetails(dict: File, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dict) ?: return
    val type = header.mIdString.substringBefore(":")
    val prefs = ctx.prefs()
    val prefKey = "pref_dict_enabled_${header.mIdString}"
    var enabled by remember { mutableStateOf(prefs.getBoolean(prefKey, true)) }
    var showDetails by remember { mutableStateOf(false) }
    val title = when (type) {
        DictionaryInfoUtils.DEFAULT_MAIN_DICT -> stringResource(R.string.main_dictionary)
        Dictionary.TYPE_EMOJI -> stringResource(R.string.subtype_emoji)
        else -> type
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = { isChecked ->
                enabled = isChecked
                prefs.edit().putBoolean(prefKey, isChecked).apply()
            },
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        DeleteButton {
            dict.delete()
            dict.parentFile?.name?.constructLocale()?.let { dictLocale ->
                ctx.prefs().edit().remove("pref_dict_download_link_${type}_${dictLocale}").apply()
            }
            onDelete()
        }
        ExpandButton { showDetails = !showDetails }
    }
    AnimatedVisibility(showDetails, enter = fadeIn(), exit = fadeOut()) {
        Text(
            header.info(LocalConfiguration.current.locale()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 12.dp)
        )
    }

}


@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        DictionaryDialog({}, Locale.ENGLISH)
    }
}
