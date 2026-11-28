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
import com.example.mmp.domain.*
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

data class DeviceMusicScanResult(
    val tracks: List<Track>,
    val isComplete: Boolean
)

private val deviceMusicScanMutex = Mutex()

suspend fun scanDeviceMusicResult(context: Context): DeviceMusicScanResult = deviceMusicScanMutex.withLock {
    withContext(Dispatchers.IO) {
        val byPath = LinkedHashMap<String, Track>()
        var mediaStoreQuerySucceeded = false
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        try {
            val mediaStoreCursor = context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            if (mediaStoreCursor != null) {
                mediaStoreQuerySucceeded = true
                mediaStoreCursor.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                if (idCol >= 0 && durCol >= 0) {
                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idCol)
                            val title = if (titleCol >= 0 && !cursor.isNull(titleCol)) cursor.getString(titleCol) else "Неизвестный трек"
                            val rawArtist = if (artistCol >= 0 && !cursor.isNull(artistCol)) cursor.getString(artistCol) else null
                            val rawAlbum = if (albumCol >= 0 && !cursor.isNull(albumCol)) cursor.getString(albumCol) else null
                            val artist = rawArtist?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Неизвестный исполнитель"
                            val album = rawAlbum?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Без альбома"
                            val duration = cursor.getLong(durCol)
                            val mime = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else "audio/mpeg"
                            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                            val dateAdded = if (dateAddedCol >= 0 && !cursor.isNull(dateAddedCol)) cursor.getLong(dateAddedCol) else 0L
                            val path = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) ?: "" else ""
                            val parentFile = path.takeIf { it.isNotBlank() }?.let { File(it).parentFile }
                            val folderPath = parentFile?.absolutePath ?: "/Music"
                            val folderName = parentFile?.name ?: "Music"

                            if (duration >= 5000L) {
                                val contentUri = ContentUris.withAppendedId(collection, id)
                                val key = path.ifBlank { "mediastore:$id" }
                                byPath[key] = Track(
                                    id, contentUri, title, artist, album, duration, mime, size, path, folderPath, folderName,
                                    dateAdded = dateAdded
                                )
                            }
                        } catch (_: Exception) {
                            // Ignore one malformed MediaStore row and continue indexing the rest.
                        }
                    }
                }
                }
            }
        } catch (_: SecurityException) {
            // Permission may have been revoked while the query was running.
        } catch (_: Exception) {
            // A transient provider error must not crash the player.
        }

        DeviceMusicScanResult(byPath.values.toList(), mediaStoreQuerySucceeded)
    }
}

suspend fun scanUnindexedDownloadTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
    val byPath = LinkedHashMap<String, Track>()
    try {
        val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val subDirs = listOf(
            File(musicRoot, "Playlists"),
            File(musicRoot, "MMP_SoundCloud"),
            File(musicRoot, "Imma3_YTMusic")
        )
        val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus")
        for (dir in subDirs) {
            try {
                if (!dir.isDirectory) continue
                dir.walkTopDown().maxDepth(7).forEach { file ->
                    try {
                        if (
                            file.isFile &&
                            file.length() > 2048L &&
                            file.extension.lowercase(Locale.ROOT) in supportedExtensions
                        ) {
                            val path = file.absolutePath
                            if (!byPath.containsKey(path)) {
                                byPath[path] = buildTrackFromLocalFile(file, file.parentFile?.name ?: "Playlists")
                            }
                        }
                    } catch (_: Exception) {
                        // Skip inaccessible files without aborting the complete scan.
                    }
                }
            } catch (_: Exception) {
                // Skip one inaccessible download directory and continue.
            }
        }
    } catch (_: Exception) {
        // Public Music directory can be unavailable on scoped-storage devices.
    }

    byPath.values.toList()
}

suspend fun scanDeviceMusic(context: Context): List<Track> {
    val mediaStore = scanDeviceMusicResult(context)
    val fallback = scanUnindexedDownloadTracks(context)
    return (mediaStore.tracks + fallback).distinctBy {
        it.filePath.ifBlank { it.uri.toString() }
    }
}
private const val MAX_ARTWORK_BYTES = 16 * 1024 * 1024

private fun readArtworkBytes(input: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_ARTWORK_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

fun decodeSampledBitmap(bytes: ByteArray, reqMaxSide: Int = 512): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= reqMaxSide) sampleSize *= 2
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (_: Throwable) {
        null
    }
}

suspend fun loadBitmapFromUriOrHttp(context: Context, uriStr: String, maxSide: Int = 512): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                val connection = (URL(uriStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.contentLengthLong > MAX_ARTWORK_BYTES) return@withContext null
                    val bytes = connection.inputStream.use { readArtworkBytes(it) } ?: return@withContext null
                    decodeSampledBitmap(bytes, maxSide)
                } finally {
                    connection.disconnect()
                }
            } else {
                val bytes = context.contentResolver.openInputStream(Uri.parse(uriStr))
                    ?.use { readArtworkBytes(it) } ?: return@withContext null
                decodeSampledBitmap(bytes, maxSide)
            }
        } catch (_: Throwable) {
            null
        }
    }

