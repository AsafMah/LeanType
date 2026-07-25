package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import helium314.keyboard.latin.R
import java.util.Locale

object LocaleUtils {
    fun wrapContextWithLocale(context: Context, localeTag: String): Context {
        if (localeTag.isEmpty() || localeTag == "system") {
            return context
        }
        val locale = if (localeTag.contains("-")) {
            val parts = localeTag.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(localeTag)
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = android.os.LocaleList(locale)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    val localeCodes = listOf(
        "en", "af", "am", "ar", "as", "ast", "az", "be", "bg", "bn", "bs", "ca", "cs", "cy", "da", "de", "dv", "el", "es", "es-US", "et", "eu", "fa", "fi", "fil", "fr", "gd", "gl", "gu", "hi", "hr", "hu", "hy", "in", "is", "it", "iw", "ja", "ka", "kab", "kk", "km", "kn", "ko", "kw", "ky", "lb", "lo", "lt", "lv", "mk", "ml", "mn", "mr", "ms", "my", "nb", "ne", "nl", "or", "pa", "pl", "pt", "pt-BR", "pt-PT", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sv", "sw", "ta", "te", "tg", "th", "tl", "tr", "uk", "ur", "uz", "vi", "zh-CN", "zh-HK", "zh-TW", "zu"
    )

    fun getAppLanguageItems(context: Context): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        items.add(context.getString(R.string.app_language_system) to "")
        for (code in localeCodes) {
            val locale = if (code.contains("-")) {
                val parts = code.split("-")
                Locale(parts[0], parts[1])
            } else {
                Locale(code)
            }
            val name = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
            items.add(name to code)
        }
        return items
    }
}
