// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

object SoundPackImporter {
    private const val TAG = "SoundPackImporter"
    private const val SOUND_PACKS_DIR_NAME = "sound_packs"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

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
        require(SoundPackRules.isValidId(packId)) { "Invalid sound pack ID: $packId" }
        val root = getSoundPacksDir(context).canonicalFile
        val directory = File(root, packId).canonicalFile
        require(directory.parentFile == root && directory.name == packId) {
            "Sound pack must be a strict direct child of its root"
        }
        return directory
    }

    internal fun tryGetPackDir(context: Context, packId: String): File? = try {
        getPackDir(context, packId)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Invalid sound pack directory for '$packId'", e)
        null
    } catch (e: IOException) {
        Log.e(TAG, "Cannot resolve sound pack directory for '$packId'", e)
        null
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) return true
        return getManifest(context, packId) != null
    }

    fun getManifest(context: Context, packId: String): SoundPackManifest? {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) return null
        val packDir = tryGetPackDir(context, packId) ?: return null
        if (!packDir.exists() || !packDir.isDirectory) return null
        val manifestFile = File(packDir, "pack.json")
        if (manifestFile.exists()) {
            try {
                return readManifest(manifestFile).takeIf { it.id == packId }
            } catch (e: Throwable) {
                Log.e(TAG, "Invalid pack.json for $packId", e)
                return null
            }
        }

        // Legacy pack fallback
        val audioFiles = getPackAudioFiles(context, packId)
        if (audioFiles.isValid) {
            val nameFile = File(packDir, "name.txt")
            val displayName = if (nameFile.exists()) {
                try { nameFile.readText().trim() } catch (_: Throwable) { packId }
            } else {
                packId.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
            val soundsMap = mutableMapOf<String, SoundEvent>()
            audioFiles.standardFile?.let { soundsMap["keypress.default"] = SoundEvent(files = listOf(it.relativeTo(packDir).invariantSeparatorsPath)) }
            audioFiles.spaceFile?.let { soundsMap["keypress.space"] = SoundEvent(files = listOf(it.relativeTo(packDir).invariantSeparatorsPath)) }
            audioFiles.deleteFile?.let { soundsMap["keypress.delete"] = SoundEvent(files = listOf(it.relativeTo(packDir).invariantSeparatorsPath)) }
            audioFiles.enterFile?.let { soundsMap["keypress.return"] = SoundEvent(files = listOf(it.relativeTo(packDir).invariantSeparatorsPath)) }

            return SoundPackManifest(
                schemaVersion = 1,
                id = packId,
                name = displayName,
                summary = "Custom imported sound pack",
                sounds = soundsMap
            )
        }
        return null
    }

    fun getPackAudioFiles(context: Context, packId: String): PackFiles {
        val packDir = tryGetPackDir(context, packId)
            ?: return PackFiles(null, null, null, null)
        if (!packDir.exists() || !packDir.isDirectory) {
            return PackFiles(null, null, null, null)
        }

        val allFiles = packDir.walkTopDown().onEnter { dir ->
            dir.canonicalFile == dir.absoluteFile
        }.filter { file ->
            file.isFile && resolveAudioFile(packDir, file.relativeTo(packDir).invariantSeparatorsPath) != null
        }.toList()

        fun findFile(prefixes: List<String>): File? {
            return allFiles.firstOrNull { file ->
                val name = file.nameWithoutExtension.lowercase()
                prefixes.any { name == it || name.startsWith("${it}_") || name.startsWith("${it}-") }
            }
        }

        val standard = findFile(listOf("standard", "click", "default", "key", "press", "tap", "keypress_default_1", "keypress_default"))
            ?: allFiles.firstOrNull()
        val space = findFile(listOf("space", "spacebar")) ?: standard
        val delete = findFile(listOf("delete", "backspace", "del")) ?: standard
        val enter = findFile(listOf("enter", "return")) ?: standard

        return PackFiles(standard, space, delete, enter)
    }

    internal fun resolveAudioFile(packDir: File, relativePath: String): File? {
        if (relativePath.isBlank() || File(relativePath).isAbsolute) return null
        if (relativePath.split('/', '\\').any { it == "." || it == ".." }) return null
        return try {
            val root = packDir.canonicalFile
            File(root, relativePath).canonicalFile.takeIf {
                it.path.startsWith(root.path + File.separator) &&
                    it.isFile && it.extension.lowercase() in SoundPackRules.AUDIO_EXTENSIONS
            }
        } catch (e: IOException) {
            Log.e(TAG, "Cannot resolve sound pack audio '$relativePath' in '$packDir'", e)
            null
        }
    }

    private fun readManifest(file: File): SoundPackManifest {
        val manifest = json.decodeFromString<SoundPackManifest>(file.readText(Charsets.UTF_8))
        require(manifest.schemaVersion == 1 && SoundPackRules.isValidId(manifest.id)) {
            "Invalid sound pack schema or ID"
        }
        require(manifest.sounds.isNotEmpty() && manifest.sounds.values.all { event ->
            event.files.isNotEmpty() && event.files.all { resolveAudioFile(file.parentFile!!, it) != null }
        }) { "Invalid sound pack audio references" }
        require(manifest.preview == null || resolveAudioFile(file.parentFile!!, manifest.preview) != null) {
            "Invalid sound pack preview"
        }
        return manifest
    }

    val LEGACY_ID_MAP = mapOf(
        "dev.leantype.sounds.gateron-oil-king-thock" to "dev.leantype.sounds.thock",
        "dev.leantype.sounds.gateron_oil_king_thock" to "dev.leantype.sounds.thock",
        "dev.leantype.sounds.kailh-box-jade-clicky" to "dev.leantype.sounds.clicky",
        "dev.leantype.sounds.kailh_box_jade_clicky" to "dev.leantype.sounds.clicky",
        "dev.leantype.sounds.holy-panda-tactile" to "dev.leantype.sounds.tactile",
        "dev.leantype.sounds.holy_panda_tactile" to "dev.leantype.sounds.tactile",
        "dev.leantype.sounds.ibm-model-m-beamspring" to "dev.leantype.sounds.mechanical",
        "dev.leantype.sounds.ibm_model_m_beamspring" to "dev.leantype.sounds.mechanical",
        "dev.leantype.sounds.classic-1930s-royal-typewriter" to "dev.leantype.sounds.typewriter",
        "dev.leantype.sounds.classic_1930s_royal_typewriter" to "dev.leantype.sounds.typewriter",
        "dev.leantype.sounds.creamy-linear-jelly" to "dev.leantype.sounds.creamy",
        "dev.leantype.sounds.creamy_linear_jelly" to "dev.leantype.sounds.creamy",
        "dev.leantype.sounds.8-bit-chiptune-arcade" to "dev.leantype.sounds.chiptune",
        "dev.leantype.sounds.arcade_8bit_chiptune" to "dev.leantype.sounds.chiptune",
        "dev.leantype.sounds.minimalistic-ceramic-glass-marble" to "dev.leantype.sounds.glass",
        "dev.leantype.sounds.minimalistic_ceramic_glass_marble" to "dev.leantype.sounds.glass",
        "dev.leantype.sounds.water-bubble-pop" to "dev.leantype.sounds.bubble",
        "dev.leantype.sounds.water_bubble_pop" to "dev.leantype.sounds.bubble",
        "dev.leantype.sounds.acoustic-teak-woodblock" to "dev.leantype.sounds.woodblock",
        "dev.leantype.sounds.acoustic_teak_woodblock" to "dev.leantype.sounds.woodblock",
        "dev.leantype.sounds.grand-piano" to "dev.leantype.sounds.piano",
        "dev.leantype.sounds.nylon-guitar" to "dev.leantype.sounds.acoustic-pluck",
        "dev.leantype.sounds.kerala-chenda" to "dev.leantype.sounds.folk-drum",
        "dev.leantype.sounds.carnatic-mridangam" to "dev.leantype.sounds.resonant-drum",
        "dev.leantype.sounds.kalimba-tines" to "dev.leantype.sounds.kalimba",
        "dev.leantype.sounds.orchestral-pizzicato" to "dev.leantype.sounds.pizzicato"
    )

    fun getInstalledCustomPacks(context: Context): List<SoundPackInfo> {
        val packsDir = getSoundPacksDir(context)
        val dirs = packsDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        val list = mutableListOf<SoundPackInfo>()

        for (dir in dirs) {
            val id = dir.name
            if (SoundPackUrls.isPreset(id)) continue
            val safeDir = tryGetPackDir(context, id) ?: continue

            // Auto-clean legacy folder if new canonical pack exists
            val canonicalId = LEGACY_ID_MAP[id]
            if (canonicalId != null && isPackInstalled(context, canonicalId)) {
                safeDir.deleteRecursively()
                continue
            }

            val manifest = getManifest(context, id)
            if (manifest != null) {
                list.add(
                    SoundPackInfo(
                        id = id,
                        displayName = manifest.name,
                        description = manifest.summary ?: "Custom sound pack",
                        author = manifest.author,
                        versionName = manifest.versionName,
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

        return try {
            val tempFile = File(context.cacheDir, "sound_import_${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            try {
                if (ext == "zip") {
                    importFromZipFile(context, tempFile, customName = customName)
                } else if (ext in SoundPackRules.AUDIO_EXTENSIONS) {
                    importSingleAudioFile(context, tempFile, filename, customName)
                } else {
                    null
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import sound from URI $uri", e)
            null
        }
    }

    fun importSingleAudioFile(
        context: Context,
        audioFile: File,
        originalName: String,
        customName: String? = null
    ): String? {
        val ext = originalName.substringAfterLast(".", "ogg").lowercase()
        val rawName = originalName.substringBeforeLast(".")
        val cleanId = "custom_${UUID.randomUUID()}"
        val displayName = customName?.takeIf { it.isNotBlank() } ?: rawName
        val packDir = getPackDir(context, cleanId)
        packDir.mkdirs()

        val audioSubDir = File(packDir, "audio").apply { mkdirs() }
        val targetFile = File(audioSubDir, "keypress_default.$ext")
        audioFile.copyTo(targetFile, overwrite = true)

        val manifest = SoundPackManifest(
            schemaVersion = 1,
            id = cleanId,
            name = displayName,
            summary = "Single imported sound",
            versionCode = 1,
            versionName = "1.0",
            sounds = mapOf(
                "keypress.default" to SoundEvent(
                    files = listOf("audio/keypress_default.$ext"),
                    mode = SoundMode.SINGLE,
                    volume = 1f
                )
            )
        )
        File(packDir, "pack.json").writeText(json.encodeToString(manifest))
        File(packDir, "name.txt").writeText(displayName)
        CustomSoundManager.getInstance(context).reloadIfActive(cleanId)
        return cleanId
    }

    fun importFromZipFile(
        context: Context,
        zipFile: File,
        expectedSha256: String? = null,
        customName: String? = null
    ): String? {
        if (!zipFile.exists() || zipFile.length() > SoundPackRules.MAX_ZIP_SIZE) {
            Log.e(TAG, "Zip file exceeds max allowed size or does not exist")
            return null
        }

        if (expectedSha256 != null) {
            val actualSha256 = calculateSha256(zipFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                Log.e(TAG, "SHA-256 verification failed: expected $expectedSha256, got $actualSha256")
                return null
            }
        }

        val stagingDir = File(context.cacheDir, "sound_unpack_${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            val unzippedOk = unzipSafe(zipFile, stagingDir)
            if (!unzippedOk) {
                return null
            }

            // Locate pack.json or audio files
            val manifestFile = findPackJson(stagingDir)
            val manifest: SoundPackManifest?
            val effectiveRoot = manifestFile?.parentFile ?: findAudioRoot(stagingDir) ?: stagingDir

            if (manifestFile != null && manifestFile.exists()) {
                manifest = try {
                    readManifest(manifestFile)
                } catch (e: Throwable) {
                    Log.e(TAG, "pack.json failed validation", e)
                    return null
                }
            } else {
                manifest = null
            }

            val finalId = if (manifest != null) {
                manifest.id
            } else {
                "custom_${UUID.randomUUID()}"
            }

            val finalDir = getPackDir(context, finalId)
            finalDir.deleteRecursively()
            finalDir.mkdirs()

            effectiveRoot.listFiles()?.forEach { file ->
                file.copyRecursively(File(finalDir, file.name), overwrite = true)
            }

            // If no pack.json, generate one for structured playback
            if (!File(finalDir, "pack.json").exists()) {
                val packAudio = getPackAudioFiles(context, finalId)
                if (!packAudio.isValid) {
                    Log.e(TAG, "No valid audio files found in unzipped pack")
                    finalDir.deleteRecursively()
                    return null
                }
                val name = customName?.takeIf { it.isNotBlank() }
                    ?: zipFile.nameWithoutExtension.replace("_", " ").replaceFirstChar { it.uppercase() }

                val generatedManifest = SoundPackManifest(
                    schemaVersion = 1,
                    id = finalId,
                    name = name,
                    summary = "Custom imported sound pack",
                    sounds = buildMap {
                        packAudio.standardFile?.let { put("keypress.default", SoundEvent(files = listOf(it.relativeTo(finalDir).invariantSeparatorsPath))) }
                        packAudio.spaceFile?.let { put("keypress.space", SoundEvent(files = listOf(it.relativeTo(finalDir).invariantSeparatorsPath))) }
                        packAudio.deleteFile?.let { put("keypress.delete", SoundEvent(files = listOf(it.relativeTo(finalDir).invariantSeparatorsPath))) }
                        packAudio.enterFile?.let { put("keypress.return", SoundEvent(files = listOf(it.relativeTo(finalDir).invariantSeparatorsPath))) }
                    }
                )
                File(finalDir, "pack.json").writeText(json.encodeToString(generatedManifest))
                File(finalDir, "name.txt").writeText(name)
            }

            CustomSoundManager.getInstance(context).reloadIfActive(finalId)
            finalId
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import sound pack from zip", e)
            null
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun unzipSafe(zipFile: File, targetDir: File): Boolean {
        var totalBytes = 0L
        var entryCount = 0
        val targetCanonical = targetDir.canonicalPath

        ZipInputStream(FileInputStream(zipFile).buffered()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > SoundPackRules.MAX_ENTRIES) {
                    Log.e(TAG, "Zip contains too many entries (> ${SoundPackRules.MAX_ENTRIES})")
                    return false
                }

                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.contains("..")) {
                    Log.e(TAG, "Zip entry contains invalid path traversal: $name")
                    return false
                }

                val outFile = File(targetDir, name)
                if (!outFile.canonicalPath.startsWith(targetCanonical + File.separator)) {
                    Log.e(TAG, "Zip entry escapes target dir: $name")
                    return false
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    val ext = outFile.extension.lowercase()
                    if (ext !in SoundPackRules.ALLOWED_EXTENSIONS) {
                        Log.w(TAG, "Skipping disallowed extension in zip: ${outFile.name}")
                    } else {
                        outFile.parentFile?.mkdirs()
                        var fileBytes = 0L
                        val buffer = ByteArray(8192)
                        FileOutputStream(outFile).use { out ->
                            var read = zipIn.read(buffer)
                            while (read != -1) {
                                fileBytes += read
                                totalBytes += read
                                if (fileBytes > SoundPackRules.MAX_FILE_SIZE) {
                                    Log.e(TAG, "Single file inside zip is too large: ${outFile.name}")
                                    return false
                                }
                                if (totalBytes > SoundPackRules.MAX_UNPACKED_SIZE) {
                                    Log.e(TAG, "Total unpacked size exceeds max limit")
                                    return false
                                }
                                out.write(buffer, 0, read)
                                read = zipIn.read(buffer)
                            }
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        return true
    }

    private fun findPackJson(root: File): File? {
        val direct = File(root, "pack.json")
        if (direct.exists()) return direct
        return root.walkTopDown().firstOrNull { it.isFile && it.name == "pack.json" }
    }

    private fun findAudioRoot(root: File): File? {
        val audio = root.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in SoundPackRules.AUDIO_EXTENSIONS
        }.toList()
        var commonParent = audio.firstOrNull()?.parentFile ?: return null
        while (audio.any { !it.path.startsWith(commonParent.path + File.separator) }) {
            commonParent = commonParent.parentFile ?: return null
        }
        return commonParent
    }

    fun downloadAndInstall(
        context: Context,
        pack: RemoteSoundPack,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (!helium314.keyboard.latin.utils.AddonPolicy.allowsInAppDownloads()) {
            Log.w(TAG, "Sound pack download blocked: use browser download and local import")
            return false
        }
        val tempZip = File(context.cacheDir, "sound_dl_${UUID.randomUUID()}.zip")
        return try {
            val url = URL(pack.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", "LeanType")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed with HTTP ${connection.responseCode}")
                return false
            }

            val contentLength: Long = if (connection.contentLength > 0) connection.contentLength.toLong() else if (pack.sizeBytes > 0) pack.sizeBytes else -1L
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (contentLength > 0) {
                            onProgress(downloadedBytes.toFloat() / contentLength.toFloat())
                        }
                        read = input.read(buffer)
                    }
                }
            }

            val sha = pack.sha256.takeIf { it.isNotBlank() }
            val installedId = importFromZipFile(context, tempZip, expectedSha256 = sha)
            installedId != null
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to download sound pack ${pack.id}: ${e.message}", e)
            false
        } finally {
            tempZip.delete()
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deletePack(context: Context, packId: String): Boolean {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID || SoundPackUrls.isPreset(packId)) return false
        val packDir = tryGetPackDir(context, packId) ?: return false
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