suspend fun loadTrackThumbnail(context: Context, track: Track): Bitmap? = withContext(Dispatchers.IO) {
    CoverCache.get(track.id)?.takeIf { !it.isRecycled }?.let { return@withContext it }
    if (track.customCoverUri != null) {
        loadBitmapFromUriOrHttp(context, track.customCoverUri, 256)?.also { CoverCache.put(track.id, it) }?.let { return@withContext it }
    }
    if (track.uri.scheme?.startsWith("http") == true) return@withContext null
    val retriever = MediaMetadataRetriever()
    try {
        if (track.uri.scheme == "file" && track.filePath.isNotBlank()) {
            retriever.setDataSource(track.filePath)
        } else {
            retriever.setDataSource(context, track.uri)
        }
        retriever.embeddedPicture?.let { decodeSampledBitmap(it, 256)?.also { bmp -> CoverCache.put(track.id, bmp) } }
    } catch (_: Throwable) { null } finally { try { retriever.release() } catch (_: Throwable) {} }
}

suspend fun extractTrackMetadata(context: Context, track: Track): DetailedMetadata = withContext(Dispatchers.IO) {
    val isHttpStream = track.uri.scheme?.startsWith("http") == true
    val retriever = MediaMetadataRetriever()
    try {
        var bitmap: Bitmap? = null
        if (track.customCoverUri != null) {
            bitmap = loadBitmapFromUriOrHttp(context, track.customCoverUri, 600)?.also { CoverCache.put(track.id, it) }
        }

        if (!isHttpStream) {
            if (track.uri.scheme == "file" && track.filePath.isNotBlank()) {
                retriever.setDataSource(track.filePath)
            } else {
                retriever.setDataSource(context, track.uri)
            }
            if (bitmap == null) {
                val artBytes = retriever.embeddedPicture
                bitmap = if (artBytes != null) decodeSampledBitmap(artBytes, 600)?.also { CoverCache.put(track.id, it) } else null
            }
        }

        val rawBitrate = if (!isHttpStream) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L else 160000L
        val sampleRateRaw = if (!isHttpStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull() else null
        val sampleRateStr = if (sampleRateRaw != null && sampleRateRaw > 0) String.format(Locale.US, "%.1f kHz", sampleRateRaw / 1000f) else "44.1 kHz"

        val durationSec = (track.durationMs / 1000L).coerceAtLeast(1L)
        val calculatedFromSizeKbps = if (track.sizeBytes > 0) ((track.sizeBytes * 8L) / (durationSec * 1000L)) else 0L
        var kbps = when {
            rawBitrate >= 64000L -> rawBitrate / 1000L
            rawBitrate in 64L..1411L -> rawBitrate
            else -> calculatedFromSizeKbps
        }
        if (kbps < 96L && calculatedFromSizeKbps >= 128L) kbps = calculatedFromSizeKbps
        val format = when {
            track.filePath.startsWith("ytm://") -> "YT STREAM"
            isHttpStream -> "SC STREAM"
            track.mimeType.contains("flac", true) -> "FLAC"
            track.mimeType.contains("wav", true) -> "WAV"
            track.mimeType.contains("mp2t", true) -> "MPEG-TS"
            track.mimeType.contains("aac", true) -> "AAC"
            track.mimeType.contains("ogg", true) || track.mimeType.contains("opus", true) -> "OGG"
            track.mimeType.contains("mp4", true) || track.mimeType.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }
        val cleanKbps = when {
            track.filePath.startsWith("ytm://") -> 160L
            isHttpStream -> 128L
            format == "MP3" && kbps in 290L..340L -> 320L
            format == "MP3" && kbps in 235L..275L -> 256L
            format == "MP3" && kbps in 175L..210L -> 192L
            format == "MP3" && kbps in 115L..145L -> 128L
            kbps <= 0L -> if (format == "FLAC" || format == "WAV") 999L else 320L
            else -> kbps
        }

        var lyricsList = emptyList<LyricLine>()
        if (!track.customLyrics.isNullOrBlank()) {
            lyricsList = parseLrcText(track.customLyrics)
        } else {
            val offlineSaved = loadOfflineLyrics(context, track.artist, track.title)
            if (!offlineSaved.isNullOrBlank()) {
                lyricsList = parseLrcText(offlineSaved)
            } else if (track.filePath.isNotBlank()) {
                try {
                    val audioFile = File(track.filePath)
                    val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
                    if (lrcFile.exists() && lrcFile.canRead()) {
                        lyricsList = parseLrcText(lrcFile.readText())
                    }
                } catch (_: Throwable) {}
            }
        }

        DetailedMetadata(bitmap, if (cleanKbps == 999L) "Lossless" else "$cleanKbps kbps", sampleRateStr, format, lyricsList)
    } catch (e: Throwable) { DetailedMetadata() } finally { try { retriever.release() } catch (_: Throwable) {} }
}

fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}