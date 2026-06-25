// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import android.os.IBinder
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ConfirmationDialogContent
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

fun getDictionaryLocales(context: Context): MutableSet<Locale> {
    val locales = HashSet<Locale>()

    // ponytail: get cached dictionaries: extracted or user-added/downloaded dictionaries
    DictionaryInfoUtils.getCacheDirectories(context).forEach { directory ->
        if (!hasAnythingOtherThanExtractedMainDictionary(context, directory)) return@forEach
        val locale = DictionaryInfoUtils.getWordListIdFromFileName(directory.name).constructLocale()
        locales.add(locale)
    }
    // get assets dictionaries
    val assetsDictionaryList = DictionaryInfoUtils.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            locales.add(DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dictionary))
        }
    }
    // ponytail: include enabled locales and multilingual secondary locales
    val enabled = SubtypeSettings.getEnabledSubtypes()
    enabled.forEach { subtype ->
        locales.add(subtype.locale())
        getSecondaryLocales(subtype.extraValue).forEach { locales.add(it) }
    }
    return locales
}

@Composable
fun MissingDictionaryDialog(onDismissRequest: () -> Unit, locale: Locale, inline: Boolean = false) {
    val context = LocalContext.current
    val prefs = context.prefs()
    if (prefs.getBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, Defaults.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG)) {
        onDismissRequest()
        return
    }
    val availableDicts = createDictionaryTextAnnotated(locale)
    val repositoryLink = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL)
    val dictUrl = "${Links.DICTIONARY_URL}${Links.DICTIONARY_DOWNLOAD_SUFFIX}dictionaries/main_$locale.dict"
    val dictionaryLink = stringResource(R.string.dictionary_link_text).withHtmlLink(dictUrl)
    val message = stringResource(R.string.no_dictionary_message, repositoryLink, locale.toString(), dictionaryLink)
    var annotatedString = message.htmlToAnnotated()
    // ponytail: in standard flavor, if there are known dicts we show them as downloadable rows instead of bullet links
    val knownDicts = remember {
        if (helium314.keyboard.latin.BuildConfig.FLAVOR == "standard") {
            getKnownDictionariesForLocale(locale, context)
        } else emptyList()
    }
    if (availableDicts.isNotEmpty() && knownDicts.isEmpty())
        annotatedString += AnnotatedString("\n") + availableDicts

    if (inline) {
        ConfirmationDialogContent(
            onDismissRequest = onDismissRequest,
            cancelButtonText = stringResource(R.string.dialog_close),
            onConfirmed = { prefs.edit { putBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) } },
            confirmButtonText = stringResource(R.string.no_dictionary_dont_show_again_button),
            content = {
                androidx.compose.foundation.layout.Column {
                    Text(annotatedString)
                    if (knownDicts.isNotEmpty()) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        knownDicts.forEach { (desc, link) ->
                            DownloadableDictionaryRow(locale = locale, desc = desc, link = link, onRefresh = {})
                        }
                    }
                }
            }
        )
    } else {
        ConfirmationDialog(
            onDismissRequest = onDismissRequest,
            cancelButtonText = stringResource(R.string.dialog_close),
            onConfirmed = { prefs.edit { putBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) } },
            confirmButtonText = stringResource(R.string.no_dictionary_dont_show_again_button),
            content = {
                androidx.compose.foundation.layout.Column {
                    Text(annotatedString)
                    if (knownDicts.isNotEmpty()) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        knownDicts.forEach { (desc, link) ->
                            DownloadableDictionaryRow(locale = locale, desc = desc, link = link, onRefresh = {})
                        }
                    }
                }
            }
        )
    }
}

/** if dictionaries for [locale] or language are available returns links to them */
@Composable
fun createDictionaryTextAnnotated(locale: Locale): AnnotatedString {
    val context = LocalContext.current
    val knownDicts = getKnownDictionariesForLocale(locale, context)
    if (knownDicts.isEmpty()) return AnnotatedString("")
    val knownDictLinks = knownDicts.map { (name, link) ->
        "<li>${name.withHtmlLink(link)}</li>"
    }
    return "<ul>${knownDictLinks.joinToString("\n")}</ul>".htmlToAnnotated()
}

/** returns a pair of dictionary description and link for each dictionary  */
fun getKnownDictionariesForLocale(locale: Locale, context: Context): List<Pair<String, String>> {
    val knownDicts = mutableListOf<Pair<String, String>>()
    context.assets.open("dictionaries_in_dict_repo.csv").reader().forEachLine {
        if (it.isBlank()) return@forEachLine
        val (type, localeString, experimental) = it.split(",")
        // we use a locale string here because that's in the dictionaries repo
        // ideally the repo would switch to language tag, but not sure how this is handled in the dictionary header
        // further, the dicts in the dictionaries repo should be compatible with other AOSP-based keyboards
        val dictLocale = localeString.constructLocale()
        if (LocaleUtils.getMatchLevel(locale, dictLocale) < LocaleUtils.LOCALE_GOOD_MATCH) return@forEachLine
        val rawDictString = "$type: ${dictLocale.getDisplayName(context.resources.configuration.locale())}"
        val dictString = if (experimental != "exp") rawDictString
            else context.getString(R.string.available_dictionary_experimental, rawDictString)
        val dictLinkSuffix = when (experimental) {
            "cldr" -> Links.DICTIONARY_EMOJI_CLDR_SUFFIX
            "exp"  -> Links.DICTIONARY_EXPERIMENTAL_SUFFIX
            else   -> Links.DICTIONARY_NORMAL_SUFFIX
        }
        val dictBaseUrl = Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX + dictLinkSuffix
        val dictLink = dictBaseUrl + type + "_" + localeString.lowercase() + ".dict"
        knownDicts.add(dictString to dictLink)
    }
    return knownDicts
}

