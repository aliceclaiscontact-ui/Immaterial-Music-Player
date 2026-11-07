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
import android.util.LruCache
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ShapeStyle(val label: String) {
    SQUIRCLE("Плавный (MD3)"), PILL("Пилюля"), COOKIE("Клевер"), STAR("Звезда"),
    FLOWER("Цветок"), HEXAGON("Шестиугольник"), HEART("Сердце"), ARCH("Арка"),
    SHARP("Острый"), CYBER("Срезанный")
}

fun ShapeStyle.toCardShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(26.dp)
    ShapeStyle.PILL -> RoundedCornerShape(28.dp)
    ShapeStyle.COOKIE -> RoundedCornerShape(30.dp)
    ShapeStyle.STAR -> RoundedCornerShape(22.dp)
    ShapeStyle.FLOWER -> RoundedCornerShape(28.dp)
    ShapeStyle.HEXAGON -> CutCornerShape(14.dp)
    ShapeStyle.HEART -> RoundedCornerShape(26.dp)
    ShapeStyle.ARCH -> RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    ShapeStyle.SHARP -> RoundedCornerShape(6.dp)
    ShapeStyle.CYBER -> CutCornerShape(16.dp)
}

fun ShapeStyle.toExpressiveShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(28.dp)
    ShapeStyle.PILL -> CircleShape
    ShapeStyle.SHARP -> RoundedCornerShape(8.dp)
    ShapeStyle.CYBER -> CutCornerShape(22)
    ShapeStyle.ARCH -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 14, bottomEndPercent = 14)
    ShapeStyle.HEXAGON -> GenericShape { size, _ ->
        val cx = size.width / 2f; val cy = size.height / 2f; val r = min(cx, cy)
        for (i in 0 until 6) { val angle = i * PI / 3 - PI / 2; val x = (cx + r * cos(angle)).toFloat(); val y = (cy + r * sin(angle)).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    ShapeStyle.STAR -> GenericShape { size, _ ->
        val cx = size.width / 2f; val cy = size.height / 2f; val r = min(cx, cy)
        for (i in 0 until 24) { val angle = i * PI / 12 - PI / 2; val rad = if (i % 2 == 0) r else r * 0.82f; val x = (cx + rad * cos(angle)).toFloat(); val y = (cy + rad * sin(angle)).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    ShapeStyle.COOKIE -> GenericShape { size, _ ->
        val cx = size.width / 2f; val cy = size.height / 2f; val baseR = min(cx, cy) * 0.90f; val amp = min(cx, cy) * 0.10f
        for (i in 0..120) { val t = i * 2 * PI / 120; val rad = baseR + amp * cos(8 * t); val x = (cx + rad * cos(t)).toFloat(); val y = (cy + rad * sin(t)).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    ShapeStyle.FLOWER -> GenericShape { size, _ ->
        val cx = size.width / 2f; val cy = size.height / 2f; val baseR = min(cx, cy) * 0.86f; val amp = min(cx, cy) * 0.14f
        for (i in 0..120) { val t = i * 2 * PI / 120; val rad = baseR + amp * cos(6 * t); val x = (cx + rad * cos(t)).toFloat(); val y = (cy + rad * sin(t)).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    ShapeStyle.HEART -> GenericShape { size, _ ->
        val w = size.width; val h = size.height
        moveTo(w * 0.5f, h * 0.92f); cubicTo(w * 0.08f, h * 0.64f, -w * 0.05f, h * 0.24f, w * 0.26f, h * 0.10f)
        cubicTo(w * 0.42f, h * 0.03f, w * 0.50f, h * 0.18f, w * 0.50f, h * 0.26f); cubicTo(w * 0.50f, h * 0.18f, w * 0.58f, h * 0.03f, w * 0.74f, h * 0.10f)
        cubicTo(w * 1.05f, h * 0.24f, w * 0.92f, h * 0.64f, w * 0.5f, h * 0.92f); close()
    }
}

enum class ThemeMode(val label: String) {
    DARK("Тёмная"), LIGHT("Светлая"), ADAPTIVE_DARK("Adaptive (Тёмная)"), ADAPTIVE_LIGHT("Adaptive (Светлая)")
}

data class MMPThemeConfig(
    val themeName: String = "Expressive Pink", val primaryHex: Long = 0xFFFF66C4, val secondaryHex: Long = 0xFF7B61FF,
    val bgHex: Long = 0xFF0D0D12, val shapeStyle: ShapeStyle = ShapeStyle.SQUIRCLE, val themeMode: ThemeMode = ThemeMode.DARK, val blurIntensity: Float = 18f
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeName", themeName); json.put("primaryHex", primaryHex); json.put("secondaryHex", secondaryHex)
        json.put("bgHex", bgHex); json.put("shapeStyle", shapeStyle.name); json.put("themeMode", themeMode.name)
        json.put("blurIntensity", blurIntensity.toDouble())
        return json.toString(4)
    }
    companion object {
        fun fromJson(jsonString: String): MMPThemeConfig? {
            return try {
                val json = JSONObject(jsonString)
                val style = ShapeStyle.entries.find { it.name == json.optString("shapeStyle") } ?: ShapeStyle.SQUIRCLE
                val mode = ThemeMode.entries.find { it.name == json.optString("themeMode") } ?: ThemeMode.DARK
                MMPThemeConfig(json.optString("themeName", "Custom Theme"), json.optLong("primaryHex", 0xFFFF66C4), json.optLong("secondaryHex", 0xFF7B61FF), json.optLong("bgHex", 0xFF0D0D12), style, mode, json.optDouble("blurIntensity", 18.0).toFloat())
            } catch (e: Exception) { null }
        }
    }
}

data class PlaylistItem(val name: String, val coverUri: String? = null, val trackIds: List<Long> = emptyList())
data class Track(val id: Long, val uri: Uri, val title: String, val artist: String, val album: String, val durationMs: Long, val mimeType: String, val sizeBytes: Long)
data class DetailedMetadata(val coverArt: Bitmap? = null, val bitrateKbps: String = "—", val sampleRateStr: String = "44.1 kHz", val formatName: String = "AUDIO")
data class DailyStatRecord(val artistSeconds: MutableMap<String, Int> = mutableMapOf(), val trackPlays: MutableMap<Long, Int> = mutableMapOf())

object CoverCache {
    private val cache = object : LruCache<Long, Bitmap>(40) {}
    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bmp: Bitmap) { cache.put(id, bmp) }
}

fun extractDominantColor(bitmap: Bitmap?, fallbackHex: Long): Long {
    if (bitmap == null) return fallbackHex
    return try {
        val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        for (x in 0 until 16) {
            for (y in 0 until 16) {
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF; val g = (pixel shr 8) and 0xFF; val b = pixel and 0xFF
                val brightness = (r + g + b) / 3
                if (brightness in 35..235) { rSum += r; gSum += g; bSum += b; count++ }
            }
        }
        if (count == 0L) return fallbackHex
        (0xFFL shl 24) or ((rSum / count).coerceIn(60, 255) shl 16) or ((gSum / count).coerceIn(60, 255) shl 8) or (bSum / count).coerceIn(60, 255)
    } catch (_: Exception) { fallbackHex }
}

object StorageHelper {
    fun getTodayDateString(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    fun getOrInitFirstListenDate(prefs: SharedPreferences): String {
        val existing = prefs.getString("first_listen_date", null)
        if (existing != null) return existing
        val today = getTodayDateString(); prefs.edit().putString("first_listen_date", today).apply(); return today
    }
    fun saveTheme(prefs: SharedPreferences, config: MMPThemeConfig) { prefs.edit().putString("saved_theme_v3", config.toJson()).apply() }
    fun loadTheme(prefs: SharedPreferences): MMPThemeConfig = MMPThemeConfig.fromJson(prefs.getString("saved_theme_v3", null) ?: "") ?: MMPThemeConfig()
    fun saveRecentIds(prefs: SharedPreferences, ids: List<Long>) { val arr = JSONArray(); ids.take(5).forEach { arr.put(it) }; prefs.edit().putString("recent_track_ids", arr.toString()).apply() }
    fun loadRecentIds(prefs: SharedPreferences): List<Long> = try { val arr = JSONArray(prefs.getString("recent_track_ids", "[]")); (0 until arr.length()).map { arr.getLong(it) } } catch (e: Exception) { emptyList() }
    fun saveArtistSeconds(prefs: SharedPreferences, secondsMap: Map<String, Int>) { val obj = JSONObject(); secondsMap.forEach { (k, v) -> obj.put(k, v) }; prefs.edit().putString("artist_listen_seconds", obj.toString()).apply() }
    fun loadArtistSeconds(prefs: SharedPreferences): Map<String, Int> = try { val obj = JSONObject(prefs.getString("artist_listen_seconds", "{}")); val map = mutableMapOf<String, Int>(); obj.keys().forEach { key -> map[key] = obj.optInt(key, 0) }; map } catch (e: Exception) { emptyMap() }
    fun saveTrackPlayCounts(prefs: SharedPreferences, counts: Map<Long, Int>) { val obj = JSONObject(); counts.forEach { (k, v) -> obj.put(k.toString(), v) }; prefs.edit().putString("track_play_counts_all", obj.toString()).apply() }
    fun loadTrackPlayCounts(prefs: SharedPreferences): Map<Long, Int> = try { val obj = JSONObject(prefs.getString("track_play_counts_all", "{}")); val map = mutableMapOf<Long, Int>(); obj.keys().forEach { key -> key.toLongOrNull()?.let { id -> map[id] = obj.optInt(key, 0) } }; map } catch (e: Exception) { emptyMap() }
    fun saveDailyStats(prefs: SharedPreferences, stats: Map<String, DailyStatRecord>) {
        val root = JSONObject()
        stats.forEach { (date, record) ->
            val dateObj = JSONObject(); val artObj = JSONObject(); record.artistSeconds.forEach { (a, s) -> artObj.put(a, s) }
            val trkObj = JSONObject(); record.trackPlays.forEach { (id, c) -> trkObj.put(id.toString(), c) }
            dateObj.put("artists", artObj); dateObj.put("tracks", trkObj); root.put(date, dateObj)
        }
        prefs.edit().putString("daily_history_stats", root.toString()).apply()
    }
    fun loadDailyStats(prefs: SharedPreferences): MutableMap<String, DailyStatRecord> = try {
        val root = JSONObject(prefs.getString("daily_history_stats", "{}")); val result = mutableMapOf<String, DailyStatRecord>()
        root.keys().forEach { date ->
            val dObj = root.getJSONObject(date); val artMap = mutableMapOf<String, Int>(); val trkMap = mutableMapOf<Long, Int>()
            dObj.optJSONObject("artists")?.let { aObj -> aObj.keys().forEach { k -> artMap[k] = aObj.optInt(k, 0) } }
            dObj.optJSONObject("tracks")?.let { tObj -> tObj.keys().forEach { k -> k.toLongOrNull()?.let { id -> trkMap[id] = tObj.optInt(k, 0) } } }
            result[date] = DailyStatRecord(artMap, trkMap)
        }
        result
    } catch (e: Exception) { mutableMapOf() }
    fun savePlaylists(prefs: SharedPreferences, list: List<PlaylistItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject(); obj.put("name", item.name); if (item.coverUri != null) obj.put("coverUri", item.coverUri)
            val idsArr = JSONArray(); item.trackIds.forEach { idsArr.put(it) }; obj.put("trackIds", idsArr); arr.put(obj)
        }
        prefs.edit().putString("user_playlists_v3", arr.toString()).apply()
    }
    fun loadPlaylists(prefs: SharedPreferences): List<PlaylistItem> = try {
        val arr = JSONArray(prefs.getString("user_playlists_v3", "[]"))
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i); val uri = obj.optString("coverUri", "").ifBlank { null }; val idsArr = obj.optJSONArray("trackIds")
            val ids = if (idsArr != null) (0 until idsArr.length()).map { idx -> idsArr.getLong(idx) } else emptyList()
            PlaylistItem(obj.optString("name", "Плейлист"), uri, ids)
        }
    } catch (e: Exception) { emptyList() }
}

suspend fun scanDeviceMusic(context: Context): List<Track> = withContext(Dispatchers.IO) {
    val trackList = mutableListOf<Track>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE)
    try {
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION); val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE); val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol); val duration = cursor.getLong(durCol)
                if (duration > 3000) {
                    trackList.add(Track(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), cursor.getString(titleCol) ?: "Неизвестный трек", cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Неизвестный исполнитель", cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Без альбома", duration, cursor.getString(mimeCol) ?: "audio/mpeg", cursor.getLong(sizeCol)))
                }
            }
        }
    } catch (_: Exception) {}
    trackList
}

