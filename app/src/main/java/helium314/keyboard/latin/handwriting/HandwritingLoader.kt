// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File

object HandwritingLoader {
    private const val PLUGIN_FILENAME = "handwriting_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.handwriting.plugin.HandwritingRecognizerImpl"
    private const val PREF_HAS_PLUGIN = "pref_handwriting_has_plugin"

    private var activeRecognizer: HandwritingRecognizer? = null

    fun getRecognizer(context: Context): HandwritingRecognizer? {
        if (activeRecognizer != null) return activeRecognizer
        if (!hasPlugin(context)) return null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return null
        }
        apkFile.setReadOnly()

        try {
            val md5 = java.security.MessageDigest.getInstance("MD5")
            val bytes = apkFile.readBytes()
            val hash = md5.digest(bytes).joinToString("") { "%02x".format(it) }
            Log.i("HandwritingLoader", "Loaded plugin APK path: ${apkFile.absolutePath}, size: ${bytes.size}, md5: $hash")
        } catch (e: Exception) {
            Log.e("HandwritingLoader", "Failed to calculate MD5", e)
        }

        try {
            val classLoader = DexClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val recognizer = clazz.getDeclaredConstructor().newInstance() as HandwritingRecognizer
            recognizer.init(context)
            activeRecognizer = recognizer
            return recognizer
        } catch (e: Exception) {
            Log.e("HandwritingLoader", "Failed to load handwriting plugin", e)
        }
        return null
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
            val recognizer = clazz.getDeclaredConstructor().newInstance() as HandwritingRecognizer
            recognizer.init(context)
            
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
            activeRecognizer = recognizer
            return true
        } catch (e: Exception) {
            Log.e("HandwritingLoader", "Failed to import plugin APK", e)
            // Cleanup on failure
            try {
                File(context.filesDir, PLUGIN_FILENAME).delete()
            } catch (_: Exception) {}
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            activeRecognizer = null
        }
        return false
    }

    fun removePlugin(context: Context) {
        try {
            File(context.filesDir, PLUGIN_FILENAME).delete()
        } catch (_: Exception) {}
        try {
            context.codeCacheDir.deleteRecursively()
        } catch (_: Exception) {}
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
        activeRecognizer = null
    }
}
