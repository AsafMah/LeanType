// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.AddonPolicy
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.UUID

object OcrPluginLoader {
    private const val CURRENT_INTERFACE_VERSION = 1
    private const val PLUGIN_FILENAME = "ocr_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.ocr.plugin.TextRecognizerImpl"
    private const val PREF_HAS_PLUGIN = "pref_ocr_has_plugin"
    const val PREF_OCR_KEEP_LINE_BREAKS = "pref_ocr_keep_line_breaks"
    const val PREF_OCR_TRIM_WHITESPACE = "pref_ocr_trim_whitespace"
    const val PREF_OCR_CASING = "pref_ocr_casing"
    const val PREF_OCR_LINE_JOIN_FORMAT = "pref_ocr_line_join_format"
    const val PREF_OCR_DEHYPHENATE = "pref_ocr_dehyphenate"
    const val PREF_OCR_NORMALIZE_PUNCTUATION = "pref_ocr_normalize_punctuation"
    const val PREF_OCR_STRIP_BULLETS = "pref_ocr_strip_bullets"
    const val PREF_OCR_REMOVE_NOISE = "pref_ocr_remove_noise"
    const val PREF_OCR_AUTO_COPY = "pref_ocr_auto_copy"
    const val PREF_OCR_AUTO_INSERT = "pref_ocr_auto_insert"
    const val PREF_OCR_SUGGEST_SCREENSHOT_TEXT = "pref_ocr_suggest_screenshot_text"
    const val PREF_OCR_PERSIST_FLASH = "pref_ocr_persist_flash"
    private const val TAG = "OcrPluginLoader"

    private var activeRecognizer: ITextRecognizer? = null
    private var cachedClassLoader: PluginClassLoader? = null
    private var cachedApkModified: Long = 0L
    private val runtimeLock = Any()

    internal var importValidator: (Context, File) -> Boolean = ::validateImport
    internal var moveImportedFile: (File, File) -> Boolean = { source, target ->
        source.renameTo(target)
    }

    @JvmStatic
    fun resetRecognizer(): Unit = synchronized(runtimeLock) {
        val recognizer = activeRecognizer
        activeRecognizer = null
        try {
            recognizer?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to release OCR recognizer", e)
        }
    }

    private fun invalidateClassLoader(): Unit = synchronized(runtimeLock) {
        resetRecognizer()
        cachedClassLoader = null
        cachedApkModified = 0L
    }

