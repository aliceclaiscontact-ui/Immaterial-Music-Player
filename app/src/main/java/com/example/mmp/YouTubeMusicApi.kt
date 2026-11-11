package com.example.mmp

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YtMusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long
)

object YouTubeMusicClient {
    private val streamCache = mutableMapOf<String, String>()
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.reallyaweso.me",
        "https://api.piped.private.coffee"
    )

    fun clearCache() {
        streamCache.clear()
    }

    private fun httpPostJson(urlStr: String, bodyJson: String, clientName: String = "WEB_REMIX", clientVersion: String = "1.20260101.01.00"): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            setRequestProperty("X-YouTube-Client-Name", if (clientName == "ANDROID_MUSIC") "21" else "67")
            setRequestProperty("X-YouTube-Client-Version", clientVersion)
            setRequestProperty("Origin", "https://music.youtube.com")
        }
        conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("YT_HTTP_${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP_${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<YtMusicTrack> = withContext(Dispatchers.IO) {
        // 1. Попытка через InnerTube WEB_REMIX (YouTube Music)
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20260101.01.00")
                        put("hl", "ru")
                        put("gl", "US")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D") // Фильтр "Песни"
            }
            val resp = httpPostJson("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", payload.toString())
            val parsed = parseInnerTubeSearch(resp)
            if (parsed.isNotEmpty()) return@withContext parsed.take(limit)
        } catch (_: Throwable) {}

        // 2. Резервный поиск через Piped API (music_songs)
        val enc = URLEncoder.encode(query, "UTF-8")
        for (instance in pipedInstances) {
            try {
                val resp = httpGet("$instance/search?q=$enc&filter=music_songs")
                val root = JSONObject(resp)
                val items = root.optJSONArray("items") ?: continue
                val list = mutableListOf<YtMusicTrack>()
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val urlPart = o.optString("url", "")
                    val vid = urlPart.substringAfter("v=", "").substringBefore("&")
                    if (vid.isBlank()) continue
                    val title = o.optString("title", "Без названия")
                    val artist = o.optString("uploaderName", "YouTube Music").removeSuffix(" - Topic")
                    val thumb = o.optString("thumbnail", "").takeIf { it.isNotBlank() }
                    val durSec = o.optLong("duration", 0L)
                    list.add(YtMusicTrack(vid, title, artist, thumb, durSec * 1000L))
                }
                if (list.isNotEmpty()) return@withContext list.take(limit)
            } catch (_: Throwable) {}
        }
        emptyList()
    }

    private fun parseInnerTubeSearch(jsonStr: String): List<YtMusicTrack> {
        val results = mutableListOf<YtMusicTrack>()
        val seenIds = mutableSetOf<String>()
        try {
            // Рекурсивный поиск объектов musicResponsiveListItemRenderer
            val root = JSONObject(jsonStr)
            findMusicItemsRecursive(root, results, seenIds)
        } catch (_: Throwable) {}
        return results
    }

    private fun findMusicItemsRecursive(node: Any?, out: MutableList<YtMusicTrack>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                if (node.has("musicResponsiveListItemRenderer")) {
                    val renderer = node.getJSONObject("musicResponsiveListItemRenderer")
                    parseResponsiveItem(renderer)?.let {
                        if (seen.add(it.videoId)) out.add(it)
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    findMusicItemsRecursive(node.opt(k), out, seen)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findMusicItemsRecursive(node.opt(i), out, seen)
                }
            }
        }
    }

    private fun parseResponsiveItem(renderer: JSONObject): YtMusicTrack? {
        val playlistData = renderer.optJSONObject("playlistItemData")
        val videoId = playlistData?.optString("videoId", "")?.takeIf { it.isNotBlank() } ?: return null

        val flexCols = renderer.optJSONArray("flexColumns") ?: return null
        var title = "Без названия"
        var artist = "YouTube Music"
        var durationMs = 0L

        if (flexCols.length() > 0) {
            val col0 = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val runs0 = col0?.optJSONObject("text")?.optJSONArray("runs")
            if (runs0 != null && runs0.length() > 0) {
                title = runs0.optJSONObject(0)?.optString("text", title) ?: title
            }
        }
        if (flexCols.length() > 1) {
            val col1 = flexCols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val runs1 = col1?.optJSONObject("text")?.optJSONArray("runs")
            if (runs1 != null && runs1.length() > 0) {
                val parts = mutableListOf<String>()
                for (i in 0 until runs1.length()) {
                    val txt = runs1.optJSONObject(i)?.optString("text", "")?.trim() ?: ""
                    if (txt.isNotBlank() && txt != "•" && txt != "Песня" && txt != "Видео") {
                        parts.add(txt)
                    }
                }
                if (parts.isNotEmpty()) artist = parts.first()
                val lastPart = parts.lastOrNull() ?: ""
                if (lastPart.contains(":")) {
                    val split = lastPart.split(":")
                    if (split.size == 2) {
                        val m = split[0].toLongOrNull() ?: 0L
                        val s = split[1].toLongOrNull() ?: 0L
                        durationMs = (m * 60 + s) * 1000L
                    }
                }
            }
        }

        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        var bestThumb: String? = null
        if (thumbs != null && thumbs.length() > 0) {
            bestThumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "")
                ?.replace("w60-h60", "w544-h544")
                ?.replace("w120-h120", "w544-h544")
        }

        return YtMusicTrack(videoId, title, artist, bestThumb, durationMs)
    }

    suspend fun resolveAudioStreamUrl(videoId: String, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            streamCache[videoId]?.let { return@withContext it }
        }

        // 1. InnerTube ANDROID_MUSIC player (отдает прямые m4a/opus потоки без шифрования подписи)
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "7.16.53")
                        put("androidSdkVersion", 34)
                        put("hl", "ru")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val resp = httpPostJson(
                "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
                payload.toString(),
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.16.53"
            )
            val root = JSONObject(resp)
            val streamingData = root.optJSONObject("streamingData")
            val adaptive = streamingData?.optJSONArray("adaptiveFormats")
            var bestUrl: String? = null
            var bestBitrate = 0

            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val mime = fmt.optString("mimeType", "")
                    if (mime.startsWith("audio/")) {
                        val url = fmt.optString("url", "")
                        val bitrate = fmt.optInt("bitrate", 0)
                        if (url.isNotBlank() && bitrate >= bestBitrate) {
                            bestBitrate = bitrate
                            bestUrl = url
                        }
                    }
                }
            }
            if (!bestUrl.isNullOrBlank()) {
                streamCache[videoId] = bestUrl
                return@withContext bestUrl
            }
        } catch (_: Throwable) {}

        // 2. Резерв через Piped API /streams/{videoId}
        for (instance in pipedInstances) {
            try {
                val resp = httpGet("$instance/streams/$videoId")
                val root = JSONObject(resp)
                val audioStreams = root.optJSONArray("audioStreams") ?: continue
                var bestUrl: String? = null
                var bestBitrate = 0
                for (i in 0 until audioStreams.length()) {
                    val st = audioStreams.optJSONObject(i) ?: continue
                    val url = st.optString("url", "")
                    val bitrate = st.optInt("bitrate", 0)
                    if (url.isNotBlank() && bitrate >= bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
                if (!bestUrl.isNullOrBlank()) {
                    streamCache[videoId] = bestUrl
                    return@withContext bestUrl
                }
            } catch (_: Throwable) {}
        }
        null
    }

    suspend fun downloadTrackToDevice(
        context: Context,
        ytTrack: YtMusicTrack
    ): File? = withContext(Dispatchers.IO) {
        try {
            val streamUrl = resolveAudioStreamUrl(ytTrack.videoId, forceRefresh = true) ?: return@withContext null
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Imma3_YTMusic")
            if (!baseDir.exists()) baseDir.mkdirs()

            val cleanArtist = ytTrack.artist.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(30)
            val cleanTitle = ytTrack.title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(40)
            val fileName = "$cleanArtist - $cleanTitle".trim().ifBlank { "yt_${ytTrack.videoId}" }
            val outFile = File(baseDir, "$fileName.m4a")

            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "com.google.android.apps.youtube.music/7.16.53 (Linux; U; Android 14)")
            }
            FileOutputStream(outFile).use { out ->
                conn.inputStream.use { inp -> inp.copyTo(out) }
            }

            if (!outFile.exists() || outFile.length() < 2048L) {
                outFile.delete()
                return@withContext null
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("audio/mp4"),
                null
            )
            outFile
        } catch (_: Throwable) {
            null
        }
    }

    fun toTrack(ytTrack: YtMusicTrack, directUrl: String): Track {
        return Track(
            id = ytTrack.videoId.hashCode().toLong(),
            uri = Uri.parse(directUrl),
            title = ytTrack.title,
            artist = ytTrack.artist,
            album = "YouTube Music",
            durationMs = ytTrack.durationMs,
            mimeType = "audio/mp4",
            sizeBytes = 0L,
            filePath = "ytm://${ytTrack.videoId}",
            folderPath = "YouTube Music",
            folderName = "YouTube Music",
            customCoverUri = ytTrack.artworkUrl
        )
    }
}
