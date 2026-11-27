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

data class YtMusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long
)

object YouTubeMusicClient {
    const val STREAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/133.0.0.0 Safari/537.36"
    private val streamCache = mutableMapOf<String, String>()
    private var cachedVisitorData: String? = null

    private val cobaltInstances = listOf(
        "https://cobalt-api.kwiatekmiki.com/",
        "https://cobalt.canine.tools/",
        "https://api.cobalt.tools/"
    )

    private val pipedInstances = listOf(
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.kavin.rocks"
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
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", STREAM_USER_AGENT)
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val match = Regex(""""VISITOR_DATA":"([^"]+)"""").find(html)
            if (match != null) {
                cachedVisitorData = match.groupValues[1]
                return cachedVisitorData!!
            }
        } catch (_: Throwable) {}
        return "Cgt0VjRfX19fX19fXyjh9_65BjIKCgJVUxIEGgAgSw%3D%3D"
    }

    private fun httpPostJson(
        urlStr: String,
        bodyJson: String,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Int = 2500
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", STREAM_USER_AGENT)
            for ((k, v) in extraHeaders) {
                setRequestProperty(k, v)
            }
        }
        conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP_${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun httpGet(urlStr: String, timeoutMs: Int = 2500): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", STREAM_USER_AGENT)
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
                mapOf(
                    "X-YouTube-Client-Name" to "67",
                    "X-YouTube-Client-Version" to "1.20260101.01.00",
                    "X-Goog-Visitor-Id" to vd
                ),
                timeoutMs = 5000
            )
            val parsed = parseInnerTubeSearch(resp)
            if (parsed.isNotEmpty()) return@withContext parsed.take(limit)
        } catch (_: Throwable) {}
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

    suspend fun resolveAudioStreamUrl(
        videoId: String,
        forceRefresh: Boolean = false,
        fallbackArtist: String = "",
        fallbackTitle: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            streamCache[videoId]?.let { return@withContext it }
        }

        for (cobaltUrl in cobaltInstances) {
            try {
                val body = JSONObject().apply {
                    put("url", "https://music.youtube.com/watch?v=$videoId")
                    put("downloadMode", "audio")
                    put("audioFormat", "mp3")
                }
                val resp = httpPostJson(cobaltUrl, body.toString(), timeoutMs = 2400)
                val root = JSONObject(resp)
                val status = root.optString("status", "")
                if (status == "tunnel" || status == "redirect") {
                    val direct = root.optString("url", "")
                    if (direct.isNotBlank()) {
                        streamCache[videoId] = direct
                        return@withContext direct
                    }
                }
            } catch (_: Throwable) {}
        }

        for (instance in pipedInstances) {
            try {
                val resp = httpGet("$instance/streams/$videoId", timeoutMs = 2400)
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

        if (fallbackTitle.isNotBlank()) {
            try {
                val cleanTitle = fallbackTitle.replace(Regex("""\([^)]*\)"""), "").trim()
                val query = "$fallbackArtist $cleanTitle".trim()
                val scMatches = SoundCloudClient.searchTracks(query, limit = 5)
                val best = scMatches.firstOrNull { it.streamProgressiveUrl != null }
                if (best?.streamProgressiveUrl != null) {
                    val resolvedSc = SoundCloudClient.resolveStreamUrl(best.streamProgressiveUrl, forceRefresh = false)
                    if (!resolvedSc.isNullOrBlank()) {
                        streamCache[videoId] = resolvedSc
                        return@withContext resolvedSc
                    }
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
            val streamUrl = resolveAudioStreamUrl(
                ytTrack.videoId,
                forceRefresh = true,
                fallbackArtist = ytTrack.artist,
                fallbackTitle = ytTrack.title
            ) ?: return@withContext null

            if (streamUrl.contains(".m3u8")) {
                return@withContext SoundCloudClient.downloadTrackToDevice(
                    context,
                    ytTrack.title,
                    ytTrack.artist,
                    streamUrl,
                    "Imma3_YTMusic"
                )
            }

            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Imma3_YTMusic")
            if (!baseDir.exists()) baseDir.mkdirs()

            val cleanArtist = ytTrack.artist.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(30)
            val cleanTitle = ytTrack.title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(40)
            val fileName = "$cleanArtist - $cleanTitle".trim().ifBlank { "yt_${ytTrack.videoId}" }
            val ext = if (streamUrl.contains(".m4a")) "m4a" else "mp3"
            val outFile = File(baseDir, "$fileName.$ext")

            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
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
                arrayOf(if (ext == "m4a") "audio/mp4" else "audio/mpeg"),
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
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            filePath = "ytm://${ytTrack.videoId}",
            folderPath = "YouTube Music",
            folderName = "YouTube Music",
            customCoverUri = ytTrack.artworkUrl
        )
    }
}