    @JvmStatic
    fun getTargetAbi(): String {
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "armeabi-v7a" -> return "armeabi-v7a"
                "x86_64" -> return "x86_64"
                "x86" -> return "x86"
            }
        }
        return "arm64-v8a"
    }

    @JvmStatic
    fun getPluginDownloadUrl(tag: String? = null): String {
        val abi = getTargetAbi()
        val filename = "ocr_plugin-$abi.apk"
        return if (tag == null || tag == "latest") {
            "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases/latest/download/$filename"
        } else {
            "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases/download/$tag/$filename"
        }
    }

    @JvmStatic
    fun downloadPluginApk(context: Context, tag: String? = null, tempFile: File): Boolean {
        if (!AddonPolicy.allowsInAppDownloads() || !AddonPolicy.allowsOcrPlugins()) {
            Log.w(TAG, "OCR plugin download is disabled for this build")
            return false
        }
        val urlsToTry = listOf(
            getPluginDownloadUrl(tag),
            if (tag == null || tag == "latest") {
                "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases/latest/download/ocr_plugin.apk"
            } else {
                "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases/download/$tag/ocr_plugin.apk"
            }
        ).distinct()

        for (urlStr in urlsToTry) {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "HeliboardL")
                conn.connect()

                var redirectConn = conn
                var status = redirectConn.responseCode
                var redirectCount = 0
                while ((status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || status == java.net.HttpURLConnection.HTTP_MOVED_PERM || status == java.net.HttpURLConnection.HTTP_SEE_OTHER) && redirectCount < 5) {
                    val newUrl = redirectConn.getHeaderField("Location")
                    redirectConn.disconnect()
                    val nextUrl = java.net.URL(newUrl)
                    redirectConn = nextUrl.openConnection() as java.net.HttpURLConnection
                    redirectConn.setRequestProperty("User-Agent", "HeliboardL")
                    redirectConn.connect()
                    status = redirectConn.responseCode
                    redirectCount++
                }

                if (status == java.net.HttpURLConnection.HTTP_OK) {
                    redirectConn.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    redirectConn.disconnect()
                    return true
                }
                redirectConn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download from $urlStr", e)
            }
        }
        return false
    }

    private fun getNativeLibDir(context: Context, apkFile: File): File {
        val baseDir = File(context.filesDir, "plugin_libs")
        if (!baseDir.exists()) baseDir.mkdirs()
        val targetName = "ocr_${apkFile.lastModified()}"
        val targetDir = File(baseDir, targetName)
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name.startsWith("ocr_") || f.name == "ocr") && f.name != targetName) {
                try {
                    f.deleteRecursively()
                } catch (_: Exception) {}
            }
        }
        return targetDir
    }

    private fun extractNativeLibs(apkFile: File, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()
        try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val abis = android.os.Build.SUPPORTED_ABIS
                var targetAbi: String? = null
                for (abi in abis) {
                    if (zip.entries().asSequence().any { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }) {
                        targetAbi = abi
                        break
                    }
                }
                if (targetAbi != null) {
                    val prefix = "lib/$targetAbi/"
                    for (entry in zip.entries().asSequence()) {
                        if (entry.name.startsWith(prefix) && entry.name.endsWith(".so")) {
                            val fileName = entry.name.substring(prefix.length)
                            val outFile = File(outputDir, fileName)
                            if (!outFile.exists() || outFile.length() != entry.size) {
                                zip.getInputStream(entry).use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                outFile.setReadable(true, false)
                                outFile.setExecutable(true, false)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract native libraries", e)
        }
    }

    private fun ensureWorkManagerInitialized(context: Context) {
        try {
            androidx.work.WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            try {
                androidx.work.WorkManager.initialize(
                    context.applicationContext,
                    (context.applicationContext as? androidx.work.Configuration.Provider)?.workManagerConfiguration
                        ?: androidx.work.Configuration.Builder().build()
                )
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun getRecognizer(context: Context): ITextRecognizer? = synchronized(runtimeLock) {
        if (!AddonPolicy.allowsOcrPlugins()) return@synchronized null
        if (activeRecognizer != null) return@synchronized activeRecognizer
        if (!hasPlugin(context)) return@synchronized null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return@synchronized null
        }
        apkFile.setReadOnly()

        loadRecognizerInternal(context, apkFile)
    }

    private fun loadRecognizerInternal(context: Context, apkFile: File): ITextRecognizer? {
        return try {
            ensureWorkManagerInitialized(context)
            val apkLastModified = apkFile.lastModified()
            val nativeLibDir = getNativeLibDir(context, apkFile)
            extractNativeLibs(apkFile, nativeLibDir)

            val classLoader = if (cachedClassLoader != null && cachedApkModified == apkLastModified) {
                cachedClassLoader!!
            } else {
                PluginClassLoader(
                    apkFile.absolutePath,
                    context.codeCacheDir.absolutePath,
                    nativeLibDir.absolutePath,
                    context.classLoader
                ).also {
                    cachedClassLoader = it
                    cachedApkModified = apkLastModified
                }
            }

            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val recognizer = clazz.getDeclaredConstructor().newInstance() as ITextRecognizer

            if (recognizer.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin interface version is newer than supported")
                return null
            }

            val pluginContext = PluginContext(context.applicationContext, apkFile.absolutePath, classLoader)
            recognizer.init(pluginContext)

            if (recognizer.isAvailable()) {
                activeRecognizer = recognizer
                Log.i(TAG, "OCR recognizer loaded successfully (${recognizer.getScriptName()})")
                recognizer
            } else {
                Log.w(TAG, "OCR recognizer is not available after initialization")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load OCR plugin", e)
            null
        }
    }

    fun hasPlugin(context: Context): Boolean = synchronized(runtimeLock) {
        if (!AddonPolicy.allowsOcrPlugins()) return@synchronized false
        val has = context.prefs().getBoolean(PREF_HAS_PLUGIN, false)
        if (!has) return@synchronized false
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        apkFile.exists() && apkFile.length() > 0
    }

    fun getPluginVersion(context: Context): String? {
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) return null
        return try {
            val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            info?.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun getActiveScriptName(context: Context): String? {
        val recognizer = getRecognizer(context)
        return recognizer?.getDisplayName() ?: recognizer?.getScriptName()
    }

    @Synchronized
    fun importPlugin(context: Context, uri: Uri): Boolean {
        if (!AddonPolicy.allowsOcrPlugins()) {
            Log.w(TAG, "OCR plugin import is disabled for this build")
            return false
        }
        return importFromSource(context) { candidate ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                candidate.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Cannot open OCR plugin")
        }
    }

    @Synchronized
    fun importPluginFromTempFile(context: Context, tempFile: File): Boolean {
        if (!AddonPolicy.allowsOcrPlugins()) {
            Log.w(TAG, "OCR plugin import is disabled for this build")
            return false
        }
        val success = importFromSource(context) { candidate -> tempFile.copyTo(candidate) }
        if (success) {
            // A caller may pass the installed APK itself; never delete the committed destination.
            try {
                if (tempFile.canonicalFile != File(context.filesDir, PLUGIN_FILENAME).canonicalFile) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove imported OCR source file", e)
            }
        }
        return success
    }

    private fun importFromSource(context: Context, copy: (File) -> Unit): Boolean {
        val target = File(context.filesDir, PLUGIN_FILENAME)
        val staging = File(context.filesDir, "ocr_import_${UUID.randomUUID()}")
        val candidate = File(staging, "candidate.apk")
        val backup = File(staging, "previous.apk")
        val preferences = context.prefs()
        val wasInstalled = preferences.getBoolean(PREF_HAS_PLUGIN, false)
        var replaced = false
        var committed = false
        return try {
            check(staging.mkdirs()) { "Cannot create OCR import staging directory" }
            copy(candidate)
            check(candidate.isFile && candidate.length() > 0 && candidate.setReadOnly()) {
                "Cannot prepare OCR plugin for validation"
            }
            check(importValidator(context, candidate)) { "OCR plugin verification failed" }

            // Staging/validation never holds the runtime lock needed by the installed recognizer.
            synchronized(runtimeLock) {
                try {
                    if (target.exists()) {
                        check(target.isFile && moveImportedFile(target, backup)) { "Cannot back up OCR plugin" }
                    }
                    check(moveImportedFile(candidate, target)) { "Cannot commit OCR plugin" }
                    replaced = true
                    check(preferences.edit().putBoolean(PREF_HAS_PLUGIN, true).commit()) {
                        "Cannot save OCR plugin installation state"
                    }
                    committed = true
                    invalidateClassLoader()
                } catch (e: Throwable) {
                    if (backup.exists()) {
                        if (target.exists()) {
                            target.setWritable(true)
                            target.delete()
                        }
                        if (!backup.renameTo(target)) {
                            Log.e(TAG, "Could not restore OCR plugin; backup retained at ${backup.path}")
                        }
                    } else if (replaced) {
                        target.setWritable(true)
                        target.delete()
                    }
                    if (replaced) preferences.edit().putBoolean(PREF_HAS_PLUGIN, wasInstalled).commit()
                    throw e
                }
            }
            Log.i(TAG, "OCR plugin imported and verified successfully")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import OCR plugin", e)
            false
        } finally {
            // If restoration itself fails, retain the only surviving old APK for recovery.
            if (committed || !backup.exists()) {
                try {
                    staging.walkBottomUp().forEach { it.setWritable(true); it.delete() }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove OCR staging files", e)
                }
            }
        }
    }

    private fun validateImport(context: Context, apk: File): Boolean {
        var recognizer: ITextRecognizer? = null
        return try {
            ensureWorkManagerInitialized(context)
            val libraries = File(apk.parentFile, "lib").apply { mkdirs() }
            val dexCache = File(apk.parentFile, "dex").apply { mkdirs() }
            extractNativeLibs(apk, libraries)
            val loader = PluginClassLoader(
                apk.absolutePath, dexCache.absolutePath, libraries.absolutePath, context.classLoader
            )
            recognizer = loader.loadClass(PLUGIN_CLASS_NAME).getDeclaredConstructor()
                .newInstance() as ITextRecognizer
            if (recognizer.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) return false
            recognizer.init(PluginContext(context.applicationContext, apk.absolutePath, loader))
            recognizer.isAvailable()
        } catch (e: Throwable) {
            Log.w(TAG, "OCR plugin candidate validation failed", e)
            false
        } finally {
            try {
                recognizer?.release()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to release OCR validation recognizer", e)
            }
        }
    }

    @Synchronized
    fun removePlugin(context: Context): Unit = synchronized(runtimeLock) {
        try {
            invalidateClassLoader()
            val apkFile = File(context.filesDir, PLUGIN_FILENAME)
            if (apkFile.exists()) apkFile.delete()
            val baseDir = File(context.filesDir, "plugin_libs")
            baseDir.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name.startsWith("ocr_") || f.name == "ocr")) {
                    try { f.deleteRecursively() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
    }

    @JvmStatic
    fun release() {
        resetRecognizer()
    }

    private class PluginContext(
        base: Context,
        private val apkPath: String,
        private val pluginClassLoader: ClassLoader
    ) : android.content.ContextWrapper(base), androidx.work.Configuration.Provider {
        private val pluginResources: android.content.res.Resources by lazy {
            try {
                val assetManager = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPathMethod = android.content.res.AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPathMethod.invoke(assetManager, apkPath)
                android.content.res.Resources(assetManager, base.resources.displayMetrics, base.resources.configuration)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create plugin resources", e)
                base.resources
            }
        }

        override fun getResources(): android.content.res.Resources = pluginResources

        override fun getAssets(): android.content.res.AssetManager = pluginResources.assets

        override fun getClassLoader(): ClassLoader = pluginClassLoader

        override fun getApplicationContext(): Context = this

        override val workManagerConfiguration: androidx.work.Configuration
            get() = (baseContext.applicationContext as? androidx.work.Configuration.Provider)?.workManagerConfiguration
                ?: androidx.work.Configuration.Builder().build()
    }

    private class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String?,
        private val librarySearchPath: String?,
        parent: ClassLoader
    ) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {
        override fun findLibrary(name: String): String? {
            if (librarySearchPath != null) {
                val filename = System.mapLibraryName(name)
                val file = java.io.File(librarySearchPath, filename)
                if (file.exists()) {
                    return file.absolutePath
                }
            }
            return super.findLibrary(name)
        }

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("helium314.keyboard.ocr.plugin.") ||
                name.startsWith("com.google.mlkit.") ||
                name.startsWith("com.google.android.datatransport.") ||
                name.startsWith("com.google.android.gms.") ||
                name.startsWith("com.google.firebase.")
            ) {
                val loaded = findLoadedClass(name)
                if (loaded != null) return loaded
                try {
                    return findClass(name)
                } catch (_: ClassNotFoundException) {
                    // fallback to parent
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}
