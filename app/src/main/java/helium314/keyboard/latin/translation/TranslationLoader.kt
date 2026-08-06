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
    private const val CURRENT_INTERFACE_VERSION = 1
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
            val classLoader = DexClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin version newer than supported interface!")
                return null
            }

            provider.init(context.applicationContext)
            activeProviderRef = WeakReference(provider)
            provider
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load translation plugin", e)
            null
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
            val classLoader = DexClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Incompatible plugin interface version")
                return false
            }

            provider.init(context.applicationContext)
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
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
    }
}
