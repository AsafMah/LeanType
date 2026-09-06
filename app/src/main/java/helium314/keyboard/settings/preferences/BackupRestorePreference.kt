// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.AppUpgrade
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import helium314.keyboard.settings.FeedbackManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

internal class BackupRestoreOperationQueue(private val worker: Executor, private val main: Executor) {
    private val pending = ArrayDeque<() -> Unit>()

    @Synchronized
    fun submit(work: () -> Unit, onComplete: () -> Unit = {}, onError: (Throwable) -> Unit) {
        pending.addLast {
            worker.execute {
                val result = runCatching(work)
                main.execute {
                    try {
                        result.getOrThrow()
                        onComplete()
                    } catch (t: Throwable) {
                        onError(t)
                    } finally {
                        next()
                    }
                }
            }
        }
        if (pending.size == 1) pending.first().invoke()
    }

    @Synchronized
    private fun next() {
        pending.removeFirst()
        pending.firstOrNull()?.invoke()
    }
}

// Operations keep this handle; disposal clears the callbacks that capture UI state.
internal class BackupRestoreCallbacks(
    private var onError: ((String) -> Unit)?,
    private var onSuccess: (() -> Unit)?
) : LifecycleEventObserver {
    fun error(message: String) = onError?.invoke(message)
    fun success() = onSuccess?.invoke()
    fun detach() {
        onError = null
        onSuccess = null
    }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) detach()
    }
}

@Composable
private fun rememberBackupRestoreCallbacks(onError: (String) -> Unit): BackupRestoreCallbacks {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentError by rememberUpdatedState(onError)
    val callbacks = remember(ctx, lifecycle) {
        BackupRestoreCallbacks({ currentError(it) }, {
            (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        })
    }
    DisposableEffect(callbacks, lifecycle) {
        lifecycle.addObserver(callbacks)
        onDispose {
            lifecycle.removeObserver(callbacks)
            callbacks.detach()
        }
    }
    return callbacks
}

private fun reportBackupRestoreError(ctx: Context, callbacks: BackupRestoreCallbacks, prefix: String, error: Throwable) {
    val message = error.message ?: error.javaClass.simpleName
    callbacks.error(prefix + message)
    FeedbackManager.message(ctx, ctx.getString(
        if (prefix == "b") R.string.backup_error else R.string.restore_error, message
    ))
    Log.w("AdvancedScreen", "error during backup/restore", error)
}

private val backupRestoreOperations by lazy {
    BackupRestoreOperationQueue(
        Executors.newSingleThreadExecutor { Thread(it, "BackupRestore") },
        Executor { Handler(Looper.getMainLooper()).post(it) }
    )
}

@Composable
fun BackupRestorePreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    var selectedCategories by remember {
        mutableStateOf(
            setOf(
                BackupCategory.LAYOUTS,
                BackupCategory.THEME_APPEARANCE,
                BackupCategory.DICTIONARY_HISTORY,
                BackupCategory.CLIPBOARD,
                BackupCategory.GENERAL_SETTINGS
            )
        )
    }
    val backupLauncher = backupLauncher(selectedCategories) { error = it }
    val restoreLauncher = restoreLauncher(selectedCategories) { error = it }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.backup_select_items),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val categories = listOf(
                        BackupCategory.LAYOUTS to R.string.backup_category_layouts,
                        BackupCategory.THEME_APPEARANCE to R.string.backup_category_theme,
                        BackupCategory.DICTIONARY_HISTORY to R.string.backup_category_dictionary,
                        BackupCategory.CLIPBOARD to R.string.backup_category_clipboard,
                        BackupCategory.GENERAL_SETTINGS to R.string.backup_category_general
                    )
                    categories.forEach { (category, stringResId) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .toggleable(
                                    value = selectedCategories.contains(category),
                                    onValueChange = { checked ->
                                        selectedCategories = if (checked) {
                                            selectedCategories + category
                                        } else {
                                            selectedCategories - category
                                        }
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedCategories.contains(category),
                                onCheckedChange = null
                            )
                            Text(
                                text = stringResource(stringResId),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.backup_restore_message))
                }
            },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                showDialog = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
                    )
                    .setType("application/zip")
                backupLauncher.launch(intent)
            }
        )
    }
    if (error != null) {
        InfoDialog(
            if (error!!.startsWith("b"))
                stringResource(R.string.backup_error, error!!.drop(1))
            else stringResource(R.string.restore_error, error!!.drop(1))
        ) { error = null }
    }
}

