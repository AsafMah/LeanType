// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object TranslationModelImporter {
    private const val TAG = "TranslationModelImporter"

    fun migrateLegacyModels(context: Context) {
        try {
            val baseDir = context.noBackupFilesDir ?: context.filesDir
            val modelsDir = File(baseDir, "com.google.mlkit.translate.models")
            if (!modelsDir.exists() || !modelsDir.isDirectory) return

            modelsDir.listFiles()?.forEach { modelDir ->
                if (modelDir.isDirectory) {
                    val versionZeroDir = File(modelDir, "0")
                    if (versionZeroDir.exists() && versionZeroDir.isDirectory) {
                        versionZeroDir.listFiles()?.forEach { file ->
                            val dest = File(modelDir, file.name)
                            if (dest.exists()) dest.delete()
                            file.renameTo(dest)
                        }
                        versionZeroDir.deleteRecursively()
                        Log.i(TAG, "Restored files from $versionZeroDir to $modelDir")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error cleaning legacy translation model folders", e)
        }
    }

    fun importFromUri(context: Context, uri: Uri): String? {
        migrateLegacyModels(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importFromStream(context, stream)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import translation model from URI: $uri", e)
            null
        }
    }

    fun importFromStream(context: Context, inputStream: InputStream): String? {
        migrateLegacyModels(context)
        val tempZip = File(context.cacheDir, "import_translation_model_${System.currentTimeMillis()}.zip")
        return try {
            FileOutputStream(tempZip).use { out ->
                inputStream.copyTo(out)
            }

            var detectedModelName: String? = null

            // Inspect zip entries to detect model name (e.g. dict.en_es_25 or merged_dict_en_es_25...)
            java.util.zip.ZipFile(tempZip).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    val match = Regex("""(?:dict\.|merged_dict_)([a-z]{2,3}_[a-z]{2,3})""").find(name)
                    if (match != null) {
                        detectedModelName = match.groupValues[1]
                        break
                    }
                }
            }

            if (detectedModelName == null) {
                Log.e(TAG, "Could not detect translation model language pair from zip contents")
                return null
            }

            val modelName = detectedModelName!!
            val baseDir = context.noBackupFilesDir ?: context.filesDir
            val targetDir = File(baseDir, "com.google.mlkit.translate.models/$modelName")
            targetDir.mkdirs()

            ZipInputStream(tempZip.inputStream().buffered()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val relPath = if (entryName.contains("/")) entryName.substringAfter("/") else entryName
                    if (relPath.isNotEmpty()) {
                        val outFile = File(targetDir, relPath)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Log.i(TAG, "Successfully imported translation model $modelName into $targetDir")
            modelName
        } catch (e: Throwable) {
            Log.e(TAG, "Error extracting translation model zip", e)
            null
        } finally {
            tempZip.delete()
        }
    }
}
