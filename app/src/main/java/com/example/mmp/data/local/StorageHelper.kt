package com.example.mmp.data.local

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object StorageHelper {
    fun getTodayDateString(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    fun isOnboardingCompleted(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean("onboarding_completed_v1", false)
    }

    fun setOnboardingCompleted(prefs: SharedPreferences, completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed_v1", completed).apply()
    }

    fun loadStreamingProvider(prefs: SharedPreferences): StreamingProvider {
        val raw = prefs.getString("active_streaming_provider", StreamingProvider.YOUTUBE_MUSIC.name)
        return StreamingProvider.entries.find { it.name == raw } ?: StreamingProvider.YOUTUBE_MUSIC
    }

    fun saveStreamingProvider(prefs: SharedPreferences, provider: StreamingProvider) {
        prefs.edit().putString("active_streaming_provider", provider.name).commit()
    }

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

    fun saveDeviceLibraryCache(context: Context, tracks: List<Track>) {
        val atomicFile = android.util.AtomicFile(File(context.filesDir, "device_library_cache_v1.json"))
        try {
            val array = JSONArray()
            tracks.forEach { track ->
                array.put(JSONObject().apply {
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
                    put("dateAdded", track.dateAdded)
                    if (track.customCoverUri != null) put("customCoverUri", track.customCoverUri)
                    if (track.customLyrics != null) put("customLyrics", track.customLyrics)
                })
            }
            val output = atomicFile.startWrite()
            try {
                output.write(array.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(output)
            } catch (_: Throwable) {
                atomicFile.failWrite(output)
            }
        } catch (_: Throwable) {
            // The in-memory library remains usable if disk caching is temporarily unavailable.
        }
    }

    fun loadDeviceLibraryCache(context: Context): List<Track> {
        val atomicFile = android.util.AtomicFile(File(context.filesDir, "device_library_cache_v1.json"))
        return try {
            val raw = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(raw)
            val tracks = mutableListOf<Track>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uri = item.optString("uri", "")
                if (uri.isBlank()) continue
                tracks.add(
                    Track(
                        id = item.optLong("id"),
                        uri = Uri.parse(uri),
                        title = item.optString("title", "Неизвестный трек"),
                        artist = item.optString("artist", "Неизвестный исполнитель"),
                        album = item.optString("album", "Без альбома"),
                        durationMs = item.optLong("durationMs", 0L),
                        mimeType = item.optString("mimeType", "audio/mpeg"),
                        sizeBytes = item.optLong("sizeBytes", 0L),
                        filePath = item.optString("filePath", ""),
                        folderPath = item.optString("folderPath", ""),
                        folderName = item.optString("folderName", "Music"),
                        customCoverUri = item.optString("customCoverUri", "").ifBlank { null },
                        customLyrics = item.optString("customLyrics", "").ifBlank { null },
                        dateAdded = item.optLong("dateAdded", 0L)
                    )
                )
            }
            tracks
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveTheme(prefs: SharedPreferences, config: MMPThemeConfig) {
        prefs.edit().putString("saved_theme_v3", config.toJson()).apply()
    }

    fun loadTheme(prefs: SharedPreferences): MMPThemeConfig {
        val stored = MMPThemeConfig.fromJson(prefs.getString("saved_theme_v3", null) ?: "") ?: MMPThemeConfig()
        val isUntouchedLegacyDefault =
            stored.themeName == "Expressive Pink" &&
            stored.primaryHex == 0xFFFF66C4L &&
            stored.secondaryHex == 0xFF7B61FFL &&
            stored.bgHex == 0xFF0D0D12L
        val isGeneratedPurpleImmaDefault =
            stored.themeName == "Imma U1" &&
            stored.primaryHex == 0xFFD9A1FAL &&
            stored.secondaryHex == 0xFFAD86E8L &&
            stored.bgHex == 0xFF2A1040L
        val isUntouchedImmaOneDefault =
            stored.themeName == "Imma 1" &&
            stored.primaryHex == 0xFF79D6C3L &&
            stored.secondaryHex == 0xFF4EAA9AL &&
            stored.bgHex == 0xFF15191DL

        return if (isUntouchedLegacyDefault || isGeneratedPurpleImmaDefault || isUntouchedImmaOneDefault) {
            stored.copy(
                themeName = "Material 3 Expressive",
                primaryHex = 0xFF6750A4L,
                secondaryHex = 0xFF625B71L,
                bgHex = 0xFF141218L
            ).also { migrated -> saveTheme(prefs, migrated) }
        } else stored
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
        prefs.edit().putString("user_playlists_v3", arr.toString()).commit()
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
            obj.put("filePath", t.filePath)
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
                val fp = o.optString("filePath", "").ifBlank { o.optString("uri") }
                list.add(
                    Track(
                        id = o.optLong("id"),
                        uri = Uri.parse(o.optString("uri")),
                        title = o.optString("title", "Online Track"),
                        artist = o.optString("artist", "Streaming"),
                        album = o.optString("album", "Streaming"),
                        durationMs = o.optLong("durationMs", 0L),
                        mimeType = "audio/mpeg",
                        sizeBytes = 0L,
                        filePath = fp,
                        folderPath = "Streaming",
                        folderName = o.optString("album", "Streaming"),
                        customCoverUri = o.optString("customCoverUri").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Throwable) {}
        return list
    }
}