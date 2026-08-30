// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.net.Uri
import android.util.Log
import helium314.keyboard.latin.utils.Log as KLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object SoundPackImporter {
    private const val TAG = "SoundPackImporter"
    private const val SOUND_PACKS_DIR_NAME = "sound_packs"

    data class PackFiles(
        val standardFile: File?,
        val spaceFile: File?,
        val deleteFile: File?,
        val enterFile: File?
    ) {
        val isValid: Boolean get() = standardFile?.exists() == true || spaceFile?.exists() == true || deleteFile?.exists() == true || enterFile?.exists() == true
    }

    fun getSoundPacksDir(context: Context): File {
        val baseDir = context.noBackupFilesDir ?: context.filesDir
        val dir = File(baseDir, SOUND_PACKS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPackDir(context: Context, packId: String): File {
        val cleanId = packId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(getSoundPacksDir(context), cleanId)
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) return true
        val packDir = getPackDir(context, packId)
        if (!packDir.exists() || !packDir.isDirectory) return false
        val files = getPackAudioFiles(context, packId)
        return files.isValid
    }

    fun getPackAudioFiles(context: Context, packId: String): PackFiles {
        val packDir = getPackDir(context, packId)
        if (!packDir.exists() || !packDir.isDirectory) {
            return PackFiles(null, null, null, null)
        }

        val allFiles = packDir.listFiles()?.filter { file ->
            val ext = file.extension.lowercase()
            ext in listOf("ogg", "wav", "mp3")
        } ?: emptyList()

        fun findFile(prefixes: List<String>): File? {
            return allFiles.firstOrNull { file ->
                val name = file.nameWithoutExtension.lowercase()
                prefixes.any { name == it || name.startsWith("${it}_") || name.startsWith("${it}-") }
            }
        }

        val standard = findFile(listOf("standard", "click", "default", "key", "press", "tap"))
            ?: allFiles.firstOrNull()
        val space = findFile(listOf("space", "spacebar")) ?: standard
        val delete = findFile(listOf("delete", "backspace", "del")) ?: standard
        val enter = findFile(listOf("enter", "return")) ?: standard

        return PackFiles(standard, space, delete, enter)
    }

    fun getInstalledCustomPacks(context: Context): List<SoundPackInfo> {
        val packsDir = getSoundPacksDir(context)
        val dirs = packsDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        val list = mutableListOf<SoundPackInfo>()

        for (dir in dirs) {
            val id = dir.name
            if (SoundPackUrls.isPreset(id)) continue
            val audioFiles = getPackAudioFiles(context, id)
            if (audioFiles.isValid) {
                val displayNameFile = File(dir, "name.txt")
                val displayName = if (displayNameFile.exists()) {
                    try { displayNameFile.readText().trim() } catch (_: Throwable) { id }
                } else {
                    id.replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                list.add(
                    SoundPackInfo(
                        id = id,
                        displayName = displayName,
                        description = "Custom imported sound pack",
                        isPreset = false,
                        isCustom = true
                    )
                )
            }
        }
        return list.sortedBy { it.displayName }
    }

    fun importFromUri(context: Context, uri: Uri, customName: String? = null): String? {
        val filename = getFilename(context, uri) ?: uri.lastPathSegment ?: "custom_sound"
        val ext = filename.substringAfterLast(".", "").lowercase()
        val rawId = filename.substringBeforeLast(".").replace("[^a-zA-Z0-9_-]".toRegex(), "_").lowercase()
        val packId = "custom_${rawId}_${System.currentTimeMillis() % 10000}"
        val displayName = customName?.takeIf { it.isNotBlank() } ?: filename.substringBeforeLast(".")

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (ext == "zip") {
                    val ok = importFromStream(context, stream, packId, displayName)
                    if (ok) packId else null
                } else if (ext in listOf("ogg", "wav", "mp3")) {
                    val packDir = getPackDir(context, packId)
                    packDir.mkdirs()
                    val targetFile = File(packDir, "standard.$ext")
                    FileOutputStream(targetFile).use { out -> stream.copyTo(out) }
                    File(packDir, "name.txt").writeText(displayName)
                    CustomSoundManager.getInstance(context).reloadIfActive(packId)
                    packId
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import sound from URI $uri", e)
            null
        }
    }

    fun importFromStream(
        context: Context,
        inputStream: InputStream,
        packId: String,
        displayName: String
    ): Boolean {
        val packDir = getPackDir(context, packId)
        val tempDir = File(context.cacheDir, "temp_sound_pack_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        return try {
            ZipInputStream(inputStream.buffered()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val simpleName = if (entryName.contains("/")) entryName.substringAfterLast("/") else entryName
                    val ext = simpleName.substringAfterLast(".", "").lowercase()
                    if (simpleName.isNotEmpty() && !entry.isDirectory && ext in listOf("ogg", "wav", "mp3", "txt")) {
                        val outFile = File(tempDir, simpleName)
                        FileOutputStream(outFile).use { out -> zipIn.copyTo(out) }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val validFiles = tempDir.listFiles()?.filter {
                it.extension.lowercase() in listOf("ogg", "wav", "mp3")
            } ?: emptyList()

            if (validFiles.isEmpty()) {
                Log.e(TAG, "No valid audio files found in zip for pack $packId")
                return false
            }

            packDir.deleteRecursively()
            packDir.mkdirs()
            tempDir.listFiles()?.forEach { file ->
                file.copyTo(File(packDir, file.name), overwrite = true)
            }
            File(packDir, "name.txt").writeText(displayName)

            CustomSoundManager.getInstance(context).reloadIfActive(packId)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract sound pack $packId", e)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun deletePack(context: Context, packId: String): Boolean {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) return false
        val packDir = getPackDir(context, packId)
        val deleted = packDir.deleteRecursively()
        CustomSoundManager.getInstance(context).reloadIfActive(packId)
        return deleted
    }

    fun getFilename(context: Context, uri: Uri): String? {
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
}
