package com.example.mmp.domain.model

import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONObject

enum class StreamingProvider(val title: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    SOUNDCLOUD("SoundCloud")
}

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class TrackSortMode(val label: String) {
    DATE_ADDED("Сначала новые"),
    TITLE("По названию (А-Я)"),
    ARTIST("По исполнителю"),
    DURATION("По длительности")
}

enum class ShapeStyle(val label: String) {
    SQUIRCLE("Стандартная")
}

enum class ThemeMode(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    ADAPTIVE("Adaptive"),
    ADAPTIVE_LIGHT("Adaptive Светлая")
}

data class MMPThemeConfig(
    val themeName: String = "Material 3 Expressive",
    val primaryHex: Long = 0xFF6750A4,
    val secondaryHex: Long = 0xFF625B71,
    val bgHex: Long = 0xFF141218,
    val shapeStyle: ShapeStyle = ShapeStyle.SQUIRCLE,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val blurIntensity: Float = 18f
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeName", themeName)
        json.put("primaryHex", primaryHex)
        json.put("secondaryHex", secondaryHex)
        json.put("bgHex", bgHex)
        json.put("shapeStyle", shapeStyle.name)
        json.put("themeMode", themeMode.name)
        json.put("blurIntensity", blurIntensity.toDouble())
        return json.toString(4)
    }

    companion object {
        fun fromJson(jsonString: String): MMPThemeConfig? {
            return try {
                val json = JSONObject(jsonString)
                val styleName = json.optString("shapeStyle", "SQUIRCLE")
                val style = ShapeStyle.entries.find { it.name == styleName } ?: ShapeStyle.SQUIRCLE
                val modeName = json.optString("themeMode", "DARK")
                val mode = ThemeMode.entries.find { it.name == modeName } ?: ThemeMode.DARK
                MMPThemeConfig(
                    themeName = json.optString("themeName", "Material 3 Expressive"),
                    primaryHex = json.optLong("primaryHex", 0xFF6750A4),
                    secondaryHex = json.optLong("secondaryHex", 0xFF625B71),
                    bgHex = json.optLong("bgHex", 0xFF141218),
                    shapeStyle = style,
                    themeMode = mode,
                    blurIntensity = json.optDouble("blurIntensity", 18.0).toFloat()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class PlaylistItem(
    val name: String,
    val coverUri: String? = null,
    val trackIds: List<Long> = emptyList(),
    val description: String = "",
    val trackFilePaths: List<String> = emptyList()
)

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long,
    val filePath: String = "",
    val folderPath: String = "",
    val folderName: String = "Память",
    val customCoverUri: String? = null,
    val customLyrics: String? = null,
    val dateAdded: Long = 0L
)

data class TrackTagOverride(
    val title: String,
    val artist: String,
    val album: String,
    val coverUri: String? = null,
    val lyrics: String? = null
)

data class LyricWord(
    val timeMs: Long,
    val text: String
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

data class DetailedMetadata(
    val coverArt: Bitmap? = null,
    val bitrateKbps: String = "—",
    val sampleRateStr: String = "44.1 kHz",
    val formatName: String = "AUDIO",
    val lyrics: List<LyricLine> = emptyList()
)

data class DailyStatRecord(
    val artistSeconds: MutableMap<String, Int> = mutableMapOf(),
    val trackPlays: MutableMap<Long, Int> = mutableMapOf()
)