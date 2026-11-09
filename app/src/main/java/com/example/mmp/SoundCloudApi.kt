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

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun getOrExtractClientId(): String = withContext(Dispatchers.IO) {
        if (!cachedClientId.isNullOrBlank()) return@withContext cachedClientId!!
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

    private fun parseTrackJson(item: JSONObject): SoundCloudTrack? {
        val id = item.optLong("id", 0L)
        if (id == 0L) return null

        val policy = item.optString("policy", "")
        if (policy.equals("SNIPPET", ignoreCase = true)) return null

        val title = item.optString("title", "Без названия")
        val user = item.optJSONObject("user")
        val artist = user?.optString("username", "SoundCloud") ?: "SoundCloud"
        val rawArtwork = item.optString("artwork_url", "").takeIf { it.isNotBlank() && it != "null" }
            ?: user?.optString("avatar_url", "").takeIf { it.isNotBlank() && it != "null" }
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
        val cid = getOrExtractClientId()
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQ&client_id=$cid&limit=$limit"

        try {
            val response = httpGet(endpoint)
            val root = JSONObject(response)
            val collection = root.optJSONArray("collection") ?: JSONArray()
            val result = mutableListOf<SoundCloudTrack>()

            for (i in 0 until collection.length()) {
                val item = collection.getJSONObject(i)
                parseTrackJson(item)?.let { result.add(it) }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<SoundCloudPlaylist> = withContext(Dispatchers.IO) {
        val cid = getOrExtractClientId()
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://api-v2.soundcloud.com/search/playlists?q=$encodedQ&client_id=$cid&limit=$limit"

        try {
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
                val rawArtwork = item.optString("artwork_url", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: user?.optString("avatar_url", "").takeIf { it.isNotBlank() && it != "null" }
                val artwork = rawArtwork?.replace("-large", "-t500x500")
                val trackCount = item.optInt("track_count", 0)

                val tracksArr = item.optJSONArray("tracks") ?: JSONArray()
                val playlistTracks = mutableListOf<SoundCloudTrack>()
                for (j in 0 until tracksArr.length()) {
                    val trkObj = tracksArr.optJSONObject(j) ?: continue
                    parseTrackJson(trkObj)?.let { playlistTracks.add(it) }
                }

                result.add(
                    SoundCloudPlaylist(
                        id = id,
                        title = title,
                        author = author,
                        artworkUrl = artwork,
                        trackCount = if (trackCount > 0) trackCount else playlistTracks.size,
                        tracks = playlistTracks
                    )
                )
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun resolveStreamUrl(streamMetaUrl: String): String? = withContext(Dispatchers.IO) {
        if (streamMetaUrl.startsWith("http") && (streamMetaUrl.contains(".mp3") || streamMetaUrl.contains(".m3u8") || streamMetaUrl.contains("cf-media.sndcdn.com") || streamMetaUrl.contains("cf-hls-media.sndcdn.com"))) {
            return@withContext streamMetaUrl
        }
        resolvedUrlCache[streamMetaUrl]?.let { return@withContext it }

        val cid = getOrExtractClientId()
        val sep = if (streamMetaUrl.contains("?")) "&" else "?"
        val target = "$streamMetaUrl${sep}client_id=$cid"

        try {
            val resp = httpGet(target)
            val json = JSONObject(resp)
            val direct = json.optString("url", "")
            if (direct.isNotBlank()) {
                resolvedUrlCache[streamMetaUrl] = direct
                direct
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun downloadTrackToDevice(context: Context, title: String, artist: String, streamMetaOrDirectUrl: String): File? = withContext(Dispatchers.IO) {
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

            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MMP_SoundCloud")
            if (!musicDir.exists()) musicDir.mkdirs()

            val cleanArtist = artist.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(30)
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ _-]"), "").trim().take(40)
            val fileName = "$cleanArtist - $cleanTitle".trim().ifBlank { "sc_track_${System.currentTimeMillis()}" }
            val outFile = File(musicDir, "$fileName.mp3")

            FileOutputStream(outFile).use { out ->
                if (!finalUrl.contains(".m3u8")) {
                    val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                    conn.inputStream.use { inp -> inp.copyTo(out) }
                } else {
                    var playlist = httpGet(finalUrl)
                    val baseUri = finalUrl.substringBeforeLast("/")
                    val subUrl = playlist.lines().map { it.trim() }.firstOrNull { !it.startsWith("#") && it.contains(".m3u8") }
                    if (!subUrl.isNullOrBlank()) {
                        val resolvedSub = if (subUrl.startsWith("http")) subUrl else "$baseUri/$subUrl"
                        playlist = httpGet(resolvedSub)
                    }
                    val segments = playlist.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                    if (segments.isEmpty()) return@withContext null
                    for (seg in segments) {
                        val segUrl = if (seg.startsWith("http")) seg else "$baseUri/$seg"
                        val conn = (URL(segUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 12000
                            readTimeout = 12000
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
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
                arrayOf("audio/mpeg"),
                null
            )
            return@withContext outFile
        } catch (e: Throwable) {
            e.printStackTrace()
            return@withContext null
        }
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
