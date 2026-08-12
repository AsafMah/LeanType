// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.voice.offline.model

import android.content.Context
import android.os.ParcelFileDescriptor
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun getModelState(engineType: String): ModelState {
        val targetDir = File(modelsDir, engineType)
        return if (targetDir.exists() && isModelDirectoryValid(engineType, targetDir)) {
            ModelState(engineType, ModelState.STATE_READY, "Model loaded")
        } else {
            ModelState(engineType, ModelState.STATE_MISSING, "Model not imported")
        }
    }

    fun isModelReady(engineType: String): Boolean {
        val targetDir = File(modelsDir, engineType)
        return targetDir.exists() && isModelDirectoryValid(engineType, targetDir)
    }

    fun getModelDir(engineType: String): File {
        return File(modelsDir, engineType)
    }

    fun importModelSafely(request: ModelImportRequest): Boolean {
        val targetEngine = request.engineType
        val tmpZip = File(modelsDir, "${targetEngine}_${System.currentTimeMillis()}.tmp")

        try {
            ParcelFileDescriptor.AutoCloseInputStream(request.file).use { input ->
                FileOutputStream(tmpZip).use { output ->
                    input.copyTo(output)
                }
            }

            val sha256 = request.sha256
            if (sha256 != null && !verifySha256(tmpZip, sha256)) {
                tmpZip.delete()
                return false
            }

            return if (targetEngine == VoiceConstants.ENGINE_VOSK) {
                extractVoskModel(tmpZip, modelsDir)
            } else {
                val finalFile = File(modelsDir, targetEngine)
                finalFile.deleteRecursively()
                tmpZip.renameTo(finalFile)
            }
        } catch (e: Exception) {
            tmpZip.delete()
            return false
        }
    }

    fun extractVoskModel(tmpZip: File, targetDir: File): Boolean {
        val extractTmp = File(targetDir, "extract_tmp_${System.currentTimeMillis()}")
        try {
            extractTmp.mkdirs()
            safeUnzip(tmpZip, extractTmp)
            val root = findVoskRoot(extractTmp) ?: return false
            val finalDir = File(targetDir, VoiceConstants.ENGINE_VOSK)
            finalDir.deleteRecursively()

            if (!root.renameTo(finalDir)) {
                root.copyRecursively(finalDir, overwrite = true)
                root.deleteRecursively()
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            extractTmp.deleteRecursively()
            tmpZip.delete()
        }
    }

    fun deleteModel(engineType: String): Boolean {
        val targetDir = File(modelsDir, engineType)
        return targetDir.deleteRecursively()
    }

    private fun safeUnzip(zipFile: File, targetDir: File) {
        val canonicalDestDirPath = targetDir.canonicalPath
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                val canonicalNewFilePath = newFile.canonicalPath

                if (!canonicalNewFilePath.startsWith(canonicalDestDirPath + File.separator) &&
                    canonicalNewFilePath != canonicalDestDirPath
                ) {
                    throw SecurityException("Zip entry is outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findVoskRoot(dir: File): File? {
        if (dir.isDirectory && dir.listFiles()?.any { it.name == "am" || it.name == "conf" } == true) {
            return dir
        }
        val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (subdir in subdirs) {
            val root = findVoskRoot(subdir)
            if (root != null) return root
        }
        return null
    }

    private fun isModelDirectoryValid(engineType: String, dir: File): Boolean {
        return if (engineType == VoiceConstants.ENGINE_VOSK) {
            dir.isDirectory && (dir.listFiles()?.any { it.name == "am" || it.name == "conf" } == true)
        } else {
            dir.exists() && dir.length() > 0
        }
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            hexString.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
