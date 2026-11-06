package com.example.mmp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val prefs = remember { context.getSharedPreferences("mmp_prefs_v2", Context.MODE_PRIVATE) }

    var themeConfig by remember { mutableStateOf(StorageHelper.loadTheme(prefs)) }
    var deviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentTrackIds by remember { mutableStateOf(StorageHelper.loadRecentIds(prefs)) }
    var playlists by remember { mutableStateOf(StorageHelper.loadPlaylists(prefs)) }
    val artistPlayCounts = remember {
        mutableStateMapOf<String, Int>().apply { putAll(StorageHelper.loadArtistCounts(prefs)) }
    }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) {
            scope.launch { deviceTracks = scanDeviceMusic(context) }
        }
    }

    // При старте проверяем права, сканируем треки и запрашиваем уведомления для шторки
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted
        if (granted) {
            deviceTracks = scanDeviceMusic(context)
        } else {
            permissionLauncher.launch(audioPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Восстанавливаем последние 5 треков по сохранённым в памяти ID
    val recentTracks = remember(deviceTracks, recentTrackIds) {
        val trackMap = deviceTracks.associateBy { it.id }
        recentTrackIds.mapNotNull { trackMap[it] }
    }

    val mediaPlayer = remember { MediaPlayer() }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentMetadata by remember { mutableStateOf(DetailedMetadata()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }

    var listenedSecondsTotal by remember { mutableStateOf(prefs.getInt("listened_seconds", 0)) }

    fun syncMediaService(track: Track?, meta: DetailedMetadata, playing: Boolean) {
        if (track == null) return
        PlayerBridge.currentTitle = track.title
        PlayerBridge.currentArtist = track.artist
        PlayerBridge.currentBitmap = meta.coverArt
        PlayerBridge.isPlaying = playing
        val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    fun playTrack(track: Track) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, track.uri)
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentTrack = track
            isPlaying = true

            // Сохраняем в последние 5 треков и статистику артистов
            val updatedIds = (listOf(track.id) + recentTrackIds.filter { it != track.id }).take(5)
            recentTrackIds = updatedIds
            StorageHelper.saveRecentIds(prefs, updatedIds)

            artistPlayCounts[track.artist] = (artistPlayCounts[track.artist] ?: 0) + 1
            StorageHelper.saveArtistCounts(prefs, artistPlayCounts.toMap())

            scope.launch {
                val meta = extractTrackMetadata(context, track)
                currentMetadata = meta
                syncMediaService(track, meta, true)
            }

            mediaPlayer.setOnCompletionListener {
                val idx = deviceTracks.indexOfFirst { it.id == track.id }
                if (idx != -1 && idx + 1 < deviceTracks.size) {
                    playTrack(deviceTracks[idx + 1])
                } else {
                    isPlaying = false
                    syncMediaService(currentTrack, currentMetadata, false)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePlayPause() {
        if (currentTrack != null) {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                mediaPlayer.start()
                isPlaying = true
            }
            syncMediaService(currentTrack, currentMetadata, isPlaying)
        } else if (deviceTracks.isNotEmpty()) {
            playTrack(deviceTracks.first())
        }
    }

    // Привязываем кнопки из системного уведомления в шторке
    SideEffect {
        PlayerBridge.onPlayPause = { togglePlayPause() }
        PlayerBridge.onNext = {
            currentTrack?.let { cur ->
                val idx = deviceTracks.indexOfFirst { it.id == cur.id }
                if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1])
            }
        }
        PlayerBridge.onPrev = {
            currentTrack?.let { cur ->
                val idx = deviceTracks.indexOfFirst { it.id == cur.id }
                if (idx > 0) playTrack(deviceTracks[idx - 1])
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500L)
            if (mediaPlayer.isPlaying) {
                currentPositionMs = mediaPlayer.currentPosition.toLong()
            }
            delay(500L)
            if (mediaPlayer.isPlaying) {
                currentPositionMs = mediaPlayer.currentPosition.toLong()
                listenedSecondsTotal += 1
                prefs.edit().putInt("listened_seconds", listenedSecondsTotal).apply()
            }
        }
    }

    var selectedBottomTab by remember { mutableStateOf(0) }
    var showCreateStep1Sheet by remember { mutableStateOf(false) }
    var showCreateStep2Dialog by remember { mutableStateOf(false) }
    var pendingPlaylistCoverUri by remember { mutableStateOf<String?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            pendingPlaylistCoverUri = it.toString()
        }
    }

    val cardShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toCardShape() }
    val expressiveShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toExpressiveShape() }

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
                    StorageHelper.saveTheme(prefs, loaded)
                    Toast.makeText(context, "Тема применена и сохранена!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Уже закреплено в фоне!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            Box(modifier = Modifier.fillMaxSize().blur(themeConfig.blurIntensity.dp * 3)) {
                Box(modifier = Modifier.size(280.dp).offset((-50).dp, 40.dp).clip(CircleShape).background(primaryColor.copy(0.28f)))
                Box(modifier = Modifier.size(320.dp).align(Alignment.BottomEnd).offset(60.dp, (-80).dp).clip(CircleShape).background(secondaryColor.copy(0.22f)))
            }

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 162.dp)) {
                AnimatedContent(
                    targetState = selectedBottomTab,
                    transitionSpec = {
                        fadeIn(tween(240)) + scaleIn(initialScale = 0.96f) togetherWith fadeOut(tween(180))
                    },
                    label = "MainTabTransition"
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            recentTracks = recentTracks,
                            allTracksCount = deviceTracks.size,
                            artistPlayCounts = artistPlayCounts,
                            listenedMinutes = listenedSecondsTotal / 60,
                            listenedSeconds = listenedSecondsTotal % 60,
                            hasPermission = hasAudioPermission,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            themeConfig = themeConfig,
                            onRequestPermission = { permissionLauncher.launch(audioPermission) },
                            onRequestBatteryIgnore = { requestIgnoreBatteryOptimizations() },
                            onThemeChange = {
                                themeConfig = it
                                StorageHelper.saveTheme(prefs, it)
                            },
                            onTrackClick = { playTrack(it) },
                            onExportTheme = { exportLauncher.launch("${themeConfig.themeName}.mmpui") },
                            onImportTheme = { importLauncher.launch(arrayOf("*/*")) }
                        )
                        1 -> LibraryScreen(
                            tracks = deviceTracks,
                            playlists = playlists,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            onRescanClick = { scope.launch { deviceTracks = scanDeviceMusic(context) } },
                            onTrackClick = { playTrack(it) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniPlayerBar(
                    track = currentTrack,
                    metadata = currentMetadata,
                    isPlaying = isPlaying,
                    currentPosMs = currentPositionMs,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    onBarClick = { if (currentTrack != null) showFullPlayerSheet = true },
                    onPlayPause = { togglePlayPause() }
                )

                FrostedDockBar(
                    selectedTab = selectedBottomTab,
                    cardShape = cardShape,
                    primaryColor = primaryColor,
                    onTabSelected = { selectedBottomTab = it },
                    onCreateClick = { showCreateStep1Sheet = true }
                )
            }

            // Плавный выезд меню "Создать -> Плейлист"
            AnimatedVisibility(
                visible = showCreateStep1Sheet,
                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                CreateMenuSheet(
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    onDismiss = { showCreateStep1Sheet = false },
                    onSelectPlaylist = {
                        showCreateStep1Sheet = false
                        pendingPlaylistCoverUri = null
                        showCreateStep2Dialog = true
                    }
                )
            }

            // Диалог выбора обложки и названия плейлиста
            if (showCreateStep2Dialog) {
                CreatePlaylistDetailsDialog(
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    selectedCoverUri = pendingPlaylistCoverUri,
                    onPickCoverClick = { coverPickerLauncher.launch(arrayOf("image/*")) },
                    onDismiss = { showCreateStep2Dialog = false },
                    onCreate = { name, coverUri ->
                        if (name.isNotBlank()) {
                            val updated = playlists + PlaylistItem(name.trim(), coverUri)
                            playlists = updated
                            StorageHelper.savePlaylists(prefs, updated)
                        }
                        showCreateStep2Dialog = false
                    }
                )
            }

            // Плавное полноэкранное открытие плеера (как на фото №5)
            AnimatedVisibility(
                visible = showFullPlayerSheet && currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(250)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(260)
                ) + fadeOut(tween(200))
            ) {
                currentTrack?.let { activeTrack ->
                    FullScreenPlayerOverlay(
                        track = activeTrack,
                        metadata = currentMetadata,
                        isPlaying = isPlaying,
                        currentPosMs = currentPositionMs,
                        cardShape = cardShape,
                        expressiveShape = expressiveShape,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        bgColor = bgColor,
                        onDismiss = { showFullPlayerSheet = false },
                        onPlayPause = { togglePlayPause() },
                        onSeek = { newPos ->
                            mediaPlayer.seekTo(newPos.toInt())
                            currentPositionMs = newPos
                        },
                        onNext = {
                            val idx = deviceTracks.indexOfFirst { it.id == activeTrack.id }
                            if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1])
                        },
                        onPrev = {
                            val idx = deviceTracks.indexOfFirst { it.id == activeTrack.id }
                            if (idx > 0) playTrack(deviceTracks[idx - 1])
                        }
                    )
                }
            }
        }
    }
}