fun cleanUnusedMainDicts(context: Context) {
    val dictionaryDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context))
    val dirs = dictionaryDir.listFiles() ?: return
    val usedLocaleLanguageTags = hashSetOf<String>()
    SubtypeSettings.getEnabledSubtypes().forEach { subtype ->
        val locale = subtype.locale()
        usedLocaleLanguageTags.add(locale.toLanguageTag())
    }
    SubtypeSettings.getAdditionalSubtypes().forEach { subtype ->
        getSecondaryLocales(subtype.extraValue).forEach { usedLocaleLanguageTags.add(it.toLanguageTag()) }
    }
    for (dir in dirs) {
        if (!dir.isDirectory) continue
        if (dir.name in usedLocaleLanguageTags) continue
        if (hasAnythingOtherThanExtractedMainDictionary(context, dir))
            continue
        dir.deleteRecursively()
    }
}

// ponytail: check if the cached folder contains user-added or downloaded dicts (which shouldn't be automatically deleted or hidden)
private fun hasAnythingOtherThanExtractedMainDictionary(context: Context, dir: File): Boolean {
    val files = dir.listFiles() ?: return false
    if (files.isEmpty()) return false
    if (files.any { it.name != DictionaryInfoUtils.MAIN_DICT_FILE_NAME }) return true
    if (files.any { it.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME }) {
        val locale = DictionaryInfoUtils.getWordListIdFromFileName(dir.name).constructLocale()
        val assetsList = DictionaryInfoUtils.getAssetsDictionaryList(context) ?: return true
        val best = LocaleUtils.getBestMatch(locale, assetsList.toList()) {
            DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)
        }
        return best == null
    }
    return false
}

// ponytail: Dynamic dictionary downloader using HTTP URL connection.
fun downloadDictionary(context: Context, locale: Locale, type: String, linkUrl: String, onComplete: (Boolean) -> Unit) {
    val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context) ?: return onComplete(false)
    val targetFile = File(cacheDir, "${type}.dict")
    CoroutineScope(Dispatchers.IO).launch {
        var success = false
        try {
            java.net.URL(linkUrl).openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            success = true
        } catch (e: Exception) {
            Log.e("DictionaryUtils", "Failed to download dictionary", e)
        }
        withContext(Dispatchers.Main) {
            onComplete(success)
        }
    }
}

@Composable
fun DownloadableDictionaryRow(locale: Locale, desc: String, link: String, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val type = remember(link) { link.substringAfterLast("/").substringBefore("_") }
    val cacheDir = remember(locale) { DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx) }
    val file = remember(cacheDir, type) { cacheDir?.let { File(it, "$type.dict") } }
    var downloading by remember { mutableStateOf(false) }
    var exists by remember(file) { mutableStateOf(file?.exists() == true) }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (exists) {
            var showDeleteDialog by remember { mutableStateOf(false) }
            androidx.compose.material3.TextButton(onClick = { showDeleteDialog = true }) {
                Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
            }
            if (showDeleteDialog) {
                ConfirmationDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    confirmButtonText = stringResource(R.string.remove),
                    onConfirmed = {
                        file?.delete()
                        exists = false
                        onRefresh()
                    },
                    content = { Text(stringResource(R.string.remove_dictionary_message, type)) }
                )
            }
        } else if (downloading) {
            Text(
                stringResource(R.string.downloading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            androidx.compose.material3.TextButton(onClick = {
                downloading = true
                downloadDictionary(ctx, locale, type, link) { success ->
                    downloading = false
                    if (success) {
                        exists = true
                        onRefresh()
                    } else {
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.download_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(stringResource(R.string.download))
            }
        }
    }
}

// ponytail: check if the main dictionary is missing/not loaded for a given locale
fun isMainDictionaryMissing(context: Context, locale: Locale): Boolean {
    // 1. check if there's any dictionary in assets matching the locale
    val assetsList = DictionaryInfoUtils.getAssetsDictionaryList(context)
    if (assetsList != null) {
        val best = LocaleUtils.getBestMatch(locale, assetsList.toList()) {
            DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)
        }
        if (best != null) return false
    }
    // 2. check if cache directory has a main.dict file
    val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)?.let { File(it) }
    if (cacheDir?.exists() == true && cacheDir.isDirectory) {
        val hasMain = cacheDir.listFiles()?.any { it.name == "main.dict" } == true
        if (hasMain) return false
    }
    // 3. check if there is a known downloadable main dictionary for this locale
    val known = getKnownDictionariesForLocale(locale, context)
    return known.any { (_, link) -> link.substringAfterLast("/").substringBefore("_") == "main" }
}

// ponytail: helper to host ComposeView in non-Activity window context (e.g. IME Service)
private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

// ponytail: bridge compose dialog to legacy view
fun showMissingDictionaryComposeDialog(context: Context, locale: Locale, windowToken: IBinder, onDismiss: () -> Unit) {
    val dialog = android.app.Dialog(getPlatformDialogThemeContext(context))
    val lifecycleOwner = ServiceLifecycleOwner()
    val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(lifecycleOwner)
        setContent {
            Theme {
                MissingDictionaryDialog(
                    onDismissRequest = {
                        dialog.dismiss()
                        onDismiss()
                    },
                    locale = locale,
                    inline = true
                )
            }
        }
    }
    dialog.setOnDismissListener {
        lifecycleOwner.destroy()
    }
    dialog.setContentView(composeView)
    val window = dialog.window
    val layoutParams = window?.attributes
    layoutParams?.token = windowToken
    layoutParams?.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
    window?.attributes = layoutParams
    dialog.show()
}
