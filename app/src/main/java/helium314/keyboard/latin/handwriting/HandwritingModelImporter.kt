// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.net.Uri
import android.util.Log
import helium314.keyboard.latin.utils.locale
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
        val isComplete: Boolean get() = hasModel && hasFst && hasRecospec
        val isReady: Boolean get() = hasModel && hasFst
    }

    private val COMMON_REGIONAL_VARIANTS = mapOf(
        "en" to listOf("en", "en-US", "en_US", "en-GB", "en_GB", "en-IN", "en_IN", "en-AU", "en_AU", "en-CA", "en_CA", "en-NZ", "en_NZ", "en-ZA", "en_ZA", "en-SG", "en_SG", "en-PH", "en_PH", "en-IE", "en_IE"),
        "es" to listOf("es", "es-ES", "es_ES", "es-US", "es_US", "es-419", "es_419", "es-MX", "es_MX", "es-AR", "es_AR", "es-CO", "es_CO", "es-CL", "es_CL", "es-PE", "es_PE"),
        "fr" to listOf("fr", "fr-FR", "fr_FR", "fr-CA", "fr_CA", "fr-BE", "fr_BE", "fr-CH", "fr_CH"),
        "de" to listOf("de", "de-DE", "de_DE", "de-AT", "de_AT", "de-CH", "de_CH"),
        "pt" to listOf("pt", "pt-BR", "pt_BR", "pt-PT", "pt_PT"),
        "zh" to listOf("zh", "zh-CN", "zh_CN", "zh-TW", "zh_TW", "zh-HK", "zh_HK", "zh-Hans", "zh_Hans", "zh-Hant", "zh_Hant"),
        "ar" to listOf("ar", "ar-EG", "ar_EG", "ar-SA", "ar_SA", "ar-AE", "ar_AE"),
        "it" to listOf("it", "it-IT", "it_IT", "it-CH", "it_CH"),
        "nl" to listOf("nl", "nl-NL", "nl_NL", "nl-BE", "nl_BE"),
        "ru" to listOf("ru", "ru-RU", "ru_RU", "ru-UA", "ru_UA", "ru-BY", "ru_BY", "ru-KZ", "ru_KZ"),
        "hi" to listOf("hi", "hi-IN", "hi_IN"),
        "ta" to listOf("ta", "ta-IN", "ta_IN", "ta-LK", "ta_LK", "ta-SG", "ta_SG"),
        "bn" to listOf("bn", "bn-BD", "bn_BD", "bn-IN", "bn_IN"),
        "ml" to listOf("ml", "ml-IN", "ml_IN"),
        "te" to listOf("te", "te-IN", "te_IN"),
        "kn" to listOf("kn", "kn-IN", "kn_IN"),
        "gu" to listOf("gu", "gu-IN", "gu_IN"),
        "mr" to listOf("mr", "mr-IN", "mr_IN"),
        "pa" to listOf("pa", "pa-IN", "pa_IN", "pa-PK", "pa_PK"),
        "ur" to listOf("ur", "ur-PK", "ur_PK", "ur-IN", "ur_IN"),
        "tr" to listOf("tr", "tr-TR", "tr_TR"),
        "ko" to listOf("ko", "ko-KR", "ko_KR"),
        "ja" to listOf("ja", "ja-JP", "ja_JP"),
        "sv" to listOf("sv", "sv-SE", "sv_SE", "sv-FI", "sv_FI"),
        "no" to listOf("no", "nb", "nn", "nb-NO", "nb_NO", "nn-NO", "nn_NO", "no-NO", "no_NO"),
        "da" to listOf("da", "da-DK", "da_DK"),
        "fi" to listOf("fi", "fi-FI", "fi_FI"),
        "pl" to listOf("pl", "pl-PL", "pl_PL"),
        "uk" to listOf("uk", "uk-UA", "uk_UA"),
        "el" to listOf("el", "el-GR", "el_GR", "el-CY", "el_CY"),
        "he" to listOf("he", "iw", "he-IL", "he_IL", "iw-IL", "iw_IL"),
        "th" to listOf("th", "th-TH", "th_TH"),
        "vi" to listOf("vi", "vi-VN", "vi_VN"),
        "id" to listOf("id", "id-ID", "id_ID"),
        "ms" to listOf("ms", "ms-MY", "ms_MY")
    )

    fun getAllTagVariants(languageTag: String): List<String> {
        val raw = languageTag.trim()
        if (raw.isEmpty()) return emptyList()
        val normalized = raw.replace('_', '-')
        val lower = normalized.lowercase()
        val underscore = raw.replace('-', '_')
        val lowerUnderscore = lower.replace('-', '_')

        val formatted = try {
            val loc = java.util.Locale.forLanguageTag(normalized)
            if (loc.toLanguageTag() != "und") loc.toLanguageTag() else normalized
        } catch (_: Throwable) {
            normalized
        }
        val formattedUnderscore = formatted.replace('-', '_')
        val baseLang = normalized.substringBefore('-').lowercase()

        val variants = mutableListOf(
            raw,
            normalized,
            lower,
            underscore,
            lowerUnderscore,
            formatted,
            formattedUnderscore,
            baseLang
        )

        COMMON_REGIONAL_VARIANTS[baseLang]?.let { regionalList ->
            variants.addAll(regionalList)
            variants.addAll(regionalList.map { it.lowercase() })
        }

        return variants.filter { it.isNotEmpty() }.distinct()
    }

    fun hasModelDirectly(context: Context, languageTag: String): Boolean {
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        val tagsToTry = listOf(languageTag, languageTag.replace('_', '-'), languageTag.replace('-', '_')).distinct()
        for (baseDir in baseDirs) {
            for (tag in tagsToTry) {
                val dir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
                if (dir.exists()) {
                    val hasModel = File(dir, "model.tflite").exists() && File(dir, "model.tflite").length() > 0
                    val hasFst = File(dir, "fst.compact").exists() && File(dir, "fst.compact").length() > 0
                    if (hasModel && hasFst) return true
                }
            }
        }
        return false
    }

    fun getComponentsStatus(context: Context, languageTag: String): ModelComponentsStatus {
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        val tagsToTry = listOf(languageTag, languageTag.replace('_', '-'), languageTag.replace('-', '_')).distinct()

        for (baseDir in baseDirs) {
            for (tag in tagsToTry) {
                val dir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
                if (dir.exists()) {
                    val hasModel = File(dir, "model.tflite").exists() && File(dir, "model.tflite").length() > 0
                    val hasFst = File(dir, "fst.compact").exists() && File(dir, "fst.compact").length() > 0
                    val hasRecospec = File(dir, "recospec").exists() && File(dir, "recospec").length() > 0
                    if (hasModel || hasFst || hasRecospec) {
                        return ModelComponentsStatus(hasModel, hasFst, hasRecospec)
                    }
                }
            }
        }
        return ModelComponentsStatus(false, false, false)
    }

    fun getInstalledLanguageStatuses(context: Context): Map<String, ModelComponentsStatus> {
        val result = mutableMapOf<String, ModelComponentsStatus>()
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        for (baseDir in baseDirs) {
            val modelsRoot = File(baseDir, "com.google.mlkit.models")
            if (modelsRoot.exists() && modelsRoot.isDirectory) {
                modelsRoot.listFiles()?.forEach { langDir ->
                    if (langDir.isDirectory) {
                        val dir = File(langDir, "DIGITAL_INK/0")
                        if (dir.exists()) {
                            val hasModel = File(dir, "model.tflite").exists() && File(dir, "model.tflite").length() > 0
                            val hasFst = File(dir, "fst.compact").exists() && File(dir, "fst.compact").length() > 0
                            val hasRecospec = File(dir, "recospec").exists() && File(dir, "recospec").length() > 0
                            if (hasModel || hasFst || hasRecospec) {
                                result[langDir.name] = ModelComponentsStatus(hasModel, hasFst, hasRecospec)
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    fun deleteModelForLanguage(context: Context, languageTag: String): Boolean {
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        val tagsToTry = listOf(languageTag, languageTag.replace('_', '-'), languageTag.replace('-', '_')).distinct()
        var deleted = false
        for (baseDir in baseDirs) {
            for (tag in tagsToTry) {
                val dir = File(baseDir, "com.google.mlkit.models/$tag/DIGITAL_INK/0")
                if (dir.exists()) {
                    if (dir.deleteRecursively()) deleted = true
                }
                val parent = File(baseDir, "com.google.mlkit.models/$tag")
                if (parent.exists()) {
                    if (parent.deleteRecursively()) deleted = true
                }
            }
        }
        Log.i(TAG, "Deleted handwriting model for $languageTag (deleted=$deleted)")
        return deleted
    }

    private val SCRIPT_TO_LANG = mapOf(
        "malayalam" to "ml", "tamil" to "ta", "telugu" to "te", "devanagari" to "hi",
        "bengali" to "bn", "gujarati" to "gu", "kannada" to "kn", "arabic" to "ar",
        "japanese" to "ja", "korean" to "ko", "thai" to "th", "vietnamese" to "vi",
        "myanmar" to "my", "sinhala" to "si", "odia" to "or", "punjabi" to "pa"
    )

    fun detectLanguageTag(filename: String): String? {
        val name = filename.lowercase()
        val qrnnRegex = Regex("""qrnn[._]([a-z]{2,3}(?:[_-][a-z0-9]+)?)[._]reco""")
        qrnnRegex.find(name)?.let { return it.groupValues[1].replace('_', '-') }

        val fstRegex = Regex("""^([a-z]{2,3}(?:[_-][a-z0-9]+)?)[._]\d+[._]compact""")
        fstRegex.find(name)?.let { return it.groupValues[1].replace('_', '-') }

        val zipRegex = Regex("""^([a-z]{2,3}(?:[_-][a-z0-9]+)?)(?:[._-]model)?\.zip$""")
        zipRegex.find(name)?.let { return it.groupValues[1].replace('_', '-') }

        val lstmRegex = Regex("""lstm[._]([a-z]+)[._]""")
        lstmRegex.find(name)?.let {
            val script = it.groupValues[1]
            return SCRIPT_TO_LANG[script] ?: script
        }

        for ((script, lang) in SCRIPT_TO_LANG) {
            if (name.contains(script)) return lang
        }
        return null
    }

    fun importAutoDetectedUris(context: Context, uris: List<Uri>): Set<String> {
        val importedTags = mutableSetOf<String>()
        val pendingSharedModels = mutableListOf<Pair<Uri, String>>()

        for (uri in uris) {
            val filename = getFilename(context, uri) ?: uri.lastPathSegment ?: ""
            val detectedTag = detectLanguageTag(filename)
            if (detectedTag != null && detectedTag != "latin") {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val ok = importForLanguageFromStream(context, detectedTag, stream, filename)
                        if (ok) importedTags.add(detectedTag)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed auto import for $detectedTag from $uri", e)
                }
            } else {
                pendingSharedModels.add(Pair(uri, filename))
            }
        }

        if (importedTags.isNotEmpty()) {
            for ((uri, filename) in pendingSharedModels) {
                for (tag in importedTags) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            importForLanguageFromStream(context, tag, stream, filename)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } else if (pendingSharedModels.isNotEmpty()) {
            val enabledSubtypes = helium314.keyboard.latin.utils.SubtypeSettings.getEnabledSubtypes(true)
            for (sub in enabledSubtypes) {
                val tag = sub.locale().toLanguageTag()
                for ((uri, filename) in pendingSharedModels) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val ok = importForLanguageFromStream(context, tag, stream, filename)
                            if (ok) importedTags.add(tag)
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        return importedTags
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
        val normalizedTag = languageTag.replace('_', '-')

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

            val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
            val targetTags = getAllTagVariants(languageTag)
            for (bDir in baseDirs) {
                for (tTag in targetTags) {
                    val targetDir = File(bDir, "com.google.mlkit.models/$tTag/DIGITAL_INK/0")
                    targetDir.mkdirs()
                    for (file in extractedFiles) {
                        val targetFile = File(targetDir, file.name)
                        file.copyTo(targetFile, overwrite = true)
                    }
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

    suspend fun downloadPacksForLanguage(
        context: Context,
        languageTag: String,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val urls = HandwritingModelUrls.getDownloadUrls(languageTag)
        if (urls.isEmpty()) return@withContext false

        var successCount = 0
        val total = urls.size
        for ((index, urlStr) in urls.withIndex()) {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                if (conn.responseCode in 200..299) {
                    val filename = urlStr.substringAfterLast('/')
                    conn.inputStream.use { stream ->
                        val ok = importForLanguageFromStream(context, languageTag, stream, filename)
                        if (ok) successCount++
                    }
                }
                onProgress?.invoke((index + 1).toFloat() / total)
            } catch (e: Throwable) {
                Log.e(TAG, "Error downloading model pack $urlStr for $languageTag", e)
            }
        }
        successCount > 0
    }
}
