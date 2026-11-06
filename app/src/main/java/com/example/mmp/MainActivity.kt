package com.example.mmp

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MMPApp()
        }
    }
}

enum class ShapeStyle(val label: String, val cornerRadius: Int, val isCut: Boolean = false) {
    EXPRESSIVE("Плавный (MD3)", 28),
    PILL("Пилюля", 50),
    SHARP("Острый (Industrial)", 6),
    CYBER("Срезанный", 16, true)
}

data class MMPThemeConfig(
    val themeName: String = "Default Neon",
    val primaryHex: Long = 0xFFFF66C4,
    val secondaryHex: Long = 0xFF7B61FF,
    val bgHex: Long = 0xFF0D0D12,
    val shapeStyle: ShapeStyle = ShapeStyle.EXPRESSIVE,
    val blurIntensity: Float = 16f
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeName", themeName)
        json.put("primaryHex", primaryHex)
        json.put("secondaryHex", secondaryHex)
        json.put("bgHex", bgHex)
        json.put("shapeStyle", shapeStyle.name)
        json.put("blurIntensity", blurIntensity.toDouble())
        return json.toString(4)
    }

    companion object {
        fun fromJson(jsonString: String): MMPThemeConfig? {
            return try {
                val json = JSONObject(jsonString)
                MMPThemeConfig(
                    themeName = json.optString("themeName", "Custom Theme"),
                    primaryHex = json.optLong("primaryHex", 0xFFFF66C4),
                    secondaryHex = json.optLong("secondaryHex", 0xFF7B61FF),
                    bgHex = json.optLong("bgHex", 0xFF0D0D12),
                    shapeStyle = ShapeStyle.valueOf(json.optString("shapeStyle", "EXPRESSIVE")),
                    blurIntensity = json.optDouble("blurIntensity", 16.0).toFloat()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long
)

data class DetailedMetadata(
    val coverArt: Bitmap? = null,
    val bitrateKbps: String = "—",
    val formatName: String = "AUDIO"
)

suspend fun scanDeviceMusic(context: Context): List<Track> = withContext(Dispatchers.IO) {
    val trackList = mutableListOf<Track>()
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
        MediaStore.Audio.Media.SIZE
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "Неизвестный трек"
            val rawArtist = cursor.getString(artistCol) ?: "Неизвестный исполнитель"
            val artist = if (rawArtist == "<unknown>") "Неизвестный исполнитель" else rawArtist
            val rawAlbum = cursor.getString(albumCol) ?: "Без альбома"
            val album = if (rawAlbum == "<unknown>") "Без альбома" else rawAlbum
            val duration = cursor.getLong(durCol)
            val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
            val size = cursor.getLong(sizeCol)

            if (duration > 5000) {
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                trackList.add(Track(id, contentUri, title, artist, album, duration, mime, size))
            }
        }
    }
    trackList
}

suspend fun extractTrackMetadata(context: Context, track: Track): DetailedMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, track.uri)
        val artBytes = retriever.embeddedPicture
        val bitmap = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null
        val bitrateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        val bitrateStr = if (bitrateRaw != null) "${bitrateRaw / 1000} kbps" else "Lossless"
        val format = when {
            track.mimeType.contains("flac", true) -> "FLAC"
            track.mimeType.contains("wav", true) -> "WAV"
            track.mimeType.contains("mp4", true) || track.mimeType.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }
        DetailedMetadata(bitmap, bitrateStr, format)
    } catch (e: Exception) {
        DetailedMetadata()
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun MMPApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("mmp_prefs", Context.MODE_PRIVATE) }

    var themeConfig by remember { mutableStateOf(MMPThemeConfig()) }
    var deviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf(listOf("Hyperpop Essentials", "Deconstructed Club")) }
    val artistPlayCounts = remember { mutableStateMapOf<String, Int>() }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) scope.launch { deviceTracks = scanDeviceMusic(context) }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) deviceTracks = scanDeviceMusic(context)
    }

    val mediaPlayer = remember { MediaPlayer() }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentMetadata by remember { mutableStateOf(DetailedMetadata()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }

    var listenedSecondsTotal by remember { mutableStateOf(prefs.getInt("listened_seconds", 0)) }

    fun playTrack(track: Track) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, track.uri)
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentTrack = track
            isPlaying = true
            recentTracks = (listOf(track) + recentTracks.filter { it.id != track.id }).take(5)
            artistPlayCounts[track.artist] = (artistPlayCounts[track.artist] ?: 0) + 1
            scope.launch { currentMetadata = extractTrackMetadata(context, track) }
            mediaPlayer.setOnCompletionListener {
                val idx = deviceTracks.indexOfFirst { it.id == track.id }
                if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1]) else isPlaying = false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000L)
            if (mediaPlayer.isPlaying) {
                currentPositionMs = mediaPlayer.currentPosition.toLong()
                listenedSecondsTotal += 1
                prefs.edit().putInt("listened_seconds", listenedSecondsTotal).apply()
            }
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    var selectedBottomTab by remember { mutableStateOf(0) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val currentShape: Shape = remember(themeConfig.shapeStyle) {
        if (themeConfig.shapeStyle.isCut) CutCornerShape(themeConfig.shapeStyle.cornerRadius.dp)
        else RoundedCornerShape(themeConfig.shapeStyle.cornerRadius.dp)
    }

    val primaryColor = Color(themeConfig.primaryHex)
    val secondaryColor = Color(themeConfig.secondaryHex)
    val bgColor = Color(themeConfig.bgHex)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out -> out.write(themeConfig.toJson().toByteArray()) }
            Toast.makeText(context, "Тема .mmpui сохранена!", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            content?.let { jsonStr ->
                val loaded = MMPThemeConfig.fromJson(jsonStr)
                if (loaded != null) {
                    themeConfig = loaded
                    Toast.makeText(context, "Тема применена!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            Box(modifier = Modifier.fillMaxSize().blur(themeConfig.blurIntensity.dp * 3)) {
                Box(modifier = Modifier.size(280.dp).offset((-50).dp, 40.dp).clip(CircleShape).background(primaryColor.copy(0.28f)))
                Box(modifier = Modifier.size(320.dp).align(Alignment.BottomEnd).offset(60.dp, (-80).dp).clip(CircleShape).background(secondaryColor.copy(0.22f)))
            }

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 160.dp)) {
                AnimatedContent(targetState = selectedBottomTab, label = "Tab") { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            recentTracks = recentTracks,
                            allTracksCount = deviceTracks.size,
                            artistPlayCounts = artistPlayCounts,
                            listenedMinutes = listenedSecondsTotal / 60,
                            listenedSeconds = listenedSecondsTotal % 60,
                            hasPermission = hasAudioPermission,
                            currentShape = currentShape,
                            themeConfig = themeConfig,
                            onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                            onThemeChange = { themeConfig = it },
                            onTrackClick = { playTrack(it) },
                            onExportTheme = { exportLauncher.launch("${themeConfig.themeName}.mmpui") },
                            onImportTheme = { importLauncher.launch(arrayOf("*/*")) }
                        )
                        1 -> LibraryScreen(
                            tracks = deviceTracks,
                            playlists = playlists,
                            hasPermission = hasAudioPermission,
                            currentShape = currentShape,
                            primaryColor = primaryColor,
                            onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                            onRescanClick = { scope.launch { deviceTracks = scanDeviceMusic(context) } },
                            onTrackClick = { playTrack(it) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp, 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniPlayerBar(
                    track = currentTrack,
                    metadata = currentMetadata,
                    isPlaying = isPlaying,
                    currentPosMs = currentPositionMs,
                    shape = currentShape,
                    primaryColor = primaryColor,
                    onBarClick = { if (currentTrack != null) showFullPlayerSheet = true },
                    onPlayPause = {
                        if (currentTrack != null) {
                            if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                            else { mediaPlayer.start(); isPlaying = true }
                        } else if (deviceTracks.isNotEmpty()) playTrack(deviceTracks.first())
                    }
                )

                FrostedDockBar(
                    selectedTab = selectedBottomTab,
                    shape = currentShape,
                    primaryColor = primaryColor,
                    onTabSelected = { selectedBottomTab = it },
                    onCreateClick = { showCreatePlaylistDialog = true }
                )
            }

            if (showFullPlayerSheet && currentTrack != null) {
                FullPlayerDialog(
                    track = currentTrack!!,
                    metadata = currentMetadata,
                    isPlaying = isPlaying,
                    currentPosMs = currentPositionMs,
                    shape = currentShape,
                    primaryColor = primaryColor,
                    onDismiss = { showFullPlayerSheet = false },
                    onPlayPause = {
                        if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                        else { mediaPlayer.start(); isPlaying = true }
                    },
                    onSeek = { newPos -> mediaPlayer.seekTo(newPos.toInt()); currentPositionMs = newPos },
                    onNext = {
                        val idx = deviceTracks.indexOfFirst { it.id == currentTrack!!.id }
                        if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1])
                    },
                    onPrev = {
                        val idx = deviceTracks.indexOfFirst { it.id == currentTrack!!.id }
                        if (idx > 0) playTrack(deviceTracks[idx - 1])
                    }
                )
            }

            if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                    shape = currentShape,
                    primaryColor = primaryColor,
                    onDismiss = { showCreatePlaylistDialog = false },
                    onCreate = { if (it.isNotBlank()) playlists = playlists + it; showCreatePlaylistDialog = false }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    recentTracks: List<Track>,
    allTracksCount: Int,
    artistPlayCounts: Map<String, Int>,
    listenedMinutes: Int,
    listenedSeconds: Int,
    hasPermission: Boolean,
    currentShape: Shape,
    themeConfig: MMPThemeConfig,
    onRequestPermission: () -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onTrackClick: (Track) -> Unit,
    onExportTheme: () -> Unit,
    onImportTheme: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    val primaryColor = Color(themeConfig.primaryHex)

    val insightText = remember(artistPlayCounts.toMap()) {
        val total = artistPlayCounts.values.sum()
        val top = artistPlayCounts.maxByOrNull { it.value }
        if (total > 0 && top != null) "Артист ${top.key} играл ${((top.value.toFloat() / total) * 100).toInt()}% твоей музыки!"
        else "Артист SOPHIE играл 57% твоей музыки!"
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MMP", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
            Box(
                modifier = Modifier.clip(currentShape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor.copy(0.5f), currentShape).padding(14.dp, 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
    }
}
