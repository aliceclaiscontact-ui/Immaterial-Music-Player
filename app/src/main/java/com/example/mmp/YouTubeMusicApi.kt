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
    const val STREAM_USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) Cronet/132.0.6808.3"
    private val streamCache = mutableMapOf<String, String>()
    private var cachedVisitorData: String? = null

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee"
    )

    fun clearCache() {
        streamCache.clear()
        cachedVisitorData = null
    }

    private fun getVisitorData(): String {
        cachedVisitorData?.let { return it }
        try {
            val conn = (URL("https://music.youtube.com").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val match = Regex(""""VISITOR_DATA":"([^"]+)"""").find(html)
            if (match != null) {
                val vd = match.groupValues[1]
                cachedVisitorData = vd
                return vd
            }
        } catch (_: Throwable) {}
        return "Cgt0VjRfX19fX19fXyjh9_65BjIKCgJVUxIEGgAgSw%3D%3D"
    }

    private fun httpPostJson(
        urlStr: String,
        bodyJson: String,
        clientId: String,
        clientVersion: String,
        userAgent: String,
        visitorData: String? = null
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("X-YouTube-Client-Name", clientId)
            setRequestProperty("X-YouTube-Client-Version", clientVersion)
            if (!visitorData.isNullOrBlank()) {
                setRequestProperty("X-Goog-Visitor-Id", visitorData)
            }
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
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP_${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<YtMusicTrack> = withContext(Dispatchers.IO) {
        val vd = getVisitorData()
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20260101.01.00")
                        put("visitorData", vd)
                        put("hl", "ru")
                        put("gl", "US")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D")
            }
            val resp = httpPostJson(
                "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
                payload.toString(),
                clientId = "67",
                clientVersion = "1.20260101.01.00",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
                visitorData = vd
            )
            val parsed = parseInnerTubeSearch(resp)
            if (parsed.isNotEmpty()) return@withContext parsed.take(limit)
        } catch (_: Throwable) {}

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

    private fun extractAudioFromPlayerJson(resp: String): String? {
        val root = JSONObject(resp)
        val streamingData = root.optJSONObject("streamingData") ?: return null
        val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return null
        var bestUrl: String? = null
        var bestBitrate = 0
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
        return bestUrl
    }

    suspend fun resolveAudioStreamUrl(videoId: String, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            streamCache[videoId]?.let { return@withContext it }
        }
        val vd = getVisitorData()

        // 1. InnerTube ANDROID_VR (не требует poToken и отдает прямые ссылки)
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("androidSdkVersion", 32)
                        put("visitorData", vd)
                        put("hl", "ru")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val resp = httpPostJson(
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                payload.toString(),
                clientId = "28",
                clientVersion = "1.60.19",
                userAgent = STREAM_USER_AGENT,
                visitorData = vd
            )
            val url = extractAudioFromPlayerJson(resp)
            if (!url.isNullOrBlank()) {
                streamCache[videoId] = url
                return@withContext url
            }
        } catch (_: Throwable) {}

        // 2. InnerTube IOS fallback
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "20.10.4")
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPhone16,2")
                        put("osName", "iOS")
                        put("osVersion", "18.3.2.22D82")
                        put("visitorData", vd)
                        put("hl", "ru")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val resp = httpPostJson(
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                payload.toString(),
                clientId = "5",
                clientVersion = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                visitorData = vd
            )
            val url = extractAudioFromPlayerJson(resp)
            if (!url.isNullOrBlank()) {
                streamCache[videoId] = url
                return@withContext url
            }
        } catch (_: Throwable) {}

        // 3. Piped API fallback
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
                setRequestProperty("User-Agent", STREAM_USER_AGENT)
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
