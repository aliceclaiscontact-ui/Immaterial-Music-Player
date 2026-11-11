package com.example.mmp

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
    SQUIRCLE("Плавный"),
    COOKIE("Клевер"),
    STAR("Звезда"),
    FLOWER("Цветок"),
    HEXAGON("Шестигранник"),
    HEART("Сердце"),
    ARCH("Арка"),
    SHARP("Острый"),
    CYBER("Срез")
}

fun ShapeStyle.toCardShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(26.dp)
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
    ShapeStyle.SHARP -> RoundedCornerShape(8.dp)
    ShapeStyle.CYBER -> CutCornerShape(22)
    ShapeStyle.ARCH -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 14, bottomEndPercent = 14)
    ShapeStyle.HEXAGON -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        for (i in 0 until 6) {
            val angle = i * PI / 3 - PI / 2
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
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
    ShapeStyle.FLOWER -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = min(cx, cy) * 0.86f
        val amp = min(cx, cy) * 0.14f
        val steps = 120
        for (i in 0..steps) {
            val t = i * 2 * PI / steps
            val rad = baseR + amp * cos(6 * t)
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

enum class ThemeMode(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    ADAPTIVE("Adaptive"),
    ADAPTIVE_LIGHT("Adaptive Светлая")
}

data class MMPThemeConfig(
    val themeName: String = "Expressive Pink",
    val primaryHex: Long = 0xFFFF66C4,
    val secondaryHex: Long = 0xFF7B61FF,
    val bgHex: Long = 0xFF0D0D12,
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
                    themeName = json.optString("themeName", "Custom Theme"),
                    primaryHex = json.optLong("primaryHex", 0xFFFF66C4),
                    secondaryHex = json.optLong("secondaryHex", 0xFF7B61FF),
                    bgHex = json.optLong("bgHex", 0xFF0D0D12),
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
    val customLyrics: String? = null
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

object CoverCache {
    private val cache = object : LruCache<Long, Bitmap>(50) {}
    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bmp: Bitmap) {
        if (!bmp.isRecycled) cache.put(id, bmp)
    }
    fun remove(id: Long) { cache.remove(id) }
}

fun splitArtists(rawArtist: String): List<String> {
    if (rawArtist.isBlank() || rawArtist == "<unknown>") return listOf("Неизвестный исполнитель")
    val delimiters = Regex("""(?i)\s*(?:,|&|;|/|\bfeat\.?|\bft\.?|\bwith\b)\s*""")
    return rawArtist.split(delimiters)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(rawArtist.trim()) }
}

fun getTopArtistsOver25(artistSeconds: Map<String, Int>): List<Pair<String, Int>> {
    val totalSec = artistSeconds.values.sum()
    if (totalSec <= 0) return emptyList()
    return artistSeconds.entries
        .map { it.key to (((it.value.toFloat() / totalSec) * 100).toInt()) }
        .filter { it.second > 25 }
        .sortedByDescending { it.second }
}

fun resolvePlaylistTracks(playlist: PlaylistItem, allTracks: List<Track>): List<Track> {
    val byId = allTracks.associateBy { it.id }
    val byPath = allTracks.filter { it.filePath.isNotBlank() }.associateBy { it.filePath }
    val result = mutableListOf<Track>()
    val seenIds = mutableSetOf<Long>()

    for (id in playlist.trackIds) {
        byId[id]?.let {
            if (seenIds.add(it.id)) result.add(it)
        }
    }
    for (path in playlist.trackFilePaths) {
        byPath[path]?.let {
            if (seenIds.add(it.id)) result.add(it)
        }
    }
    return result
}

suspend fun shareTrackCardImage(
    context: Context,
    track: Track,
    metadata: DetailedMetadata,
    primaryColorInt: Int
) = withContext(Dispatchers.IO) {
    try {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF0D0D14.toInt(), primaryColorInt, 0xFF141422.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xD912121C.toInt()
        }
        val cardRect = RectF(80f, 120f, w - 80f, h - 120f)
        canvas.drawRoundRect(cardRect, 64f, 64f, cardPaint)

        val artSize = 760
        val artLeft = (w - artSize) / 2f
        val artTop = 200f
        val artBmp = metadata.coverArt?.takeIf { !it.isRecycled }
        if (artBmp != null) {
            val scaled = Bitmap.createScaledBitmap(artBmp, artSize, artSize, true)
            val saveCount = canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), 48f, 48f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(scaled, artLeft, artTop, null)
            canvas.restoreToCount(saveCount)
        } else {
            val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColorInt }
            canvas.drawRoundRect(RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), 48f, 48f, phPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val safeTitle = if (track.title.length > 26) track.title.take(25) + "…" else track.title
        canvas.drawText(safeTitle, 160f, 1050f, titlePaint)

        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 40f
        }
        val safeArtist = if (track.artist.length > 32) track.artist.take(31) + "…" else track.artist
        canvas.drawText(safeArtist, 160f, 1115f, artistPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColorInt
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Imma3 • ${metadata.formatName} • ${metadata.bitrateKbps}", 160f, 1185f, badgePaint)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Imma3_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Imma3")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Слушаю «${track.title}» — ${track.artist} в Imma3")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Поделиться карточкой трека").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    } catch (_: Throwable) {}
}

