// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.os.Build
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.AddonPolicy

class SettingsAvailability(
    val flavor: String = BuildConfig.FLAVOR,
    val buildType: String = BuildConfig.BUILD_TYPE,
    val sdk: Int = Build.VERSION.SDK_INT,
) {
    val cloudAi: Boolean get() = AddonPolicy.allowsInAppDownloads(flavor)
    val offlineAi: Boolean get() = flavor == "offline" && sdk >= Build.VERSION_CODES.O
    val ai: Boolean get() = cloudAi || offlineAi
    val ocr: Boolean get() = AddonPolicy.allowsOcrPlugins(buildType, sdk)

    fun isAvailable(key: String): Boolean {
        if (key == SettingsWithoutKey.SCREEN_NAV_OCR || key.startsWith("pref_ocr_")) return ocr
        return when (key) {
            SettingsWithoutKey.GEMINI_API_KEY,
            SettingsWithoutKey.GEMINI_MODEL,
            SettingsWithoutKey.GEMINI_TARGET_LANGUAGE,
            SettingsWithoutKey.GROQ_TOKEN,
            SettingsWithoutKey.GROQ_MODEL,
            SettingsWithoutKey.HUGGINGFACE_TOKEN,
            SettingsWithoutKey.HUGGINGFACE_MODEL,
            SettingsWithoutKey.HUGGINGFACE_ENDPOINT,
            SettingsWithoutKey.AI_PROVIDER,
            SettingsWithoutKey.TRANSLATE_GEMINI_MODEL,
            SettingsWithoutKey.TRANSLATE_GROQ_MODEL,
            SettingsWithoutKey.TRANSLATE_HUGGINGFACE_MODEL,
            SettingsWithoutKey.AI_ALLOW_INSECURE_CONNECTIONS,
            SettingsWithoutKey.CLOUD_AI_MAX_TOKENS -> cloudAi
            SettingsWithoutKey.LOAD_OFFLINE_AI_PLUGIN,
            SettingsWithoutKey.OFFLINE_MODEL_PATH,
            SettingsWithoutKey.OFFLINE_KEEP_MODEL_LOADED -> offlineAi
            SettingsWithoutKey.SCREEN_NAV_AI_INTEGRATION,
            SettingsWithoutKey.CUSTOM_AI_KEYS,
            SettingsWithoutKey.TRANSLATION_ENGINE -> ai
            else -> true
        }
    }
}