@Composable
private fun backupLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current.applicationContext
    val callbacks = rememberBackupRestoreCallbacks(onError)
    return filePicker { uri ->
        val categories = selectedCategories.toSet()
        backupRestoreOperations.submit(work = {
            backupData(ctx, categories) { ctx.contentResolver.openOutputStream(uri) }
        }, onError = { t ->
            reportBackupRestoreError(ctx, callbacks, "b", t)
        })
    }
}

internal fun backupData(ctx: Context, categories: Set<BackupCategory>, openOutput: () -> OutputStream?) {
    val output = openOutput() ?: throw IOException("Could not open backup document")
    ZipOutputStream(output).use { zip ->
        fun writeFile(file: File, name: String) {
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        listOf(ctx.filesDir to "", DeviceProtectedUtils.getFilesDir(ctx) to "unprotected/").forEach { (base, prefix) ->
            base.walk().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(base).invariantSeparatorsPath
                if (backupFilePatterns.any { name.matches(it) } && getCategoryForFilePath(name) in categories) {
                    writeFile(file, prefix + name)
                }
            }
        }
        if (BackupCategory.CLIPBOARD in categories) {
            ctx.getDatabasePath(Database.NAME).takeIf { it.exists() }?.let { writeFile(it, Database.NAME) }
        }
        backupPreferences(ctx).forEach { (name, prefs) ->
            val category = getCategoryForFilePath(name)
            if (category == null || category in categories) {
                zip.putNextEntry(ZipEntry(name))
                settingsToJsonStream(prefs.all.filter { it.key?.let(::getCategoryForPrefKey) in categories }, zip)
                zip.closeEntry()
            }
        }
    }
}

@Composable
private fun restoreLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current.applicationContext
    val callbacks = rememberBackupRestoreCallbacks(onError)
    return filePicker { uri ->
        val categories = selectedCategories.toSet()
        backupRestoreOperations.submit(work = {
            restoreBackupData(ctx, categories) { ctx.contentResolver.openInputStream(uri) }
        }, onComplete = {
            completeRestore(ctx)
            FeedbackManager.message(ctx, R.string.backup_restored)
            callbacks.success()
        }, onError = { t ->
            reportBackupRestoreError(ctx, callbacks, "r", t)
        })
    }
}

