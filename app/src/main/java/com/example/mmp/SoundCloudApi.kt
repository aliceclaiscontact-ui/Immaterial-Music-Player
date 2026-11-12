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
    val tracks: List<SoundCloudTrack> = emptyList()
)

object SoundCloudClient {
    private const val FALLBACK_CLIENT_ID = "2t9loNQH90kzJcsMmUEoeMGjVCvAc2hw"
    private var cachedClientId: String? = null
    private val resolvedUrlCache = mutableMapOf<String, String>()
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun clearCache() {
        cachedClientId = null
        resolvedUrlCache.clear()
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        val code = conn.responseCode
        if (code == 401 || code == 403) {
            clearCache()
            throw IllegalStateException("HTTP_AUTH_$code")
        }
        if (code !in 200..299) {
            throw IllegalStateException("HTTP_$code")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun getOrExtractClientId(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh && !cachedClientId.isNullOrBlank()) return@withContext cachedClientId!!
        try {
            val html = httpGet("https://soundcloud.com/discover")
            val scriptRegex = Regex("""https://[a-zA-Z0-9\-_.]+\.sndcdn\.com/assets/[^"']+\.js""")
            val scriptUrls = scriptRegex.findAll(html).map { it.value }.toList()

            val clientRegex = Regex("""client_id[:=]["']([a-zA-Z0-9]{32})["']""")
            for (scriptUrl in scriptUrls.reversed()) {
                try {
                    val scriptBody = httpGet(scriptUrl)
                    val match = clientRegex.find(scriptBody)
                    if (match != null) {
                        val key = match.groupValues[1]
                        cachedClientId = key
                        return@withContext key
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        cachedClientId = FALLBACK_CLIENT_ID
        FALLBACK_CLIENT_ID
    }

    fun parseTrackJson(item: JSONObject): SoundCloudTrack? {
        val id = item.optLong("id", 0L)
        if (id == 0L) return null

        val policy = item.optString("policy", "")
        if (policy.equals("SNIPPET", ignoreCase = true)) return null

        val title = item.optString("title", "Без названия")
        val user = item.optJSONObject("user")
        val artist = user?.optString("username", "SoundCloud") ?: "SoundCloud"
        val rawArtwork = item.optString("artwork_url", "").takeIf { it.isNotBlank() && it != "null" }
            ?: user?.optString("avatar_url", "")?.takeIf { it.isNotBlank() && it != "null" }
        val artwork = rawArtwork?.replace("-large", "-t500x500")
        val duration = item.optLong("duration", 0L)

        val media = item.optJSONObject("media")
        val transcodings = media?.optJSONArray("transcodings")
        var selectedStreamUrl: String? = null

        if (transcodings != null && transcodings.length() > 0) {
            for (t in 0 until transcodings.length()) {
                val tr = transcodings.getJSONObject(t)
                val format = tr.optJSONObject("format")?.optString("protocol", "") ?: ""
                val snippet = tr.optBoolean("snipped", false)
                if (snippet) continue

                if (format == "progressive") {
                    val u = tr.optString("url", "")
                    if (u.isNotBlank()) {
                        selectedStreamUrl = u
                        break
                    }
                }
            }
            if (selectedStreamUrl.isNullOrBlank()) {
                for (t in 0 until transcodings.length()) {
                    val tr = transcodings.getJSONObject(t)
                    if (!tr.optBoolean("snipped", false)) {
                        val u = tr.optString("url", "")
                        if (u.isNotBlank()) {
                            selectedStreamUrl = u
                            break
                        }
                    }
                }
            }
        }

        if (selectedStreamUrl.isNullOrBlank()) return null
        return SoundCloudTrack(id, title, artist, artwork, duration, selectedStreamUrl)
    }

    suspend fun searchTracks(query: String, limit: Int = 35): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        for (attempt in 0..1) {
            try {
                val cid = getOrExtractClientId(forceRefresh = attempt == 1)
                val endpoint = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQ&client_id=$cid&limit=$limit"
                val response = httpGet(endpoint)
                val root = JSONObject(response)
                val collection = root.optJSONArray("collection") ?: JSONArray()
                val result = mutableListOf<SoundCloudTrack>()
                for (i in 0 until collection.length()) {
                    val item = collection.getJSONObject(i)
                    parseTrackJson(item)?.let { result.add(it) }
                }
                return@withContext result
            } catch (_: Throwable) {
                if (attempt == 0) clearCache()
            }
        }
        emptyList()
    }

    suspend fun fetchFullPlaylist(playlistId: Long): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            try {
                val cid = getOrExtractClientId(forceRefresh = attempt == 1)
                val endpoint = "https://api-v2.soundcloud.com/playlists/$playlistId?client_id=$cid"
                val resp = httpGet(endpoint)
                val root = JSONObject(resp)
                val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
                val fullTracks = mutableListOf<SoundCloudTrack>()
                val missingIds = mutableListOf<Long>()

                for (i in 0 until tracksArr.length()) {
                    val trkObj = tracksArr.optJSONObject(i) ?: continue
                    val parsed = parseTrackJson(trkObj)
                    if (parsed != null) {
                        fullTracks.add(parsed)
                    } else {
                        val mid = trkObj.optLong("id", 0L)
                        if (mid != 0L) missingIds.add(mid)
                    }
                }

                if (missingIds.isNotEmpty()) {
                    val chunks = missingIds.chunked(30)
                    for (chunk in chunks) {
                        val idsStr = chunk.joinToString(",")
                        val batchUrl = "https://api-v2.soundcloud.com/tracks?ids=$idsStr&client_id=$cid"
                        try {
                            val batchResp = httpGet(batchUrl)
                            val arr = JSONArray(batchResp)
                            for (k in 0 until arr.length()) {
                                val o = arr.optJSONObject(k) ?: continue
                                parseTrackJson(o)?.let { fullTracks.add(it) }
                            }
                        } catch (_: Throwable) {}
                    }
                }
                return@withContext fullTracks
            } catch (_: Throwable) {
                if (attempt == 0) clearCache()
            }
        }
        emptyList()
    }

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<SoundCloudPlaylist> = withContext(Dispatchers.IO) {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        for (attempt in 0..1) {
            try {
                val cid = getOrExtractClientId(forceRefresh = attempt == 1)
                val endpoint = "https://api-v2.soundcloud.com/search/playlists?q=$encodedQ&client_id=$cid&limit=$limit"
                val response = httpGet(endpoint)
                val root = JSONObject(response)
                val collection = root.optJSONArray("collection") ?: JSONArray()
                val result = mutableListOf<SoundCloudPlaylist>()

                for (i in 0 until collection.length()) {
                    val item = collection.getJSONObject(i)
                    val id = item.optLong("id", 0L)
                    if (id == 0L) continue

                    val title = item.optString("title", "Плейлист")
                    val user = item.optJSONObject("user")
                    val author = user?.optString("username", "SoundCloud") ?: "SoundCloud"

                    val tracksArr = item.optJSONArray("tracks") ?: JSONArray()
                    val playlistTracks = mutableListOf<SoundCloudTrack>()
                    for (j in 0 until tracksArr.length()) {
                        val trkObj = tracksArr.optJSONObject(j) ?: continue
                        parseTrackJson(trkObj)?.let { playlistTracks.add(it) }
                    }

                    var rawArtwork = item.optString("artwork_url", "").takeIf { it.isNotBlank() && it != "null" }
                    if (rawArtwork.isNullOrBlank() && playlistTracks.isNotEmpty()) {
                        rawArtwork = playlistTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                    }
                    if (rawArtwork.isNullOrBlank()) {
                        rawArtwork = user?.optString("avatar_url", "")?.takeIf { it.isNotBlank() && it != "null" }
                    }
                    val artwork = rawArtwork?.replace("-large", "-t500x500")
                    val trackCount = item.optInt("track_count", playlistTracks.size)

                    result.add(
                        SoundCloudPlaylist(
                            id = id,
                            title = title,
                            author = author,
                            artworkUrl = artwork,
                            trackCount = maxOf(trackCount, playlistTracks.size),
                            tracks = playlistTracks
                        )
                    )
                }
                return@withContext result
            } catch (_: Throwable) {
                if (attempt == 0) clearCache()
            }
        }
        emptyList()
    }

    suspend fun resolveStreamUrl(streamMetaUrl: String, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh && streamMetaUrl.startsWith("http") && (streamMetaUrl.contains(".mp3") || streamMetaUrl.contains(".m3u8") || streamMetaUrl.contains("cf-media.sndcdn.com") || streamMetaUrl.contains("cf-hls-media.sndcdn.com"))) {
            return@withContext streamMetaUrl
        }
        if (!forceRefresh) {
            resolvedUrlCache[streamMetaUrl]?.let { return@withContext it }
        }

        for (attempt in 0..1) {
            try {
                val cid = getOrExtractClientId(forceRefresh = forceRefresh || attempt == 1)
                val sep = if (streamMetaUrl.contains("?")) "&" else "?"
                val target = "$streamMetaUrl${sep}client_id=$cid"
                val resp = httpGet(target)
                val json = JSONObject(resp)
                val direct = json.optString("url", "")
                if (direct.isNotBlank()) {
                    resolvedUrlCache[streamMetaUrl] = direct
                    return@withContext direct
                }
            } catch (_: Throwable) {
                if (attempt == 0) clearCache()
            }
        }
        null
    }

    suspend fun downloadTrackToDevice(
        context: Context,
        title: String,
        artist: String,
        streamMetaOrDirectUrl: String,
        customSubDir: String? = null
    ): File? = withContext(Dispatchers.IO) {
        var partialFile: File? = null
        try {
            var direct: String? = null
            if (streamMetaOrDirectUrl.contains("api-v2.soundcloud.com")) {
                direct = resolveStreamUrl(streamMetaOrDirectUrl)
            }
            if (direct.isNullOrBlank()) {
                val query = "$artist $title".trim()
                val found = searchTracks(query, limit = 8)
                val best = found.firstOrNull { it.title.equals(title, ignoreCase = true) && it.streamProgressiveUrl != null }
                    ?: found.firstOrNull { it.streamProgressiveUrl != null }
                if (best?.streamProgressiveUrl != null) {
                    direct = resolveStreamUrl(best.streamProgressiveUrl)
                }
            }
            if (direct.isNullOrBlank() && streamMetaOrDirectUrl.startsWith("http")) {
                direct = streamMetaOrDirectUrl
            }

            val finalUrl = direct ?: return@withContext null

            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), customSubDir ?: "MMP_SoundCloud")
            if (!baseDir.exists()) baseDir.mkdirs()

            val cleanArtist = artist.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(30)
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(40)
            val fileName = "$cleanArtist - $cleanTitle".trim().ifBlank { "sc_track_${System.currentTimeMillis()}" }
            val isHls = finalUrl.substringBefore("?").contains(".m3u8", ignoreCase = true)
            var outputExtension = URL(finalUrl).path.substringAfterLast('.', "mp3").lowercase()
            var outputMime = when (outputExtension) {
                "m4a", "mp4" -> "audio/mp4"
                "aac" -> "audio/aac"
                "ogg", "opus" -> "audio/ogg"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                else -> "audio/mpeg"
            }

            var mediaPlaylistUrl = finalUrl
            var mediaPlaylist = ""
            var initSegmentUrl: String? = null
            var segmentUrls = emptyList<String>()

            if (isHls) {
                mediaPlaylist = httpGet(finalUrl)
                val childPlaylist = mediaPlaylist.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it.contains(".m3u8", true) }
                if (childPlaylist != null) {
                    mediaPlaylistUrl = URL(URL(finalUrl), childPlaylist).toString()
                    mediaPlaylist = httpGet(mediaPlaylistUrl)
                }

                val encryptionLine = mediaPlaylist.lineSequence()
                    .firstOrNull { it.startsWith("#EXT-X-KEY:") && !it.contains("METHOD=NONE", true) }
                if (encryptionLine != null || mediaPlaylist.contains("#EXT-X-BYTERANGE")) {
                    return@withContext null
                }

                val mapReference = Regex("#EXT-X-MAP:[^\\r\\n]*URI=\\"([^\\"]+)\\"")
                    .find(mediaPlaylist)?.groupValues?.getOrNull(1)
                initSegmentUrl = mapReference?.let { URL(URL(mediaPlaylistUrl), it).toString() }
                val segmentReferences = mediaPlaylist.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
                if (segmentReferences.isEmpty()) return@withContext null
                segmentUrls = segmentReferences.map { URL(URL(mediaPlaylistUrl), it).toString() }

                val firstSegmentExtension = URL(segmentUrls.first()).path.substringAfterLast('.', "ts").lowercase()
                outputExtension = when {
                    initSegmentUrl != null -> "m4a"
                    firstSegmentExtension in setOf("aac", "mp3", "m4a", "ts", "ogg") -> firstSegmentExtension
                    firstSegmentExtension == "m4s" -> "m4a"
                    else -> "ts"
                }
                outputMime = when (outputExtension) {
                    "m4a", "mp4", "m4s" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "mp3" -> "audio/mpeg"
                    "ogg" -> "audio/ogg"
                    "ts" -> "video/mp2t"
                    else -> "application/octet-stream"
                }
            } else if (outputExtension !in setOf("mp3", "m4a", "mp4", "aac", "ogg", "opus", "flac", "wav")) {
                outputExtension = "mp3"
            }

            val outFile = File(baseDir, "$fileName.$outputExtension")
            partialFile = outFile
            FileOutputStream(outFile).use { out ->
                if (!isHls) {
                    val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                    if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP_${conn.responseCode}")
                    conn.inputStream.use { inp -> inp.copyTo(out) }
                } else {
                    initSegmentUrl?.let { initUrl ->
                        val conn = (URL(initUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 12000
                            readTimeout = 20000
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                        if (conn.responseCode !in 200..299) throw IllegalStateException("HLS_INIT_HTTP_${conn.responseCode}")
                        conn.inputStream.use { inp -> inp.copyTo(out) }
                    }
                    for (segmentUrl in segmentUrls) {
                        val conn = (URL(segmentUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 12000
                            readTimeout = 20000
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                        if (conn.responseCode !in 200..299) throw IllegalStateException("HLS_SEGMENT_HTTP_${conn.responseCode}")
                        conn.inputStream.use { inp -> inp.copyTo(out) }
                    }
                }
            }

            if (!outFile.exists() || outFile.length() < 1024L) {
                outFile.delete()
                return@withContext null
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf(outputMime),
                null
            )
            return@withContext outFile
        } catch (e: Throwable) {
            partialFile?.delete()
            return@withContext null
        }
    }

    suspend fun downloadEntirePlaylist(
        context: Context,
        playlist: SoundCloudPlaylist,
        onTrackSaved: suspend (track: Track, current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val tracksToDownload = if (playlist.tracks.size < playlist.trackCount) {
            fetchFullPlaylist(playlist.id).ifEmpty { playlist.tracks }
        } else {
            playlist.tracks
        }

        val cleanPlName = playlist.title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().ifBlank { "Playlist_${playlist.id}" }
        val targetSubDir = "Playlists/$cleanPlName"
        var downloadedCount = 0

        tracksToDownload.forEachIndexed { index, scTrk ->
            val streamUrl = scTrk.streamProgressiveUrl ?: ""
            if (streamUrl.isNotBlank()) {
                val file = downloadTrackToDevice(context, scTrk.title, scTrk.artist, streamUrl, targetSubDir)
                if (file != null) {
                    downloadedCount++
                    val localTrack = buildTrackFromLocalFile(
                        file,
                        fallbackAlbum = cleanPlName,
                        fallbackCover = scTrk.artworkUrl ?: playlist.artworkUrl
                    )
                    onTrackSaved(localTrack, index + 1, tracksToDownload.size)
                }
            }
        }
        downloadedCount
    }

    fun toTrack(scTrack: SoundCloudTrack, directHttpUri: String): Track {
        return Track(
            id = scTrack.id,
            title = scTrack.title,
            artist = scTrack.artist,
            album = "SoundCloud",
            durationMs = scTrack.durationMs,
            uri = Uri.parse(directHttpUri),
            folderName = "SoundCloud",
            folderPath = "SoundCloud",
            filePath = scTrack.streamProgressiveUrl ?: directHttpUri,
            sizeBytes = 0L,
            mimeType = "audio/mpeg",
            customCoverUri = scTrack.artworkUrl
        )
    }
}
