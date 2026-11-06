package com.example.mmp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ShapeStyle(val label: String, val cornerRadius: Int, val isCut: Boolean = false) {
    EXPRESSIVE("Плавный (MD3)", 28),
    PILL("Пилюля", 50),
    SHARP("Острый (Industrial)", 6),
    CYBER("Срезанный", 16, true)
}

data class MMPThemeConfig(
    val themeName: String = "Default Neon",
    val primaryHex: Long = 0xFFFF66C4,
    val secondaryHex: Long = 0xFF7B61FF,
    val bgHex: Long = 0xFF0D0D12,
    val shapeStyle: ShapeStyle = ShapeStyle.EXPRESSIVE,
    val blurIntensity: Float = 16f
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
                MMPThemeConfig(
                    themeName = json.optString("themeName", "Custom Theme"),
                    primaryHex = json.optLong("primaryHex", 0xFFFF66C4),
                    secondaryHex = json.optLong("secondaryHex", 0xFF7B61FF),
                    bgHex = json.optLong("bgHex", 0xFF0D0D12),
                    shapeStyle = ShapeStyle.valueOf(json.optString("shapeStyle", "EXPRESSIVE")),
                    blurIntensity = json.optDouble("blurIntensity", 16.0).toFloat()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

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

            if (duration > 5000) {
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                trackList.add(Track(id, contentUri, title, artist, album, duration, mime, size))
            }
        }
    }
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
