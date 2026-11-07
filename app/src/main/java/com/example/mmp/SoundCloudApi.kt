package com.example.mmp

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

object SoundCloudClient {
    private var cachedClientId: String? = null
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun getOrExtractClientId(): String? = withContext(Dispatchers.IO) {
        if (!cachedClientId.isNullOrBlank()) return@withContext cachedClientId

        try {
            val html = httpGet("https://soundcloud.com")
            val scriptRegex = Regex("""https://a-v2\.sndcdn\.com/assets/[^"']+\.js""")
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
        null
    }

    suspend fun searchTracks(query: String, limit: Int = 25): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val cid = getOrExtractClientId() ?: return@withContext emptyList()
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQ&client_id=$cid&limit=$limit"

        try {
            val response = httpGet(endpoint)
            val root = JSONObject(response)
            val collection = root.optJSONArray("collection") ?: JSONArray()
            val result = mutableListOf<SoundCloudTrack>()

            for (i in 0 until collection.length()) {
                val item = collection.getJSONObject(i)
                val id = item.optLong("id", 0L)
                val title = item.optString("title", "Без названия")
                val user = item.optJSONObject("user")
                val artist = user?.optString("username", "SoundCloud") ?: "SoundCloud"
                val rawArtwork = item.optString("artwork_url", "").takeIf { it.isNotBlank() && it != "null" }
                val artwork = rawArtwork?.replace("-large", "-t500x500")
                val duration = item.optLong("duration", 0L)

                val media = item.optJSONObject("media")
                val transcodings = media?.optJSONArray("transcodings")
                var progressiveUrl: String? = null

                if (transcodings != null) {
                    for (t in 0 until transcodings.length()) {
                        val tr = transcodings.getJSONObject(t)
                        val format = tr.optJSONObject("format")?.optString("protocol", "")
                        if (format == "progressive") {
                            progressiveUrl = tr.optString("url", null)
                            break
                        }
                    }
                    if (progressiveUrl == null && transcodings.length() > 0) {
                        progressiveUrl = transcodings.getJSONObject(0).optString("url", null)
                    }
                }

                if (id != 0L && progressiveUrl != null) {
                    result.add(SoundCloudTrack(id, title, artist, artwork, duration, progressiveUrl))
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun resolveStreamUrl(streamMetaUrl: String): String? = withContext(Dispatchers.IO) {
        val cid = getOrExtractClientId() ?: return@withContext null
        val sep = if (streamMetaUrl.contains("?")) "&" else "?"
        val target = "$streamMetaUrl${sep}client_id=$cid"

        try {
            val resp = httpGet(target)
            val json = JSONObject(resp)
            json.optString("url", null)
        } catch (_: Throwable) {
            null
        }
    }

    fun toTrack(scTrack: SoundCloudTrack, directHttpUri: String): Track {
        return Track(
            id = scTrack.id,
            uri = Uri.parse(directHttpUri),
            title = scTrack.title,
            artist = scTrack.artist,
            album = "SoundCloud",
            durationMs = scTrack.durationMs,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            filePath = "",
            folderPath = "SoundCloud",
            folderName = "SoundCloud",
            customCoverUri = scTrack.artworkUrl,
            customLyrics = null
        )
    }
}