fun decodeSampledBitmap(bytes: ByteArray, reqMaxSide: Int = 512): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var inSampleSize = 1
        val maxDim = maxOf(options.outWidth, options.outHeight)
        if (maxDim > reqMaxSide) { val halfDim = maxDim / 2; while ((halfDim / inSampleSize) >= reqMaxSide) inSampleSize *= 2 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { this.inSampleSize = inSampleSize; this.inPreferredConfig = Bitmap.Config.RGB_565 })
    } catch (_: Throwable) { null }
}

suspend fun loadTrackThumbnail(context: Context, track: Track): Bitmap? = withContext(Dispatchers.IO) {
    CoverCache.get(track.id)?.let { return@withContext it }
    val retriever = MediaMetadataRetriever()
    try { retriever.setDataSource(context, track.uri); retriever.embeddedPicture?.let { decodeSampledBitmap(it, 256)?.also { bmp -> CoverCache.put(track.id, bmp) } } } catch (_: Throwable) { null } finally { try { retriever.release() } catch (_: Throwable) {} }
}

suspend fun extractTrackMetadata(context: Context, track: Track): DetailedMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, track.uri)
        val bitmap = retriever.embeddedPicture?.let { decodeSampledBitmap(it, 600)?.also { bmp -> CoverCache.put(track.id, bmp) } }
        val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
        val sampleRateRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull() else null
        val sampleRateStr = if (sampleRateRaw != null && sampleRateRaw > 0) String.format(Locale.US, "%.1f kHz", sampleRateRaw / 1000f) else "44.1 kHz"
        val durationSec = (track.durationMs / 1000L).coerceAtLeast(1L)
        val calcKbps = if (track.sizeBytes > 0) ((track.sizeBytes * 8L) / (durationSec * 1000L)) else 0L
        var kbps = when { rawBitrate >= 64000L -> rawBitrate / 1000L; rawBitrate in 64L..1411L -> rawBitrate; else -> calcKbps }
        if (kbps < 96L && calcKbps >= 128L) kbps = calcKbps
        val format = when { track.mimeType.contains("flac", true) -> "FLAC"; track.mimeType.contains("wav", true) -> "WAV"; track.mimeType.contains("mp4", true) || track.mimeType.contains("m4a", true) -> "M4A"; else -> "MP3" }
        val cleanKbps = when { format == "MP3" && kbps in 290L..340L -> 320L; format == "MP3" && kbps in 235L..275L -> 256L; format == "MP3" && kbps in 175L..210L -> 192L; format == "MP3" && kbps in 115L..145L -> 128L; kbps <= 0L -> if (format == "FLAC" || format == "WAV") 999L else 320L; else -> kbps }
        DetailedMetadata(bitmap, if (cleanKbps == 999L) "Lossless" else "$cleanKbps kbps", sampleRateStr, format)
    } catch (e: Throwable) { DetailedMetadata() } finally { try { retriever.release() } catch (_: Throwable) {} }
}

fun formatMillis(ms: Long): String { val totalSec = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(totalSec / 60, totalSec % 60) }
