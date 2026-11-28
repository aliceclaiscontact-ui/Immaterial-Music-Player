package com.example.mmp.domain

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
import com.example.mmp.domain.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object CoverCache {
    private val cache = object : LruCache<Long, Bitmap>(50) {}
    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bmp: Bitmap) {
        if (!bmp.isRecycled) cache.put(id, bmp)
    }
    fun remove(id: Long) { cache.remove(id) }
}

fun trackStableKey(track: Track): String = "${track.id}|${track.uri}|${track.filePath}"

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

fun buildTrackFromLocalFile(file: File, fallbackAlbum: String = "Playlists", fallbackCover: String? = null): Track {
    val rawName = file.nameWithoutExtension
    val parts = rawName.split(" - ", limit = 2)
    val artist = if (parts.size == 2) parts[0].trim() else "SoundCloud"
    val title = if (parts.size == 2) parts[1].trim() else rawName
    var durationMs = 0L
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        // Corrupt or temporarily unreadable files are skipped without aborting the library scan.
    } finally {
        try { retriever.release() } catch (_: Throwable) {}
    }
    val stableId = (file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL) + 900_000_000L
    val parent = file.parentFile
    return Track(
        id = stableId,
        uri = Uri.fromFile(file),
        title = title.ifBlank { rawName },
        artist = artist.ifBlank { "SoundCloud" },
        album = parent?.name ?: fallbackAlbum,
        durationMs = durationMs.coerceAtLeast(5000L),
        mimeType = when (file.extension.lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ts" -> "video/mp2t"
            else -> "audio/mpeg"
        },
        sizeBytes = file.length(),
        filePath = file.absolutePath,
        folderPath = parent?.absolutePath ?: "/Music",
        folderName = parent?.name ?: fallbackAlbum,
        customCoverUri = fallbackCover,
        dateAdded = (file.lastModified() / 1000L).coerceAtLeast(0L)
    )
}

fun resolvePlaylistTracks(playlist: PlaylistItem, allTracks: List<Track>): List<Track> {
    val byId = allTracks.associateBy { it.id }
    val byPath = allTracks.filter { it.filePath.isNotBlank() }.associateBy { it.filePath }
    val result = mutableListOf<Track>()
    val seenKeys = mutableSetOf<String>()

    for (id in playlist.trackIds) {
        byId[id]?.let { trk ->
            val key = trk.filePath.ifBlank { trk.id.toString() }
            if (seenKeys.add(key)) result.add(trk)
        }
    }
    for (path in playlist.trackFilePaths) {
        if (path.isBlank()) continue
        val existing = byPath[path]
        if (existing != null) {
            if (seenKeys.add(path)) result.add(existing)
        } else {
            val f = File(path)
            if (f.exists() && f.length() > 1024L && seenKeys.add(path)) {
                result.add(buildTrackFromLocalFile(f, playlist.name, playlist.coverUri))
            }
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
    if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return fallbackHex
    var scaled: Bitmap? = null
    return try {
        // Preserve the source aspect ratio so the algorithm does not artificially
        // amplify edges or large background areas in non-square cover artwork.
        val scale = minOf(64f / bitmap.width, 64f / bitmap.height)
        val sampleWidth = maxOf(1, (bitmap.width * scale).toInt())
        val sampleHeight = maxOf(1, (bitmap.height * scale).toInt())
        val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true).also { scaled = it }
        val binCount = 36 // 10-degree hue buckets.
        val weights = DoubleArray(binCount)
        val hueSin = DoubleArray(binCount)
        val hueCos = DoubleArray(binCount)
        val saturationSum = DoubleArray(binCount)
        val valueSum = DoubleArray(binCount)

        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 160) continue
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1]
                val value = hsv[2]

                // Ignore neutrals, deep shadows and glare. A stronger chroma curve
                // helps vivid purple/blue pigments beat broad, muted brown/beige areas.
                if (saturation < 0.28f || value < 0.16f || value > 0.985f) continue

                val bin = (hsv[0] / 10f).toInt().coerceIn(0, binCount - 1)
                val weight = saturation.toDouble().pow(3.0) * (0.45 + 0.55 * value)
                val radians = Math.toRadians(hsv[0].toDouble())
                weights[bin] += weight
                hueSin[bin] += kotlin.math.sin(radians) * weight
                hueCos[bin] += kotlin.math.cos(radians) * weight
                saturationSum[bin] += saturation * weight
                valueSum[bin] += value * weight
            }
        }

        if (weights.sum() <= 0.0) return fallbackHex

        // Merge neighbouring hue bins to avoid abrupt changes at bucket boundaries.
        val dominant = (0 until binCount).maxByOrNull { index ->
            weights[(index - 1 + binCount) % binCount] +
                weights[index] * 2.2 +
                weights[(index + 1) % binCount]
        } ?: return fallbackHex
        val selected = listOf(
            (dominant - 1 + binCount) % binCount,
            dominant,
            (dominant + 1) % binCount
        )
        val totalWeight = selected.sumOf { weights[it] }
        if (totalWeight <= 0.0) return fallbackHex

        val sinTotal = selected.sumOf { hueSin[it] }
        val cosTotal = selected.sumOf { hueCos[it] }
        val hue = ((Math.toDegrees(kotlin.math.atan2(sinTotal, cosTotal)) + 360.0) % 360.0).toFloat()
        val saturation = (selected.sumOf { saturationSum[it] } / totalWeight).toFloat().coerceIn(0.52f, 0.94f)
        val value = (selected.sumOf { valueSum[it] } / totalWeight).toFloat().coerceIn(0.58f, 0.92f)

        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong() and 0xFFFFFFFFL
    } catch (_: Exception) {
        fallbackHex
    } finally {
        scaled?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
    }
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
