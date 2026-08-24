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

        val tempFile = File.createTempFile("hw_import", ".tmp", context.cacheDir)
        return try {
            if (filenameHint.endsWith(".zip", ignoreCase = true)) {
                var extracted = false
                ZipInputStream(inputStream.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.contains("model.tflite") || entry.name.endsWith(".local") || entry.name.endsWith(".tflite"))) {
                            FileOutputStream(tempFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            extracted = true
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                if (!extracted) return false
            } else {
                FileOutputStream(tempFile).use { out ->
                    inputStream.copyTo(out)
                }
            }

            if (tempFile.length() == 0L) return false

            for (tag in targetTags) {
                val targetDir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
                targetDir.mkdirs()
                val targetModelFile = File(targetDir, "model.tflite")
                tempFile.copyTo(targetModelFile, overwrite = true)
            }
            Log.i(TAG, "Successfully imported handwriting model for $languageTag (installed to $targetTags)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import handwriting model for $languageTag", e)
            false
        } finally {
            tempFile.delete()
        }
    }
}