fun extractDominantColor(bitmap: Bitmap?, fallbackHex: Long): Long {
    if (bitmap == null || bitmap.isRecycled) return fallbackHex
    return try {
        val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        for (x in 0 until 16) {
            for (y in 0 until 16) {
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3
                if (brightness in 35..235) {
                    rSum += r; gSum += g; bSum += b; count++
                }
            }
        }
        if (count == 0L) return fallbackHex
        val rAvg = (rSum / count).coerceIn(60, 255)
        val gAvg = (gSum / count).coerceIn(60, 255)
        val bAvg = (bSum / count).coerceIn(60, 255)
        (0xFFL shl 24) or (rAvg shl 16) or (gAvg shl 8) or bAvg
    } catch (_: Exception) { fallbackHex }
}

fun parseLrcText(raw: String): List<LyricLine> {
    if (raw.isBlank()) return emptyList()
    val lines = mutableListOf<LyricLine>()
    val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")
    val wordRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)""")

    for (line in raw.lines()) {
        val match = lineRegex.find(line.trim())
        if (match != null) {
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val ms = when (fracStr.length) {
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val content = match.groupValues[4].trim()
            val lineTime = min * 60000L + sec * 1000L + ms

            val words = mutableListOf<LyricWord>()
            val wordMatches = wordRegex.findAll(content).toList()
            if (wordMatches.isNotEmpty()) {
                for (wm in wordMatches) {
                    val wMin = wm.groupValues[1].toLongOrNull() ?: 0L
                    val wSec = wm.groupValues[2].toLongOrNull() ?: 0L
                    val wFrac = wm.groupValues[3]
                    val wMs = when (wFrac.length) {
                        1 -> (wFrac.toLongOrNull() ?: 0L) * 100L
                        2 -> (wFrac.toLongOrNull() ?: 0L) * 10L
                        3 -> wFrac.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val wTxt = wm.groupValues[4]
                    words.add(LyricWord(wMin * 60000L + wSec * 1000L + wMs, wTxt))
                }
            }

            val cleanText = if (words.isNotEmpty()) content.replace(Regex("""<[^>]+>"""), "") else content
            if (cleanText.isNotBlank()) {
                lines.add(LyricLine(lineTime, cleanText, words))
            }
        }
    }
    if (lines.isNotEmpty()) return lines.sortedBy { it.timeMs }
    return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { idx, s ->
        LyricLine(idx * 4000L, s)
    }
}

fun getOfflineLyricsFile(context: Context, artist: String, title: String): File {
    val dir = File(context.filesDir, "lyrics_offline")
    if (!dir.exists()) dir.mkdirs()
    val clean = "${artist}_$title".replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(60)
    return File(dir, "$clean.lrc")
}

fun saveOfflineLyrics(context: Context, artist: String, title: String, lrcText: String) {
    try {
        val f = getOfflineLyricsFile(context, artist, title)
        f.writeText(lrcText)
    } catch (_: Throwable) {}
}

fun loadOfflineLyrics(context: Context, artist: String, title: String): String? {
    return try {
        val f = getOfflineLyricsFile(context, artist, title)
        if (f.exists() && f.canRead()) f.readText() else null
    } catch (_: Throwable) { null }
}

object StorageHelper {
    fun getTodayDateString(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    fun checkAndResetYearlyStats(prefs: SharedPreferences) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val savedYear = prefs.getInt("stats_current_year", currentYear)
        if (currentYear > savedYear) {
            prefs.edit()
                .putInt("stats_current_year", currentYear)
                .putInt("listened_seconds_total", 0)
                .remove("artist_listen_seconds")
                .remove("track_play_counts_all")
                .apply()
        } else if (!prefs.contains("stats_current_year")) {
            prefs.edit().putInt("stats_current_year", currentYear).apply()
        }
    }

    fun getOrInitFirstLaunchDate(context: Context, prefs: SharedPreferences): String {
        checkAndResetYearlyStats(prefs)
        val existing = prefs.getString("first_launch_date_v2", null)
        if (!existing.isNullOrBlank()) return existing

        val dateStr = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = pkgInfo.firstInstallTime
            if (installTime > 0L) {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(installTime))
            } else {
                getTodayDateString()
            }
        } catch (_: Throwable) {
            getTodayDateString()
        }
        prefs.edit().putString("first_launch_date_v2", dateStr).apply()
        return dateStr
    }

    fun saveLastPlayedState(prefs: SharedPreferences, track: Track, positionMs: Long) {
        val obj = JSONObject().apply {
            put("id", track.id)
            put("uri", track.uri.toString())
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationMs", track.durationMs)
            put("mimeType", track.mimeType)
            put("sizeBytes", track.sizeBytes)
            put("filePath", track.filePath)
            put("folderPath", track.folderPath)
            put("folderName", track.folderName)
            if (track.customCoverUri != null) put("customCoverUri", track.customCoverUri)
            if (track.customLyrics != null) put("customLyrics", track.customLyrics)
            put("positionMs", positionMs)
        }
        prefs.edit().putString("last_played_track_state", obj.toString()).apply()
    }

    fun loadLastPlayedState(prefs: SharedPreferences): Pair<Track, Long>? {
        val raw = prefs.getString("last_played_track_state", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val id = obj.getLong("id")
            val uriStr = obj.getString("uri")
            val trk = Track(
                id = id,
                uri = Uri.parse(uriStr),
                title = obj.optString("title", "Неизвестный трек"),
                artist = obj.optString("artist", "Неизвестный исполнитель"),
                album = obj.optString("album", "Без альбома"),
                durationMs = obj.optLong("durationMs", 0L),
                mimeType = obj.optString("mimeType", "audio/mpeg"),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                filePath = obj.optString("filePath", ""),
                folderPath = obj.optString("folderPath", ""),
                folderName = obj.optString("folderName", "Память"),
                customCoverUri = obj.optString("customCoverUri", "").ifBlank { null },
                customLyrics = obj.optString("customLyrics", "").ifBlank { null }
            )
            val pos = obj.optLong("positionMs", 0L)
            trk to pos
        } catch (_: Throwable) {
            null
        }
    }

    fun saveLastQueue(prefs: SharedPreferences, queue: List<Track>) {
        val arr = JSONArray()
        queue.take(150).forEach { track ->
            val obj = JSONObject().apply {
                put("id", track.id)
                put("uri", track.uri.toString())
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs)
                put("mimeType", track.mimeType)
                put("sizeBytes", track.sizeBytes)
                put("filePath", track.filePath)
                put("folderPath", track.folderPath)
                put("folderName", track.folderName)
                if (track.customCoverUri != null) put("customCoverUri", track.customCoverUri)
                if (track.customLyrics != null) put("customLyrics", track.customLyrics)
            }
            arr.put(obj)
        }
        prefs.edit().putString("last_saved_queue_v1", arr.toString()).apply()
    }

    fun loadLastQueue(prefs: SharedPreferences): List<Track> {
        val raw = prefs.getString("last_saved_queue_v1", null) ?: return emptyList()
        val list = mutableListOf<Track>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.optLong("id"),
                        uri = Uri.parse(obj.optString("uri")),
                        title = obj.optString("title", "Неизвестный трек"),
                        artist = obj.optString("artist", "Неизвестный исполнитель"),
                        album = obj.optString("album", "Без альбома"),
                        durationMs = obj.optLong("durationMs", 0L),
                        mimeType = obj.optString("mimeType", "audio/mpeg"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        filePath = obj.optString("filePath", ""),
                        folderPath = obj.optString("folderPath", ""),
                        folderName = obj.optString("folderName", "Память"),
                        customCoverUri = obj.optString("customCoverUri", "").ifBlank { null },
                        customLyrics = obj.optString("customLyrics", "").ifBlank { null }
                    )
                )
            }
        } catch (_: Throwable) {}
        return list
    }

    fun saveTheme(prefs: SharedPreferences, config: MMPThemeConfig) {
        prefs.edit().putString("saved_theme_v3", config.toJson()).apply()
    }

    fun loadTheme(prefs: SharedPreferences): MMPThemeConfig {
        return MMPThemeConfig.fromJson(prefs.getString("saved_theme_v3", null) ?: "") ?: MMPThemeConfig()
    }

    fun saveMinDurationSec(prefs: SharedPreferences, sec: Int) {
        prefs.edit().putInt("min_track_duration_sec", sec.coerceIn(5, 60)).apply()
    }

    fun loadMinDurationSec(prefs: SharedPreferences): Int {
        return prefs.getInt("min_track_duration_sec", 10).coerceIn(5, 60)
    }

    fun saveExcludedFolders(prefs: SharedPreferences, folders: Set<String>) {
        val arr = JSONArray()
        folders.forEach { arr.put(it) }
        prefs.edit().putString("excluded_folders_json", arr.toString()).apply()
    }

    fun loadExcludedFolders(prefs: SharedPreferences): Set<String> {
        return try {
            val arr = JSONArray(prefs.getString("excluded_folders_json", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun saveTagOverrides(prefs: SharedPreferences, map: Map<Long, TrackTagOverride>) {
        val root = JSONObject()
        map.forEach { (id, ov) ->
            val obj = JSONObject()
            obj.put("title", ov.title)
            obj.put("artist", ov.artist)
            obj.put("album", ov.album)
            if (ov.coverUri != null) obj.put("coverUri", ov.coverUri)
            if (ov.lyrics != null) obj.put("lyrics", ov.lyrics)
            root.put(id.toString(), obj)
        }
        prefs.edit().putString("track_tag_overrides", root.toString()).apply()
    }

    fun loadTagOverrides(prefs: SharedPreferences): Map<Long, TrackTagOverride> {
        return try {
            val root = JSONObject(prefs.getString("track_tag_overrides", "{}") ?: "{}")
            val map = mutableMapOf<Long, TrackTagOverride>()
            root.keys().forEach { k ->
                k.toLongOrNull()?.let { id ->
                    val obj = root.getJSONObject(k)
                    map[id] = TrackTagOverride(
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        album = obj.optString("album", ""),
                        coverUri = obj.optString("coverUri", "").ifBlank { null },
                        lyrics = obj.optString("lyrics", "").ifBlank { null }
                    )
                }
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun saveTrackPlayCounts(prefs: SharedPreferences, counts: Map<Long, Int>) {
        val obj = JSONObject()
        counts.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString("track_play_counts_all", obj.toString()).apply()
    }

    fun loadTrackPlayCounts(prefs: SharedPreferences): Map<Long, Int> {
        return try {
            val obj = JSONObject(prefs.getString("track_play_counts_all", "{}") ?: "{}")
            val map = mutableMapOf<Long, Int>()
            obj.keys().forEach { key ->
                key.toLongOrNull()?.let { id -> map[id] = obj.optInt(key, 0) }
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun saveArtistSeconds(prefs: SharedPreferences, secondsMap: Map<String, Int>) {
        val obj = JSONObject()
        secondsMap.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("artist_listen_seconds", obj.toString()).apply()
    }

    fun loadArtistSeconds(prefs: SharedPreferences): Map<String, Int> {
        return try {
            val obj = JSONObject(prefs.getString("artist_listen_seconds", "{}") ?: "{}")
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> map[key] = obj.optInt(key, 0) }
            map
        } catch (e: Exception) { emptyMap() }
    }

    fun saveDailyStats(prefs: SharedPreferences, stats: Map<String, DailyStatRecord>) {
        val root = JSONObject()
        stats.forEach { (date, record) ->
            val dateObj = JSONObject()
            val artObj = JSONObject()
            record.artistSeconds.forEach { (a, s) -> artObj.put(a, s) }
            val trkObj = JSONObject()
            record.trackPlays.forEach { (id, c) -> trkObj.put(id.toString(), c) }
            dateObj.put("artists", artObj)
            dateObj.put("tracks", trkObj)
            root.put(date, dateObj)
        }
        prefs.edit().putString("daily_history_stats", root.toString()).apply()
    }

    fun loadDailyStats(prefs: SharedPreferences): MutableMap<String, DailyStatRecord> {
        return try {
            val root = JSONObject(prefs.getString("daily_history_stats", "{}") ?: "{}")
            val result = mutableMapOf<String, DailyStatRecord>()
            root.keys().forEach { date ->
                val dObj = root.getJSONObject(date)
                val artMap = mutableMapOf<String, Int>()
                val trkMap = mutableMapOf<Long, Int>()
                dObj.optJSONObject("artists")?.let { aObj -> aObj.keys().forEach { k -> artMap[k] = aObj.optInt(k, 0) } }
                dObj.optJSONObject("tracks")?.let { tObj -> tObj.keys().forEach { k -> k.toLongOrNull()?.let { id -> trkMap[id] = tObj.optInt(k, 0) } } }
                result[date] = DailyStatRecord(artMap, trkMap)
            }
            result
        } catch (e: Exception) { mutableMapOf() }
    }

    fun savePlaylists(prefs: SharedPreferences, list: List<PlaylistItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            if (item.coverUri != null) obj.put("coverUri", item.coverUri)
            obj.put("description", item.description)
            val idsArr = JSONArray()
            item.trackIds.forEach { idsArr.put(it) }
            obj.put("trackIds", idsArr)
            val pathsArr = JSONArray()
            item.trackFilePaths.forEach { pathsArr.put(it) }
            obj.put("trackFilePaths", pathsArr)
            arr.put(obj)
        }
        prefs.edit().putString("user_playlists_v3", arr.toString()).apply()
    }

    fun loadPlaylists(prefs: SharedPreferences): List<PlaylistItem> {
        return try {
            val arr = JSONArray(prefs.getString("user_playlists_v3", "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val uri = obj.optString("coverUri", "").ifBlank { null }
                val desc = obj.optString("description", "")
                val idsArr = obj.optJSONArray("trackIds")
                val ids = if (idsArr != null) (0 until idsArr.length()).map { idx -> idsArr.getLong(idx) } else emptyList()
                val pathsArr = obj.optJSONArray("trackFilePaths")
                val paths = if (pathsArr != null) (0 until pathsArr.length()).map { idx -> pathsArr.getString(idx) } else emptyList()
                PlaylistItem(obj.optString("name", "Плейлист"), uri, ids, desc, paths)
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveSoundCloudTracks(prefs: SharedPreferences, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("uri", t.uri.toString())
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("album", t.album)
            obj.put("durationMs", t.durationMs)
            obj.put("customCoverUri", t.customCoverUri ?: "")
            arr.put(obj)
        }
        prefs.edit().putString("saved_sc_tracks_v1", arr.toString()).apply()
    }

    fun loadSoundCloudTracks(prefs: SharedPreferences): List<Track> {
        val raw = prefs.getString("saved_sc_tracks_v1", null) ?: return emptyList()
        val list = mutableListOf<Track>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = o.optLong("id"),
                        uri = Uri.parse(o.optString("uri")),
                        title = o.optString("title", "SoundCloud Track"),
                        artist = o.optString("artist", "SoundCloud"),
                        album = o.optString("album", "SoundCloud"),
                        durationMs = o.optLong("durationMs", 0L),
                        mimeType = "audio/mpeg",
                        sizeBytes = 0L,
                        filePath = o.optString("uri"),
                        folderPath = "SoundCloud",
                        folderName = "SoundCloud",
                        customCoverUri = o.optString("customCoverUri").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Throwable) {}
        return list
    }
}

fun applyOverridesToTrack(track: Track, overrides: Map<Long, TrackTagOverride>): Track {
    val ov = overrides[track.id] ?: return track
    return track.copy(
        title = ov.title.ifBlank { track.title },
        artist = ov.artist.ifBlank { track.artist },
        album = ov.album.ifBlank { track.album },
        customCoverUri = ov.coverUri ?: track.customCoverUri,
        customLyrics = ov.lyrics ?: track.customLyrics
    )
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
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATA
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
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Неизвестный трек"
                val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Неизвестный исполнитель"
                val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Без альбома"
                val duration = cursor.getLong(durCol)
                val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                val size = cursor.getLong(sizeCol)
                val path = if (dataCol != -1) (cursor.getString(dataCol) ?: "") else ""
                val parentFile = if (path.isNotBlank()) File(path).parentFile else null
                val folderPath = parentFile?.absolutePath ?: "/Music"
                val folderName = parentFile?.name ?: "Music"

                if (duration >= 5000L) {
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    trackList.add(Track(id, contentUri, title, artist, album, duration, mime, size, path, folderPath, folderName))
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
        if (maxDim > reqMaxSide) {
            val halfDim = maxDim / 2
            while ((halfDim / inSampleSize) >= reqMaxSide) inSampleSize *= 2
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        })
    } catch (_: Throwable) { null }
}

suspend fun loadBitmapFromUriOrHttp(context: Context, uriStr: String, maxSide: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            val conn = (URL(uriStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.inputStream.use { s -> decodeSampledBitmap(s.readBytes(), maxSide) }
        } else {
            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { s ->
                decodeSampledBitmap(s.readBytes(), maxSide)
            }
        }
    } catch (_: Throwable) { null }
}

suspend fun loadTrackThumbnail(context: Context, track: Track): Bitmap? = withContext(Dispatchers.IO) {
    CoverCache.get(track.id)?.takeIf { !it.isRecycled }?.let { return@withContext it }
    if (track.customCoverUri != null) {
        loadBitmapFromUriOrHttp(context, track.customCoverUri, 256)?.also { CoverCache.put(track.id, it) }?.let { return@withContext it }
    }
    if (track.uri.scheme?.startsWith("http") == true) return@withContext null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, track.uri)
        retriever.embeddedPicture?.let { decodeSampledBitmap(it, 256)?.also { bmp -> CoverCache.put(track.id, bmp) } }
    } catch (_: Throwable) { null } finally { try { retriever.release() } catch (_: Throwable) {} }
}

suspend fun extractTrackMetadata(context: Context, track: Track): DetailedMetadata = withContext(Dispatchers.IO) {
    val isHttpStream = track.uri.scheme?.startsWith("http") == true
    val retriever = MediaMetadataRetriever()
    try {
        var bitmap: Bitmap? = null
        if (track.customCoverUri != null) {
            bitmap = loadBitmapFromUriOrHttp(context, track.customCoverUri, 600)?.also { CoverCache.put(track.id, it) }
        }

        if (!isHttpStream) {
            retriever.setDataSource(context, track.uri)
            if (bitmap == null) {
                val artBytes = retriever.embeddedPicture
                bitmap = if (artBytes != null) decodeSampledBitmap(artBytes, 600)?.also { CoverCache.put(track.id, it) } else null
            }
        }

        val rawBitrate = if (!isHttpStream) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L else 128000L
        val sampleRateRaw = if (!isHttpStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull() else null
        val sampleRateStr = if (sampleRateRaw != null && sampleRateRaw > 0) String.format(Locale.US, "%.1f kHz", sampleRateRaw / 1000f) else "44.1 kHz"

        val durationSec = (track.durationMs / 1000L).coerceAtLeast(1L)
        val calculatedFromSizeKbps = if (track.sizeBytes > 0) ((track.sizeBytes * 8L) / (durationSec * 1000L)) else 0L
        var kbps = when {
            rawBitrate >= 64000L -> rawBitrate / 1000L
            rawBitrate in 64L..1411L -> rawBitrate
            else -> calculatedFromSizeKbps
        }
        if (kbps < 96L && calculatedFromSizeKbps >= 128L) kbps = calculatedFromSizeKbps
        val format = when {
            isHttpStream -> "SC STREAM"
            track.mimeType.contains("flac", true) -> "FLAC"
            track.mimeType.contains("wav", true) -> "WAV"
            track.mimeType.contains("mp4", true) || track.mimeType.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }
        val cleanKbps = when {
            isHttpStream -> 128L
            format == "MP3" && kbps in 290L..340L -> 320L
            format == "MP3" && kbps in 235L..275L -> 256L
            format == "MP3" && kbps in 175L..210L -> 192L
            format == "MP3" && kbps in 115L..145L -> 128L
            kbps <= 0L -> if (format == "FLAC" || format == "WAV") 999L else 320L
            else -> kbps
        }

        var lyricsList = emptyList<LyricLine>()
        if (!track.customLyrics.isNullOrBlank()) {
            lyricsList = parseLrcText(track.customLyrics)
        } else {
            val offlineSaved = loadOfflineLyrics(context, track.artist, track.title)
            if (!offlineSaved.isNullOrBlank()) {
                lyricsList = parseLrcText(offlineSaved)
            } else if (track.filePath.isNotBlank()) {
                try {
                    val audioFile = File(track.filePath)
                    val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
                    if (lrcFile.exists() && lrcFile.canRead()) {
                        lyricsList = parseLrcText(lrcFile.readText())
                    }
                } catch (_: Throwable) {}
            }
        }

        DetailedMetadata(bitmap, if (cleanKbps == 999L) "Lossless" else "$cleanKbps kbps", sampleRateStr, format, lyricsList)
    } catch (e: Throwable) { DetailedMetadata() } finally { try { retriever.release() } catch (_: Throwable) {} }
}

fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

suspend fun fetchLyricsFromLrclib(
    artist: String,
    title: String,
    durationMs: Long,
    customQuery: String? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = if (artist.contains("Неизвестный", ignoreCase = true)) "" else artist.trim()
        val targetSec = (durationMs / 1000L).coerceAtLeast(1L)

        if (customQuery.isNullOrBlank() && cleanArtist.isNotEmpty()) {
            val exactUrl = "https://lrclib.net/api/get?artist_name=" +
                URLEncoder.encode(cleanArtist, "UTF-8") +
                "&track_name=" + URLEncoder.encode(title.trim(), "UTF-8") +
                "&duration=$targetSec"
            val conn = (URL(exactUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "MMP-Android/2.6")
            }
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(resp)
                val synced = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
                val plain = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
                if (synced != null) return@withContext synced
                if (plain != null) return@withContext plain
            }
        }

        val query = customQuery?.takeIf { it.isNotBlank() } ?: "$cleanArtist $title".trim()
        val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")
        val searchConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MMP-Android/2.6")
        }
        if (searchConn.responseCode == 200) {
            val resp = searchConn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(resp)
            var bestSynced: String? = null
            var bestPlain: String? = null
            var minDiff = Long.MAX_VALUE

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val dur = item.optLong("duration", 0L)
                val diff = abs(dur - targetSec)
                val synced = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
                val plain = item.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }

                if (diff < minDiff && diff <= 3) {
                    minDiff = diff
                    if (synced != null) bestSynced = synced
                    if (plain != null) bestPlain = plain
                }
            }
            if (bestSynced != null) return@withContext bestSynced
            if (bestPlain != null) return@withContext bestPlain
        }
        null
    } catch (_: Throwable) {
        null
    }
}
