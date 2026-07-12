// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

    // ponytail: migrate legacy incorrectly-named dictionary folders (gb, au, ca) to en-GB, en-AU, en-CA
    val dictDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context))
    if (dictDir.exists() && dictDir.isDirectory) {
        val legacyMap = mapOf("gb" to "en-GB", "au" to "en-AU", "ca" to "en-CA")
        legacyMap.forEach { (legacy, correct) ->
            val legacyFolder = File(dictDir, legacy)
            if (legacyFolder.exists() && legacyFolder.isDirectory) {
                val correctFolder = File(dictDir, correct)
                if (!correctFolder.exists()) {
                    legacyFolder.renameTo(correctFolder)
                } else {
                    legacyFolder.deleteRecursively()
                }
            }
        }
    }

    // ponytail: include enabled locales and multilingual secondary locales
    val enabled = SubtypeSettings.getEnabledSubtypes(true)
    val enabledLocales = HashSet<Locale>()
    enabled.forEach { subtype ->
        enabledLocales.add(subtype.locale())
        getSecondaryLocales(subtype.extraValue).forEach { enabledLocales.add(it) }
    }
    android.util.Log.i("DictionaryUtils", "getDictionaryLocales: enabledLocales=$enabledLocales")

    // ponytail: get cached dictionaries: extracted or user-added/downloaded dictionaries
    DictionaryInfoUtils.getCacheDirectories(context).forEach { directory ->
        if (!hasAnythingOtherThanExtractedMainDictionary(context, directory)) return@forEach
        val locale = DictionaryInfoUtils.getWordListIdFromFileName(directory.name).constructLocale()
        val isEnabled = enabledLocales.contains(locale)
        val hasEnabledLanguage = enabledLocales.any { it.language == locale.language }
        android.util.Log.i("DictionaryUtils", "Cache loop: locale=$locale, isEnabled=$isEnabled, hasEnabledLanguage=$hasEnabledLanguage")
        if (!isEnabled && hasEnabledLanguage) return@forEach
        locales.add(locale)
    }
    // get assets dictionaries
    val assetsDictionaryList = DictionaryInfoUtils.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            val locale = DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dictionary)
            val hasEnabledLanguage = enabledLocales.any { it.language == locale.language }
            // ponytail: only show assets for enabled languages to avoid showing preloaded en-US when not used
            if (!hasEnabledLanguage) continue
            val isEnabled = enabledLocales.contains(locale)
            if (!isEnabled && hasEnabledLanguage) continue
            locales.add(locale)
        }
    }
    locales.addAll(enabledLocales)
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
    val message = stringResource(R.string.no_dictionary_message, repositoryLink)
    var annotatedString = message.htmlToAnnotated()
    // ponytail: in standard flavor, if there are known dicts we show them as downloadable rows instead of bullet links
    val knownDicts = remember {
        if (helium314.keyboard.latin.BuildConfig.FLAVOR == "standard" || helium314.keyboard.latin.BuildConfig.FLAVOR == "standardfull") {
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

// ponytail: Dynamic dictionary downloader using HTTP URL connection with User-Agent, redirects, and timeouts.
fun downloadDictionary(context: Context, locale: Locale, type: String, linkUrl: String, onComplete: (Boolean) -> Unit) {
    val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context) ?: return onComplete(false)
    val targetFile = File(cacheDir, "${type}.dict")
    CoroutineScope(Dispatchers.IO).launch {
        var success = false
        try {
            var url = java.net.URL(linkUrl)
            var connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "HeliboardL/3.8.9 (Android)")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true

            var status = connection.responseCode
            var conn = connection
            var redirectCount = 0
            while ((status == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308) && redirectCount < 5) {
                val newUrl = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                val nextUrl = java.net.URL(newUrl)
                conn = nextUrl.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "HeliboardL/3.8.9 (Android)")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                status = conn.responseCode
                redirectCount++
            }

            if (status == java.net.HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                success = true
            } else {
                Log.e("DictionaryUtils", "HTTP error downloading dictionary: $status")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("DictionaryUtils", "Failed to download dictionary", e)
        }
        withContext(Dispatchers.Main) {
            onComplete(success)
        }
    }
}

@Composable
fun DownloadableDictionaryRow(locale: Locale, desc: String, link: String, refreshTrigger: Int = 0, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val type = remember(link) { link.substringAfterLast("/").substringBefore("_") }
    // ponytail: extract the specific dictionary locale from the download link to avoid directory collision
    val dictLocale = remember(link) {
        val fileName = link.substringAfterLast("/")
        fileName.substringAfter("_").substringBefore(".dict").constructLocale()
    }
    val cacheDir = remember(dictLocale) { DictionaryInfoUtils.getCacheDirectoryForLocale(dictLocale, ctx) }
    val file = remember(cacheDir, type) { cacheDir?.let { File(it, "$type.dict") } }
    var downloading by remember { mutableStateOf(false) }
    val downloadedLink = remember(link, refreshTrigger) { ctx.prefs().getString("pref_dict_download_link_${type}_${dictLocale}", "") ?: "" }
    var exists by remember(file, downloadedLink, refreshTrigger) {
        mutableStateOf(
            file?.exists() == true && (downloadedLink == link || (downloadedLink.isEmpty() && link.contains("/dictionaries/")))
        )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (exists) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✓ " + stringResource(R.string.installed),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                helium314.keyboard.settings.DeleteButton(
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                ) {
                    file?.delete()
                    ctx.prefs().edit().remove("pref_dict_download_link_${type}_${dictLocale}").apply()
                    exists = false
                    onRefresh()
                }
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
                downloadDictionary(ctx, dictLocale, type, link) { success ->
                    downloading = false
                    if (success) {
                        ctx.prefs().edit().putString("pref_dict_download_link_${type}_${dictLocale}", link).apply()
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
    // 2. check if cache directory has a main.dict or main_user.dict file
    var cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)?.let { File(it) }
    var hasMain = cacheDir?.exists() == true && cacheDir.isDirectory && cacheDir.listFiles()?.any { it.name.startsWith("main") && it.name.endsWith(".dict") } == true
    if (!hasMain && (locale.country.isNotEmpty() || locale.variant.isNotEmpty())) {
        val fallbackLocale = Locale(locale.language)
        cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(fallbackLocale, context)?.let { File(it) }
        hasMain = cacheDir?.exists() == true && cacheDir.isDirectory && cacheDir.listFiles()?.any { it.name.startsWith("main") && it.name.endsWith(".dict") } == true
        if (!hasMain) {
            val variantDir = DictionaryInfoUtils.getFallbackVariantDirectory(locale, context)
            hasMain = variantDir?.exists() == true && variantDir.isDirectory && variantDir.listFiles()?.any { it.name.startsWith("main") && it.name.endsWith(".dict") } == true
        }
    }
    if (hasMain) return false
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