internal fun restoreBackupData(
    ctx: Context,
    selectedCategories: Set<BackupCategory>,
    openInput: () -> InputStream?
) {
    val staging = File(ctx.cacheDir, "restore-${UUID.randomUUID()}")
    check(staging.mkdirs()) { "Could not create restore staging directory" }
    try {
        val archive = File(staging, "backup.zip")
        (openInput() ?: throw IOException("Could not open backup document")).use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        val preferences = backupPreferences(ctx)
        val stagedPreferences = linkedMapOf<String, Map<String, Any>>()
        val stagedFiles = mutableListOf<Pair<File, File>>()
        val seen = hashSetOf<String>()
        var stagedDatabase: File? = null
        var hasBackupContent = false
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = validatedEntryName(entry)
                check(seen.add(name)) { "Duplicate backup entry: $name" }
                if (entry.isDirectory && name == "unprotected/") continue
                val unprotected = name.startsWith("unprotected/")
                val relative = if (unprotected) name.removePrefix("unprotected/") else name
                val base = if (unprotected) DeviceProtectedUtils.getFilesDir(ctx) else ctx.filesDir
                val target = checkedRestoreTarget(base, relative)
                val category = getCategoryForFilePath(relative)
                if ((!unprotected && (name in preferences || name == Database.NAME)) ||
                    (category != null && (entry.isDirectory || backupFilePatterns.any { relative.matches(it) }))) {
                    hasBackupContent = true
                }
                if (!entry.isDirectory && !unprotected && name in preferences) {
                    val values = readBackupEntry(zip, entry) { input ->
                        input.bufferedReader().use { parseBackupPreferences(it.readLines()) }
                    }
                    if (category == null || category in selectedCategories) stagedPreferences[name] = values
                } else if (!entry.isDirectory && !unprotected && name == Database.NAME) {
                    val file = File(staging, "clipboard.db")
                    readBackupEntry(zip, entry) { input -> file.outputStream().use { input.copyTo(it) } }
                    validateClipboardDatabase(file)
                    if (BackupCategory.CLIPBOARD in selectedCategories) stagedDatabase = file
                } else if (category in selectedCategories &&
                    (entry.isDirectory || backupFilePatterns.any { relative.matches(it) })) {
                    val file = checkedRestoreTarget(File(staging, "files"), name)
                    if (entry.isDirectory) {
                        check(file.mkdirs() || file.isDirectory) { "Could not stage directory: $name" }
                    } else {
                        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                        readBackupEntry(zip, entry) { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    stagedFiles.add(file to target)
                }
            }
            require(hasBackupContent) { "Archive contains no backup data" }
        }
        // Every entry and preferences section has been validated before touching live user data.
        Settings.getInstance().stopListener()
        try {
            deleteSelectedBackupData(ctx, selectedCategories)
            stagedFiles.forEach { (source, target) ->
                if (source.isDirectory) {
                    check(target.mkdirs() || target.isDirectory) { "Could not restore directory: ${target.name}" }
                } else {
                    source.copyTo(target, overwrite = true)
                }
            }
            stagedPreferences.forEach { (name, values) ->
                applyBackupPreferences(preferences.getValue(name), values, selectedCategories)
            }
            stagedDatabase?.let { file ->
                val restoredDb = ctx.getDatabasePath(Database.NAME + "_restored")
                try {
                    file.copyTo(restoredDb, overwrite = true)
                    Database.copyFromDb(restoredDb, ctx)
                } finally {
                    restoredDb.delete()
                }
            }
            LayoutUtilsCustom.onLayoutFileChanged()
            AppUpgrade.checkVersionUpgrade(ctx)
            AppUpgrade.transferOldPinnedClips(ctx)
        } finally {
            Settings.getInstance().startListener()
        }
    } finally {
        staging.deleteRecursively()
    }
}

internal fun completeRestore(ctx: Context) {
    Settings.getInstance().stopListener()
    try {
        SubtypeSettings.reloadEnabledSubtypes(ctx)
        LayoutUtilsCustom.onLayoutFileChanged()
        LayoutUtilsCustom.removeMissingLayouts(ctx)
        Settings.clearCachedBackgroundImages()
        Settings.clearCachedTypeface()
        SupportedEmojis.load(ctx)
        Settings.getInstance().reloadSettings()
        ctx.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION).setPackage(ctx.packageName))
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    } finally {
        Settings.getInstance().startListener()
    }
}

private fun backupPreferences(ctx: Context) = mapOf(
    PREFS_FILE_NAME to ctx.prefs(),
    PROTECTED_PREFS_FILE_NAME to ctx.protectedPrefs(),
) + auxiliaryPrefsToBackUp(ctx)

private fun <T> readBackupEntry(zip: ZipFile, entry: ZipEntry, read: (InputStream) -> T): T {
    val checksum = CRC32()
    return CheckedInputStream(zip.getInputStream(entry), checksum).use {
        val result = read(it)
        require(checksum.value == entry.crc) { "Corrupt backup entry: ${entry.name}" }
        result
    }
}

