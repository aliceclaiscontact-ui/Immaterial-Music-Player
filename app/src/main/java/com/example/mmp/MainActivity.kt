package com.example.mmp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MMPApp()
        }
    }
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
