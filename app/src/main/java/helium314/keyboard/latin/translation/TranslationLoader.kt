// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.lang.ref.WeakReference

object TranslationLoader {
    private const val CURRENT_INTERFACE_VERSION = 2
    private const val PLUGIN_FILENAME = "translation_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.translation.plugin.TranslationProviderImpl"
    private const val PREF_HAS_PLUGIN = "pref_translation_has_plugin"
    private const val TAG = "TranslationLoader"

    private var activeProviderRef: WeakReference<ITranslationProvider>? = null

    fun getProvider(context: Context): ITranslationProvider? {
        val cached = activeProviderRef?.get()
        if (cached != null) return cached
        if (!hasPlugin(context)) return null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return null
        }
        apkFile.setReadOnly()

        return try {
            val nativeLibDir = File(context.filesDir, "plugin_libs/translation")
            extractNativeLibs(apkFile, nativeLibDir)
            val classLoader = PluginClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin version newer than supported interface!")
                return null
            }

            val pluginContext = PluginContext(context.applicationContext, apkFile.absolutePath)
            provider.init(pluginContext)
            activeProviderRef = WeakReference(provider)
            provider
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load translation plugin", e)
            null
        }
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

    fun hasPlugin(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_HAS_PLUGIN, false)
    }

    fun getPluginVersion(context: Context): String? {
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) return null
        return try {
            val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            info?.versionName
        } catch (e: Exception) {
            null
        }
    }

    fun importPlugin(context: Context, uri: Uri): Boolean {
        try {
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}

            val apkFile = File(context.filesDir, PLUGIN_FILENAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            apkFile.setReadOnly()

            // Verify the plugin loads successfully
            val nativeLibDir = File(context.filesDir, "plugin_libs/translation")
            extractNativeLibs(apkFile, nativeLibDir)
            val classLoader = PluginClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Incompatible plugin interface version")
                return false
            }

            val pluginContext = PluginContext(context.applicationContext, apkFile.absolutePath)
            provider.init(pluginContext)
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
            activeProviderRef = WeakReference(provider)
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import translation plugin APK", e)
            try {
                File(context.filesDir, PLUGIN_FILENAME).delete()
            } catch (_: Exception) {}
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}
            try {
                File(context.filesDir, "plugin_libs/translation").deleteRecursively()
            } catch (_: Exception) {}
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            activeProviderRef = null
        }
        return false
    }

    fun unloadPlugin() {
        try {
            activeProviderRef?.get()?.cleanup()
        } catch (e: Throwable) {
            Log.e(TAG, "Error during plugin cleanup", e)
        }
        activeProviderRef = null
    }

    fun removePlugin(context: Context) {
        unloadPlugin()
        try {
            File(context.filesDir, PLUGIN_FILENAME).delete()
        } catch (_: Exception) {}
        try {
            context.codeCacheDir.deleteRecursively()
        } catch (_: Exception) {}
        try {
            File(context.filesDir, "plugin_libs/translation").deleteRecursively()
        } catch (_: Exception) {}
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
    }

    private class PluginContext(base: Context, private val apkPath: String) : android.content.ContextWrapper(base) {
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

        override fun getApplicationContext(): Context = this
    }

    private class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String?,
        librarySearchPath: String?,
        parent: ClassLoader
    ) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("helium314.keyboard.translation.plugin.") ||
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
