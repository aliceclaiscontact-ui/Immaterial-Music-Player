package com.example.mmp

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ShapeStyle(val label: String) {
    SQUIRCLE("Плавный (MD3)"),
    PILL("Пилюля"),
    COOKIE("Клевер (Expressive)"),
    STAR("Звезда (Burst)"),
    HEART("Сердце"),
    SHARP("Острый (Industrial)"),
    CYBER("Срезанный")
}

// Для карточек и плашек (чтобы текст внутри не обрезался фигурой сердца/звезды)
fun ShapeStyle.toCardShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(28.dp)
    ShapeStyle.PILL -> RoundedCornerShape(50)
    ShapeStyle.COOKIE -> RoundedCornerShape(34.dp)
    ShapeStyle.STAR -> RoundedCornerShape(24.dp)
    ShapeStyle.HEART -> RoundedCornerShape(30.dp)
    ShapeStyle.SHARP -> RoundedCornerShape(6.dp)
    ShapeStyle.CYBER -> CutCornerShape(16.dp)
}

// Настоящие фигуры MD3 Expressive для обложек, кнопок Play и превью
fun ShapeStyle.toExpressiveShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(28.dp)
    ShapeStyle.PILL -> CircleShape
    ShapeStyle.SHARP -> RoundedCornerShape(8.dp)
    ShapeStyle.CYBER -> CutCornerShape(22)
    ShapeStyle.STAR -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        val points = 12
        val total = points * 2
        for (i in 0 until total) {
            val angle = i * PI / points - PI / 2
            val rad = if (i % 2 == 0) r else r * 0.82f
            val x = (cx + rad * cos(angle)).toFloat()
            val y = (cy + rad * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    ShapeStyle.COOKIE -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = min(cx, cy) * 0.90f
        val amp = min(cx, cy) * 0.10f
        val steps = 120
        for (i in 0..steps) {
            val t = i * 2 * PI / steps
            val rad = baseR + amp * cos(8 * t)
            val x = (cx + rad * cos(t)).toFloat()
            val y = (cy + rad * sin(t)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    ShapeStyle.HEART -> GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w * 0.5f, h * 0.92f)
        cubicTo(w * 0.08f, h * 0.64f, -w * 0.05f, h * 0.24f, w * 0.26f, h * 0.10f)
        cubicTo(w * 0.42f, h * 0.03f, w * 0.50f, h * 0.18f, w * 0.50f, h * 0.26f)
        cubicTo(w * 0.50f, h * 0.18f, w * 0.58f, h * 0.03f, w * 0.74f, h * 0.10f)
        cubicTo(w * 1.05f, h * 0.24f, w * 0.92f, h * 0.64f, w * 0.5f, h * 0.92f)
        close()
    }
}

data class MMPThemeConfig(
    val themeName: String = "Expressive Pink",
    val primaryHex: Long = 0xFFFF66C4,
    val secondaryHex: Long = 0xFF7B61FF,
    val bgHex: Long = 0xFF0D0D12,
    val shapeStyle: ShapeStyle = ShapeStyle.SQUIRCLE,
    val blurIntensity: Float = 18f
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeName", themeName)
        json.put("primaryHex", primaryHex)
        json.put("secondaryHex", secondaryHex)
        json.put("bgHex", bgHex)
        json.put("shapeStyle", shapeStyle.name)
        json.put("blurIntensity", blurIntensity.toDouble())
        return json.toString(4)
    }

    companion object {
        fun fromJson(jsonString: String): MMPThemeConfig? {
            return try {
                val json = JSONObject(jsonString)
                val styleName = json.optString("shapeStyle", "SQUIRCLE")
                val style = ShapeStyle.entries.find { it.name == styleName } ?: ShapeStyle.SQUIRCLE
                MMPThemeConfig(
                    themeName = json.optString("themeName", "Custom Theme"),
                    primaryHex = json.optLong("primaryHex", 0xFFFF66C4),
                    secondaryHex = json.optLong("secondaryHex", 0xFF7B61FF),
                    bgHex = json.optLong("bgHex", 0xFF0D0D12),
                    shapeStyle = style,
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
    val coverUri: String? = null
)

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long
)

data class DetailedMetadata(
    val coverArt: Bitmap? = null,
    val bitrateKbps: String = "—",
    val formatName: String = "AUDIO"
)

object StorageHelper {
    fun saveTheme(prefs: SharedPreferences, config: MMPThemeConfig) {
        prefs.edit().putString("saved_theme", config.toJson()).apply()
    }

    fun loadTheme(prefs: SharedPreferences): MMPThemeConfig {
        val raw = prefs.getString("saved_theme", null) ?: return MMPThemeConfig()
        return MMPThemeConfig.fromJson(raw) ?: MMPThemeConfig()
    }

    fun saveRecentIds(prefs: SharedPreferences, ids: List<Long>) {
        val arr = JSONArray()
        ids.take(5).forEach { arr.put(it) }
        prefs.edit().putString("recent_track_ids", arr.toString()).apply()
    }

    fun loadRecentIds(prefs: SharedPreferences): List<Long> {
        val raw = prefs.getString("recent_track_ids", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveArtistCounts(prefs: SharedPreferences, counts: Map<String, Int>) {
        val obj = JSONObject()
        counts.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("artist_play_counts", obj.toString()).apply()
    }

    fun loadArtistCounts(prefs: SharedPreferences): Map<String, Int> {
        val raw = prefs.getString("artist_play_counts", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> map[key] = obj.optInt(key, 0) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun savePlaylists(prefs: SharedPreferences, list: List<PlaylistItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            if (item.coverUri != null) obj.put("coverUri", item.coverUri)
            arr.put(obj)
        }
        prefs.edit().putString("saved_playlists_v2", arr.toString()).apply()
    }

    fun loadPlaylists(prefs: SharedPreferences): List<PlaylistItem> {
        val raw = prefs.getString("saved_playlists_v2", null)
            ?: return listOf(PlaylistItem("Hyperpop Essentials"), PlaylistItem("Deconstructed Club"))
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val uri = obj.optString("coverUri", "").ifBlank { null }
                PlaylistItem(obj.optString("name", "Плейлист"), uri)
            }
        } catch (e: Exception) {
            listOf(PlaylistItem("Hyperpop Essentials"))
        }
    }
}

suspend fun scanDeviceMusic(context: Context): List<Track> = withContext(Dispatchers.IO) {
    val trackList = mutableListOf<Track>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.SIZE
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Неизвестный трек"
                val rawArtist = cursor.getString(artistCol) ?: "Неизвестный исполнитель"
                val artist = if (rawArtist == "<unknown>") "Неизвестный исполнитель" else rawArtist
                val rawAlbum = cursor.getString(albumCol) ?: "Без альбома"
                val album = if (rawAlbum == "<unknown>") "Без альбома" else rawAlbum
                val duration = cursor.getLong(durCol)
                val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                val size = cursor.getLong(sizeCol)

                if (duration > 3000) {
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    trackList.add(Track(id, contentUri, title, artist, album, duration, mime, size))
                }
            }
        }
    } catch (_: Exception) {}
    trackList
}

suspend fun extractTrackMetadata(context: Context, track: Track): DetailedMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, track.uri)
        val artBytes = retriever.embeddedPicture
        val bitmap = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null
        val bitrateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        val bitrateStr = if (bitrateRaw != null) "${bitrateRaw / 1000} kbps" else "Lossless"
        val format = when {
            track.mimeType.contains("flac", true) -> "FLAC"
            track.mimeType.contains("wav", true) -> "WAV"
            track.mimeType.contains("mp4", true) || track.mimeType.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }
        DetailedMetadata(bitmap, bitrateStr, format)
    } catch (e: Exception) {
        DetailedMetadata()
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
