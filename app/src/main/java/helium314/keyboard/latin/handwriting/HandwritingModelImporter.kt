// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object HandwritingModelImporter {
    private const val TAG = "HandwritingModelImporter"

    data class ModelComponentsStatus(
        val hasModel: Boolean,
        val hasFst: Boolean,
        val hasRecospec: Boolean
    ) {
        val isComplete: Boolean get() = hasModel && hasFst
        val isReady: Boolean get() = hasModel
    }

    fun getComponentsStatus(context: Context, languageTag: String): ModelComponentsStatus {
        val baseDir = context.noBackupFilesDir ?: context.filesDir
        val baseLang = languageTag.substringBefore('-').lowercase()
        val normalizedTag = languageTag.replace('_', '-')
        val tagsToCheck = setOf(normalizedTag, baseLang, languageTag)

        var hasModel = false
        var hasFst = false
        var hasRecospec = false

        for (tag in tagsToCheck) {
            val dir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
            if (File(dir, "model.tflite").exists() && File(dir, "model.tflite").length() > 0) hasModel = true
            if (File(dir, "fst.compact").exists() && File(dir, "fst.compact").length() > 0) hasFst = true
            if (File(dir, "recospec").exists() && File(dir, "recospec").length() > 0) hasRecospec = true
        }

        return ModelComponentsStatus(hasModel, hasFst, hasRecospec)
    }

    fun importForLanguage(context: Context, languageTag: String, uri: Uri): Boolean {
        return importMultipleUrisForLanguage(context, languageTag, listOf(uri))
    }

    fun importMultipleUrisForLanguage(context: Context, languageTag: String, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        var anySuccess = false
        for (uri in uris) {
            try {
                val filename = getFilename(context, uri) ?: uri.lastPathSegment ?: ""
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val ok = importForLanguageFromStream(context, languageTag, stream, filename)
                    if (ok) anySuccess = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to import handwriting file for $languageTag from $uri", e)
            }
        }
        return anySuccess
    }

    private fun getFilename(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    fun importForLanguageFromStream(
        context: Context,
        languageTag: String,
        inputStream: InputStream,
        filenameHint: String
    ): Boolean {
        val baseDir = context.noBackupFilesDir ?: context.filesDir
        val baseLang = languageTag.substringBefore('-').lowercase()
        val normalizedTag = languageTag.replace('_', '-')
        val targetTags = setOf(normalizedTag, baseLang, languageTag)

        val tempExtractDir = File(context.cacheDir, "hw_import_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        return try {
            if (filenameHint.endsWith(".zip", ignoreCase = true)) {
                ZipInputStream(inputStream.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val lowerName = entry.name.lowercase()
                            val destName = when {
                                lowerName.contains("recospec") -> "recospec"
                                lowerName.contains("fst") || lowerName.endsWith(".compact") -> "fst.compact"
                                lowerName.endsWith(".tflite") || lowerName.contains("model") || lowerName.endsWith(".local") || lowerName.contains("lstm") -> "model.tflite"
                                else -> null
                            }
                            if (destName != null) {
                                val destFile = File(tempExtractDir, destName)
                                FileOutputStream(destFile).use { out ->
                                    zipIn.copyTo(out)
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } else {
                val lowerName = filenameHint.lowercase()
                val destName = when {
                    lowerName.contains("recospec") -> "recospec"
                    lowerName.contains("fst") || lowerName.endsWith(".compact") -> "fst.compact"
                    else -> "model.tflite"
                }
                val destFile = File(tempExtractDir, destName)
                FileOutputStream(destFile).use { out ->
                    inputStream.copyTo(out)
                }
            }

            val extractedFiles = tempExtractDir.listFiles()?.filter { it.length() > 0 } ?: emptyList()
            if (extractedFiles.isEmpty()) return false

            for (tag in targetTags) {
                val targetDir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
                targetDir.mkdirs()
                for (file in extractedFiles) {
                    val targetFile = File(targetDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                }
            }
            Log.i(TAG, "Successfully imported handwriting model files for $languageTag (files: ${extractedFiles.map { it.name }} -> $targetTags)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import handwriting model for $languageTag", e)
            false
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }
}
