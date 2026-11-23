package com.example.mmp

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 9000
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
            if (now - lastClientIdTimeMs < 3600_000L) return@withContext it
        }
        try {
            val mainHtml = httpGet("https://soundcloud.com")
            val scriptRegex = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
            val scripts = scriptRegex.findAll(mainHtml).map { it.groupValues[1] }.toList().reversed()
            val idRegex = Regex("""client_id\s*:\s*"([a-zA-Z0-9]{32})"""")
            for (scriptUrl in scripts.take(6)) {
                try {
                    val js = httpGet(scriptUrl)
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
        repeat(2) { attempt ->
            try {
                if (attempt == 1) clearCache()
                val cid = getClientId()
                val apiUrl = "https://api-v2.soundcloud.com/search/tracks?q=$encoded&client_id=$cid&limit=$limit&offset=0"
                val json = JSONObject(httpGet(apiUrl))
                val collection = json.optJSONArray("collection") ?: return@withContext results
                for (i in 0 until collection.length()) {
                    val item = collection.optJSONObject(i) ?: continue
                    parseTrackJson(item)?.let { results.add(it) }
                }
                if (results.isNotEmpty()) return@withContext results
            } catch (_: Throwable) {}
        }
        results
    }

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<SoundCloudPlaylist> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SoundCloudPlaylist>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        repeat(2) { attempt ->
            try {
                if (attempt == 1) clearCache()
                val cid = getClientId()
                val apiUrl = "https://api-v2.soundcloud.com/search/playlists?q=$encoded&client_id=$cid&limit=$limit&offset=0"
                val json = JSONObject(httpGet(apiUrl))
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
                if (results.isNotEmpty()) return@withContext results
            } catch (_: Throwable) {}
        }
        results
    }

    suspend fun fetchFullPlaylist(playlistId: Long): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val fullTracks = mutableListOf<SoundCloudTrack>()
        try {
            val cid = getClientId()
            val plJson = JSONObject(httpGet("https://api-v2.soundcloud.com/playlists/$playlistId?client_id=$cid"))
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
                        val batchResp = httpGet("https://api-v2.soundcloud.com/tracks?ids=$idsParam&client_id=$cid")
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
        repeat(2) { attempt ->
            try {
                if (attempt == 1) clearCache()
                val cid = getClientId()
                val sep = if (transcodingUrl.contains("?")) "&" else "?"
                val full = "$transcodingUrl${sep}client_id=$cid"
                val json = JSONObject(httpGet(full))
                val direct = json.optString("url", "").takeIf { it.isNotBlank() }
                if (direct != null) {
                    resolvedUrlCache[transcodingUrl] = direct
                    return@withContext direct
                }
            } catch (_: Throwable) {}
        }
        null
    }

    private fun downloadBytes(urlStr: String): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0.0")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP_${conn.responseCode}")
        return conn.inputStream.use { it.readBytes() }
    }

    private fun downloadHlsToMp3(m3u8Url: String, outFile: File): Boolean {
        return try {
            val playlistText = httpGet(m3u8Url)
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
