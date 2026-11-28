package com.example.mmp.data.remote

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.example.mmp.domain.*
import com.example.mmp.domain.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SoundCloudTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val streamProgressiveUrl: String?
)

data class SoundCloudPlaylist(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val tracks: List<SoundCloudTrack>
)

object SoundCloudClient {
    private var cachedClientId: String? = null
    private var lastClientIdTimeMs: Long = 0L
    private val resolvedUrlCache = mutableMapOf<String, String>()

    fun clearCache() {
        resolvedUrlCache.clear()
        cachedClientId = null
        lastClientIdTimeMs = 0L
    }

    private fun httpGet(urlStr: String, timeoutMs: Int = 4500): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/133.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("SC_HTTP_$code")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun getClientId(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedClientId?.let {
            if (now - lastClientIdTimeMs < 7200_000L) return@withContext it
        }
        try {
            val mainHtml = httpGet("https://soundcloud.com", timeoutMs = 4000)
            val scriptRegex = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
            val scripts = scriptRegex.findAll(mainHtml).map { it.groupValues[1] }.toList().reversed()
            val idRegex = Regex("""client_id\s*:\s*"([a-zA-Z0-9]{32})"""")
            for (scriptUrl in scripts.take(2)) {
                try {
                    val js = httpGet(scriptUrl, timeoutMs = 3500)
                    val match = idRegex.find(js)
                    if (match != null) {
                        val found = match.groupValues[1]
                        cachedClientId = found
                        lastClientIdTimeMs = now
                        return@withContext found
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX"
    }

    private fun parseTrackJson(item: JSONObject): SoundCloudTrack? {
        val id = item.optLong("id", 0L)
        if (id == 0L) return null
        val title = item.optString("title", "Без названия")
        val user = item.optJSONObject("user")
        val artist = user?.optString("username", "SoundCloud") ?: "SoundCloud"
        val rawArt = item.optString("artwork_url", "").ifBlank { user?.optString("avatar_url", "") ?: "" }
        val art = rawArt.takeIf { it.isNotBlank() && it != "null" }?.replace("-large", "-t500x500")
        val dur = item.optLong("duration", 0L)

        var progUrl: String? = null
        var hlsUrl: String? = null
        val media = item.optJSONObject("media")
        val transcodings = media?.optJSONArray("transcodings")
        if (transcodings != null) {
            for (j in 0 until transcodings.length()) {
                val tc = transcodings.optJSONObject(j) ?: continue
                val url = tc.optString("url", "")
                val format = tc.optJSONObject("format")
                val protocol = format?.optString("protocol", "") ?: ""
                val mime = format?.optString("mime_type", "") ?: ""
                if (url.isBlank()) continue

                if (protocol == "progressive") {
                    progUrl = url
                    break
                } else if (protocol == "hls" && (mime.contains("mpeg") || hlsUrl == null)) {
                    hlsUrl = url
                }
            }
        }
        val finalStream = progUrl ?: hlsUrl
        return SoundCloudTrack(id, title, artist, art, dur, finalStream)
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SoundCloudTrack>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        try {
            val cid = getClientId()
            val apiUrl = "https://api-v2.soundcloud.com/search/tracks?q=$encoded&client_id=$cid&limit=$limit&offset=0"
            val json = JSONObject(httpGet(apiUrl, timeoutMs = 5000))
            val collection = json.optJSONArray("collection") ?: return@withContext results
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                parseTrackJson(item)?.let { results.add(it) }
            }
        } catch (_: Throwable) {}
        results
    }

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<SoundCloudPlaylist> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SoundCloudPlaylist>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        try {
            val cid = getClientId()
            val apiUrl = "https://api-v2.soundcloud.com/search/playlists?q=$encoded&client_id=$cid&limit=$limit&offset=0"
            val json = JSONObject(httpGet(apiUrl, timeoutMs = 5000))
            val collection = json.optJSONArray("collection") ?: return@withContext results
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                if (id == 0L) continue
                val title = item.optString("title", "Плейлист")
                val user = item.optJSONObject("user")
                val author = user?.optString("username", "SoundCloud") ?: "SoundCloud"
                val rawArt = item.optString("artwork_url", "").ifBlank { user?.optString("avatar_url", "") ?: "" }
                val art = rawArt.takeIf { it.isNotBlank() && it != "null" }?.replace("-large", "-t500x500")
                val trackCount = item.optInt("track_count", 0)

                val tracksList = mutableListOf<SoundCloudTrack>()
                val tracksArr = item.optJSONArray("tracks")
                if (tracksArr != null) {
                    for (t in 0 until tracksArr.length()) {
                        val tObj = tracksArr.optJSONObject(t) ?: continue
                        parseTrackJson(tObj)?.let { tracksList.add(it) }
                    }
                }
                results.add(SoundCloudPlaylist(id, title, author, art, trackCount, tracksList))
            }
        } catch (_: Throwable) {}
        results
    }

    suspend fun fetchFullPlaylist(playlistId: Long): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val fullTracks = mutableListOf<SoundCloudTrack>()
        try {
            val cid = getClientId()
            val plJson = JSONObject(httpGet("https://api-v2.soundcloud.com/playlists/$playlistId?client_id=$cid", timeoutMs = 6000))
            val tracksArr = plJson.optJSONArray("tracks") ?: return@withContext fullTracks
            val missingIds = mutableListOf<Long>()

            for (i in 0 until tracksArr.length()) {
                val tObj = tracksArr.optJSONObject(i) ?: continue
                val parsed = parseTrackJson(tObj)
                if (parsed != null && parsed.streamProgressiveUrl != null) {
                    fullTracks.add(parsed)
                } else {
                    val tid = tObj.optLong("id", 0L)
                    if (tid != 0L) missingIds.add(tid)
                }
            }

            if (missingIds.isNotEmpty()) {
                for (chunk in missingIds.chunked(25)) {
                    try {
                        val idsParam = chunk.joinToString(",")
                        val batchResp = httpGet("https://api-v2.soundcloud.com/tracks?ids=$idsParam&client_id=$cid", timeoutMs = 6000)
                        val arr = org.json.JSONArray(batchResp)
                        for (k in 0 until arr.length()) {
                            val item = arr.optJSONObject(k) ?: continue
                            parseTrackJson(item)?.let { fullTracks.add(it) }
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        fullTracks
    }

    suspend fun resolveStreamUrl(transcodingUrl: String, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            resolvedUrlCache[transcodingUrl]?.let { return@withContext it }
        }
        try {
            val cid = getClientId()
            val sep = if (transcodingUrl.contains("?")) "&" else "?"
            val full = "$transcodingUrl${sep}client_id=$cid"
            val json = JSONObject(httpGet(full, timeoutMs = 4000))
            val direct = json.optString("url", "").takeIf { it.isNotBlank() }
            if (direct != null) {
                resolvedUrlCache[transcodingUrl] = direct
                return@withContext direct
            }
        } catch (_: Throwable) {}
        null
    }

    private fun downloadBytes(urlStr: String): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0.0")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP_${conn.responseCode}")
        return conn.inputStream.use { it.readBytes() }
    }

    private fun downloadHlsToMp3(m3u8Url: String, outFile: File): Boolean {
        return try {
            val playlistText = httpGet(m3u8Url, timeoutMs = 8000)
            val baseUrl = m3u8Url.substringBeforeLast("/") + "/"
            val segmentUrls = playlistText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { if (it.startsWith("http")) it else "$baseUrl$it" }

            if (segmentUrls.isEmpty()) return false
            val buffer = ByteArrayOutputStream()
            for (segUrl in segmentUrls) {
                buffer.write(downloadBytes(segUrl))
            }
            val allBytes = buffer.toByteArray()
            if (allBytes.size < 2048) return false
            FileOutputStream(outFile).use { it.write(allBytes) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun downloadTrackToDevice(
        context: Context,
        title: String,
        artist: String,
        transcodingUrl: String,
        subFolderName: String? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val directUrl = if (transcodingUrl.contains("api-v2.soundcloud.com")) {
                resolveStreamUrl(transcodingUrl, forceRefresh = true) ?: return@withContext null
            } else {
                transcodingUrl
            }

            val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val baseDir = if (subFolderName != null) {
                File(File(musicRoot, "Playlists"), subFolderName)
            } else {
                File(musicRoot, "MMP_SoundCloud")
            }
            if (!baseDir.exists()) baseDir.mkdirs()

            val cleanArtist = artist.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(30)
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(40)
            val fileName = "$cleanArtist - $cleanTitle".trim().ifBlank { "sc_${System.currentTimeMillis()}" }
            val outFile = File(baseDir, "$fileName.mp3")

            if (directUrl.contains(".m3u8")) {
                val ok = downloadHlsToMp3(directUrl, outFile)
                if (!ok) {
                    outFile.delete()
                    return@withContext null
                }
            } else {
                val bytes = downloadBytes(directUrl)
                if (bytes.size < 2048) return@withContext null
                FileOutputStream(outFile).use { it.write(bytes) }
            }

            if (!outFile.exists() || outFile.length() < 2048L) {
                outFile.delete()
                return@withContext null
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )
            outFile
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun downloadEntirePlaylist(
        context: Context,
        playlist: SoundCloudPlaylist,
        onTrackDownloaded: (Track, Int, Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val tracksToDownload = if (playlist.tracks.size >= playlist.trackCount && playlist.tracks.isNotEmpty()) {
            playlist.tracks
        } else {
            fetchFullPlaylist(playlist.id).ifEmpty { playlist.tracks }
        }
        val cleanPlFolder = playlist.title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().ifBlank { "Playlist_${playlist.id}" }
        var successCount = 0
        val total = tracksToDownload.size

        for ((index, scTrk) in tracksToDownload.withIndex()) {
            val streamUrl = scTrk.streamProgressiveUrl ?: continue
            val file = downloadTrackToDevice(context, scTrk.title, scTrk.artist, streamUrl, cleanPlFolder)
            if (file != null && file.exists()) {
                successCount++
                val stableId = (file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL) + 900_000_000L
                val localTrack = Track(
                    id = stableId,
                    uri = Uri.fromFile(file),
                    title = scTrk.title,
                    artist = scTrk.artist,
                    album = playlist.title,
                    durationMs = scTrk.durationMs.coerceAtLeast(5000L),
                    mimeType = "audio/mpeg",
                    sizeBytes = file.length(),
                    filePath = file.absolutePath,
                    folderPath = file.parentFile?.absolutePath ?: "",
                    folderName = cleanPlFolder,
                    customCoverUri = scTrk.artworkUrl ?: playlist.artworkUrl
                )
                withContext(Dispatchers.Main) {
                    onTrackDownloaded(localTrack, index + 1, total)
                }
            }
        }
        successCount
    }

    fun toTrack(scTrack: SoundCloudTrack, transcodingUrl: String): Track {
        return Track(
            id = -scTrack.id,
            uri = Uri.parse(transcodingUrl),
            title = scTrack.title,
            artist = scTrack.artist,
            album = "SoundCloud",
            durationMs = scTrack.durationMs,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            filePath = transcodingUrl,
            folderPath = "SoundCloud",
            folderName = "SoundCloud",
            customCoverUri = scTrack.artworkUrl
        )
    }
}


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
