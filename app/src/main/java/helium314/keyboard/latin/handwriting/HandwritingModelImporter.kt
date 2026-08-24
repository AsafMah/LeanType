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

    fun importForLanguage(context: Context, languageTag: String, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importForLanguageFromStream(context, languageTag, stream, uri.lastPathSegment ?: "")
            } ?: false
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import handwriting model for $languageTag from $uri", e)
            false
        }
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
                                lowerName.endsWith(".tflite") || lowerName.contains("model") || lowerName.endsWith(".local") -> "model.tflite"
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
                val destFile = File(tempExtractDir, "model.tflite")
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
            Log.i(TAG, "Successfully imported handwriting model for $languageTag (files: ${extractedFiles.map { it.name }} -> $targetTags)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import handwriting model for $languageTag", e)
            false
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }
}
