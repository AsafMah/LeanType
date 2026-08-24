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
        val targetDir = File(baseDir, "com.google.mlkit.models/$languageTag/DIGITAL_INK/0")
        targetDir.mkdirs()
        val targetModelFile = File(targetDir, "model.tflite")

        return try {
            if (filenameHint.endsWith(".zip", ignoreCase = true)) {
                // Extract model from zip
                var extracted = false
                ZipInputStream(inputStream.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.contains("model.tflite") || entry.name.endsWith(".local") || entry.name.endsWith(".tflite"))) {
                            FileOutputStream(targetModelFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            extracted = true
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                extracted
            } else {
                // Direct .tflite copy
                FileOutputStream(targetModelFile).use { out ->
                    inputStream.copyTo(out)
                }
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract handwriting model for $languageTag", e)
            false
        }
    }
}
