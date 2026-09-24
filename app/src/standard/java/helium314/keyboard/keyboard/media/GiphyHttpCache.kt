// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Extract only cache semantics; neither headers nor request URLs enter persistent storage. */
internal object GiphyHttpCache {
    private const val MAX_TTL = 60 * 60 * 1000L

    fun read(connection: HttpURLConnection, now: Long): MediaCacheInfo {
        fun values(name: String): List<String> {
            val repeated = connection.headerFields.orEmpty().entries
                .filter { it.key?.equals(name, true) == true }.flatMap { it.value.orEmpty() }
            return repeated.ifEmpty { listOfNotNull(connection.getHeaderField(name)) }
        }
        val fields = listOf("Cache-Control", "Age", "Expires", "Date", "Pragma", "Vary", "Set-Cookie")
            .associateWith(::values)
        if (fields.values.flatten().sumOf { it.length.toLong() } > 4096 ||
            listOf("Age", "Expires", "Date").any { fields.getValue(it).distinct().size > 1 })
            return MediaCacheInfo(storable = false)
        fun combined(name: String) = fields.getValue(name).takeIf { it.isNotEmpty() }?.joinToString(",")
        val control = combined("Cache-Control")
        val ageText = fields.getValue("Age").firstOrNull()
        val expiresText = fields.getValue("Expires").firstOrNull()
        val dateText = fields.getValue("Date").firstOrNull()
        val pragma = combined("Pragma")
        if (listOfNotNull(control, ageText, expiresText, dateText, pragma).any { it.length > 4096 })
            return MediaCacheInfo(storable = false)
        if (pragma?.split(',')?.any { it.trim().equals("no-cache", true) } == true ||
            fields.getValue("Vary").isNotEmpty() ||
            fields.getValue("Set-Cookie").isNotEmpty()) return MediaCacheInfo(storable = false)
        val directives = control?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }.orEmpty()
        if (directives.any { it.substringBefore('=').trim() in setOf("no-store", "no-cache", "private") })
            return MediaCacheInfo(storable = false)
        val ages = directives.filter { it.substringBefore('=').trim() == "max-age" }
        if (ages.size > 1) return MediaCacheInfo(storable = false)
        val maxAge = ages.singleOrNull()?.let {
            it.substringAfter('=', "").trim().removeSurrounding("\"").toLongOrNull()
                ?.takeIf { seconds -> seconds >= 0 && seconds <= Long.MAX_VALUE / 1000 }
                ?.times(1000) ?: return MediaCacheInfo(storable = false)
        }
        val age = ageText?.trim()?.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
        if (ageText != null && age == null) return MediaCacheInfo(storable = false)
        val date = dateText?.let(::date)
        val expires = expiresText?.let(::date)
        if (dateText != null && date == null || expiresText != null && expires == null ||
            now < 0 || now > Long.MAX_VALUE - MAX_TTL) return MediaCacheInfo(storable = false)
        if (control == null && ageText == null && expiresText == null && dateText == null) return MediaCacheInfo()
        val apparentAge = if (date != null) (now - date).coerceAtLeast(0) else 0
        val currentAge = maxOf(age ?: 0, apparentAge)
        val lifetime = maxAge ?: expires?.let { (it - (date ?: now)).coerceAtLeast(0) } ?: MAX_TTL
        val remaining = (lifetime - currentAge).coerceAtLeast(0).coerceAtMost(MAX_TTL)
        return MediaCacheInfo(expiresAt = now + remaining)
    }

    fun combine(first: MediaCacheInfo, second: MediaCacheInfo) = MediaCacheInfo(
        storable = first.storable && second.storable,
        expiresAt = listOfNotNull(first.expiresAt, second.expiresAt).minOrNull()
    )

    private fun date(value: String): Long? {
        for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM d HH:mm:ss yyyy")) {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }
            val position = java.text.ParsePosition(0)
            val result = format.parse(value, position)
            if (result != null && position.index == value.length && result.time >= 0) return result.time
        }
        return null
    }
}