private fun validatedEntryName(entry: ZipEntry): String {
    val name = entry.name.replace('\\', '/')
    val parts = name.removeSuffix("/").split('/')
    require(name.isNotEmpty() && !name.startsWith('/') && ':' !in name && '\u0000' !in name &&
        parts.none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe backup entry: ${entry.name}" }
    return name
}

private fun checkedRestoreTarget(base: File, name: String): File {
    val target = File(base, name)
    require(target.canonicalPath.startsWith(base.canonicalPath + File.separator)) {
        "Unsafe backup entry: $name"
    }
    return target
}

private fun validateClipboardDatabase(file: File) {
    android.database.sqlite.SQLiteDatabase.openDatabase(
        file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
    ).use { db ->
        db.rawQuery("PRAGMA quick_check", null).use {
            require(it.moveToFirst() && it.getString(0) == "ok") { "Invalid clipboard database" }
        }
        db.rawQuery("SELECT TIMESTAMP, PINNED, TEXT FROM CLIPBOARD LIMIT 0", null).close()
    }
}

private fun deleteSelectedBackupData(ctx: Context, categories: Set<BackupCategory>) {
    val files = mutableListOf<File>()
    listOf(ctx.filesDir, DeviceProtectedUtils.getFilesDir(ctx)).distinct().forEach { base ->
        base.listFiles().orEmpty().forEach { file ->
            val category = when {
                file.name == "layouts" -> BackupCategory.LAYOUTS
                file.name in setOf("dicts", "blacklists") || file.name.startsWith("UserHistoryDictionary") ->
                    BackupCategory.DICTIONARY_HISTORY
                file.name in setOf("custom_font", "custom_emoji_font") || file.name.startsWith("custom_background_image") ->
                    BackupCategory.THEME_APPEARANCE
                else -> null
            }
            if (category in categories) files.add(checkedRestoreTarget(base, file.name))
        }
    }
    files.forEach { root ->
        root.walkBottomUp().forEach { file ->
            check(file.delete() || !file.exists()) { "Could not replace ${file.path}" }
        }
    }
    if (BackupCategory.CLIPBOARD in categories) {
        ClipboardDao.closeInstance()
        Database.closeInstance()
        val database = ctx.getDatabasePath(Database.NAME)
        check(!database.exists() || ctx.deleteDatabase(Database.NAME)) { "Could not replace clipboard database" }
    }
}

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
internal fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

private fun parseBackupPreferences(lines: List<String>): Map<String, Any> {
    val values = linkedMapOf<String, Any>()
    val headers = hashSetOf<String>()
    val iterator = lines.dropLastWhile { it.isBlank() }.iterator()
    while (iterator.hasNext()) {
        val header = iterator.next()
        require(headers.add(header) && iterator.hasNext()) { "Invalid preferences section: $header" }
        val json = iterator.next()
        val section: Map<String, Any> = when (header) {
            "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(json)
            "int settings" -> Json.decodeFromString<Map<String, Int>>(json)
            "long settings" -> Json.decodeFromString<Map<String, Long>>(json)
            "float settings" -> Json.decodeFromString<Map<String, Float>>(json)
            "string settings" -> Json.decodeFromString<Map<String, String>>(json)
            "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(json)
            else -> throw IllegalArgumentException("Unknown preferences section: $header")
        }
        section.forEach { (key, value) ->
            require(!values.containsKey(key)) { "Duplicate preference: $key" }
            values[key] = value
        }
    }
    require(headers.size == 6) { "Incomplete backup preferences" }
    return values
}

private fun applyBackupPreferences(prefs: SharedPreferences, values: Map<String, Any>, categories: Set<BackupCategory>) {
    val editor = prefs.edit()
    prefs.all.keys.filter { getCategoryForPrefKey(it) in categories }.forEach { editor.remove(it) }
    values.filter { getCategoryForPrefKey(it.key) in categories }.forEach { (key, value) ->
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
        }
    }
    check(editor.commit()) { "Could not save restored preferences" }
}

/**
 * Auxiliary SharedPreferences files (other than the main prefs and protectedPrefs) that
 * should be included in backups. The key is the zip entry name to use, and the value
 * is the SharedPreferences instance to read from / write back into on restore.
 *
 * NOTE: This must NOT include EncryptedSharedPreferences (e.g. "gemini_prefs"), because
 * those values are encrypted with a device-bound master key and would be unreadable on
 * any other device. Plus they typically hold credentials, which we don't want in a plain
 * backup zip.
 */
private fun auxiliaryPrefsToBackUp(ctx: android.content.Context): Map<String, SharedPreferences> =
    mapOf(
        FLOATING_KEYBOARD_PREFS_FILE_NAME
            to DeviceProtectedUtils.getSharedPreferences(ctx, "floating_keyboard_prefs"),
    )

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
private const val FLOATING_KEYBOARD_PREFS_FILE_NAME = "floating_keyboard_preferences.json"

private val backupFilePatterns by lazy { listOf(
    "blacklists/.*\\.txt".toRegex(),
    "layouts/.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
    "dicts/.*/.*user\\.dict".toRegex(),
    "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
    "custom_background_image.*".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
) }

enum class BackupCategory {
    LAYOUTS,
    THEME_APPEARANCE,
    DICTIONARY_HISTORY,
    CLIPBOARD,
    GENERAL_SETTINGS
}

private fun getCategoryForPrefKey(key: String): BackupCategory {
    if (key.startsWith("layout_")) return BackupCategory.LAYOUTS
    
    val themeKeys = setOf(
        "theme_style", "icon_style", "theme_colors", "theme_colors_night",
        "theme_key_borders", "theme_auto_day_night", "custom_icon_names",
        "navbar_color", "font_scale", "emoji_font_scale", "narrow_key_gaps",
        "narrow_key_gaps_level", "emoji_key_fit", "emoji_skin_tone", "space_bar_text"
    )
    if (themeKeys.contains(key) 
        || key.startsWith("user_colors_") 
        || key.startsWith("user_all_colors_")
        || key.startsWith("user_more_colors_")
        || key.startsWith("keyboard_height_scale")
        || key.startsWith("bottom_padding_scale")
        || key.startsWith("side_padding_scale")
        || key.startsWith("split_spacer_scale")
    ) {
        return BackupCategory.THEME_APPEARANCE
    }
    
    val dictKeys = setOf(
        "use_personalized_dicts", "block_potentially_offensive", "next_word_prediction", "first_word_prediction",
        "suggest_emojis", "inline_emoji_search", "show_emoji_descriptions",
        "auto_correction", "more_auto_correction", "auto_correct_threshold",
        "autocorrect_shortcuts", "backspace_reverts_autocorrect", "suggest_punctuation",
        "add_to_personal_dictionary"
    )
    if (dictKeys.contains(key) || key.startsWith("pref_text_expander_")) return BackupCategory.DICTIONARY_HISTORY
    
    val clipboardKeys = setOf(
        "enable_clipboard_history", "suggest_screenshots", "compress_screenshots",
        "clipboard_history_retention_time", "clipboard_history_pinned_first",
        "clipboard_fold_pinned", "clear_clipboard_icon"
    )
    if (clipboardKeys.contains(key)) return BackupCategory.CLIPBOARD
    
    return BackupCategory.GENERAL_SETTINGS
}

private fun getCategoryForFilePath(path: String): BackupCategory? {
    if (path.startsWith("layouts${File.separator}") || path.contains("layouts/")) {
        return BackupCategory.LAYOUTS
    }
    if (path.startsWith("custom_background_image") || path == "custom_font" || path == "custom_emoji_font" || path == FLOATING_KEYBOARD_PREFS_FILE_NAME) {
        return BackupCategory.THEME_APPEARANCE
    }
    if (path.startsWith("dicts${File.separator}") || path.startsWith("dicts/")
        || path.startsWith("blacklists${File.separator}") || path.startsWith("blacklists/")
        || path.startsWith("UserHistoryDictionary")
    ) {
        return BackupCategory.DICTIONARY_HISTORY
    }
    if (path == Database.NAME) {
        return BackupCategory.CLIPBOARD
    }
    return null
}
