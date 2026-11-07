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
        setContent { MMPApp() }
    }
}

@Composable
fun MMPApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
    val firstListenDate = remember { StorageHelper.getOrInitFirstListenDate(prefs) }
    var themeConfig by remember { mutableStateOf(StorageHelper.loadTheme(prefs)) }
    var deviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentTrackIds by remember { mutableStateOf(StorageHelper.loadRecentIds(prefs)) }
    var playlists by remember { mutableStateOf(StorageHelper.loadPlaylists(prefs)) }
    val artistSecondsAllTime = remember { mutableStateMapOf<String, Int>().apply { putAll(StorageHelper.loadArtistSeconds(prefs)) } }
    val trackPlaysAllTime = remember { mutableStateMapOf<Long, Int>().apply { putAll(StorageHelper.loadTrackPlayCounts(prefs)) } }
    val dailyStats = remember { mutableStateMapOf<String, DailyStatRecord>().apply { putAll(StorageHelper.loadDailyStats(prefs)) } }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isBatteryIgnored by remember { mutableStateOf(prefs.getBoolean("battery_opt_dismissed", false) || powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) scope.launch { deviceTracks = scanDeviceMusic(context) }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted
        if (granted) deviceTracks = scanDeviceMusic(context) else permissionLauncher.launch(audioPermission)
    }

    val recentTracks = remember(deviceTracks, recentTrackIds) { val trackMap = deviceTracks.associateBy { it.id }; recentTrackIds.mapNotNull { trackMap[it] } }
    val mediaPlayer = remember { MediaPlayer() }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentMetadata by remember { mutableStateOf(DetailedMetadata()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    var currentTrackPlayedSeconds by remember { mutableStateOf(0) }
    var currentTrackRecorded20Sec by remember { mutableStateOf(false) }
    var listenedSecondsTotal by remember { mutableStateOf(prefs.getInt("listened_seconds_total", 0)) }

    fun syncMediaService(track: Track?, meta: DetailedMetadata, playing: Boolean) {
        if (track == null) return
        PlayerBridge.currentTitle = track.title; PlayerBridge.currentArtist = track.artist; PlayerBridge.currentBitmap = meta.coverArt; PlayerBridge.isPlaying = playing
        try {
            val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (_: Throwable) {}
    }

    fun playTrack(track: Track) {
        try {
            isPlaying = false; mediaPlayer.reset(); mediaPlayer.setDataSource(context, track.uri); mediaPlayer.prepare(); mediaPlayer.start()
            currentTrack = track; currentPositionMs = 0L; currentTrackPlayedSeconds = 0; currentTrackRecorded20Sec = false; isPlaying = true
            scope.launch { val meta = extractTrackMetadata(context, track); currentMetadata = meta; syncMediaService(track, meta, true) }
            mediaPlayer.setOnCompletionListener {
                val idx = deviceTracks.indexOfFirst { it.id == track.id }
                if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1]) else { isPlaying = false; syncMediaService(currentTrack, currentMetadata, false) }
            }
        } catch (e: Throwable) { isPlaying = false; Toast.makeText(context, "Не удалось воспроизвести файл", Toast.LENGTH_SHORT).show() }
    }

    fun togglePlayPause() {
        try {
            if (currentTrack != null) {
                if (isPlaying) { if (mediaPlayer.isPlaying) mediaPlayer.pause(); isPlaying = false } else { mediaPlayer.start(); isPlaying = true }
                syncMediaService(currentTrack, currentMetadata, isPlaying)
            } else if (deviceTracks.isNotEmpty()) playTrack(deviceTracks.first())
        } catch (_: Throwable) {}
    }

    SideEffect {
        PlayerBridge.onPlayPause = { togglePlayPause() }
        PlayerBridge.onNext = { currentTrack?.let { cur -> val idx = deviceTracks.indexOfFirst { it.id == cur.id }; if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1]) } }
        PlayerBridge.onPrev = { currentTrack?.let { cur -> val idx = deviceTracks.indexOfFirst { it.id == cur.id }; if (idx > 0) playTrack(deviceTracks[idx - 1]) } }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        while (isPlaying && currentTrack != null) {
            delay(1000L)
            if (try { mediaPlayer.isPlaying } catch (_: Throwable) { false }) {
                currentPositionMs = try { mediaPlayer.currentPosition.toLong() } catch (_: Throwable) { currentPositionMs }
                listenedSecondsTotal += 1; currentTrackPlayedSeconds += 1
                val trk = currentTrack!!; val today = StorageHelper.getTodayDateString()
                artistSecondsAllTime[trk.artist] = (artistSecondsAllTime[trk.artist] ?: 0) + 1
                val todayRec = dailyStats.getOrPut(today) { DailyStatRecord() }
                todayRec.artistSeconds[trk.artist] = (todayRec.artistSeconds[trk.artist] ?: 0) + 1
                dailyStats[today] = todayRec
                if (currentTrackPlayedSeconds >= 20 && !currentTrackRecorded20Sec) {
                    currentTrackRecorded20Sec = true
                    val updatedRecent = (listOf(trk.id) + recentTrackIds.filter { it != trk.id }).take(5)
                    recentTrackIds = updatedRecent; StorageHelper.saveRecentIds(prefs, updatedRecent)
                    trackPlaysAllTime[trk.id] = (trackPlaysAllTime[trk.id] ?: 0) + 1
                    todayRec.trackPlays[trk.id] = (todayRec.trackPlays[trk.id] ?: 0) + 1
                    dailyStats[today] = todayRec; StorageHelper.saveTrackPlayCounts(prefs, trackPlaysAllTime.toMap())
                }
                if (listenedSecondsTotal % 5 == 0) {
                    prefs.edit().putInt("listened_seconds_total", listenedSecondsTotal).apply()
                    StorageHelper.saveArtistSeconds(prefs, artistSecondsAllTime.toMap())
                    StorageHelper.saveDailyStats(prefs, dailyStats.toMap())
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { try { mediaPlayer.release() } catch (_: Throwable) {} } }

    var selectedBottomTab by remember { mutableStateOf(0) }
    var showCreateStep1Sheet by remember { mutableStateOf(false) }
    var showCreateStep2Dialog by remember { mutableStateOf(false) }
    var pendingPlaylistCoverUri by remember { mutableStateOf<String?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}; pendingPlaylistCoverUri = it.toString() }
    }

    val cardShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toCardShape() }
    val expressiveShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toExpressiveShape() }
    val effectivePrimaryHex = remember(themeConfig.themeMode, themeConfig.primaryHex, currentMetadata.coverArt) {
        if (themeConfig.themeMode == ThemeMode.ADAPTIVE && currentMetadata.coverArt != null) extractDominantColor(currentMetadata.coverArt, themeConfig.primaryHex) else themeConfig.primaryHex
    }
    val primaryColor by animateColorAsState(Color(effectivePrimaryHex), tween(500), label = "")
    val secondaryColor = Color(themeConfig.secondaryHex)
    val isLightMode = themeConfig.themeMode == ThemeMode.LIGHT
    val bgColor by animateColorAsState(if (isLightMode) Color(0xFFF6F5FA) else Color(0xFF0D0D12), tween(400), label = "")
    val textColor = if (isLightMode) Color(0xFF14141C) else Color.White
    val surfaceColor = if (isLightMode) Color(0xFFE9E7F2) else Color(0xFF1A1A26)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(themeConfig.toJson().toByteArray()) }; Toast.makeText(context, "Тема .mmpui сохранена!", Toast.LENGTH_SHORT).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            content?.let { jsonStr ->
                MMPThemeConfig.fromJson(jsonStr)?.let { loaded -> themeConfig = loaded; StorageHelper.saveTheme(prefs, loaded); Toast.makeText(context, "Тема применена!", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        prefs.edit().putBoolean("battery_opt_dismissed", true).apply(); isBatteryIgnored = true
        try { if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } } catch (_: Throwable) {}
    }

    MaterialTheme(colorScheme = if (isLightMode) lightColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor) else darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor)) {
        // Z-INDEX FIX: Box wraps EVERYTHING
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            
            // 1. Блюр фона
            Box(modifier = Modifier.fillMaxSize().blur(themeConfig.blurIntensity.dp * 3)) {
                Box(modifier = Modifier.size(280.dp).offset((-50).dp, 40.dp).clip(CircleShape).background(primaryColor.copy(if (isLightMode) 0.18f else 0.28f)))
                Box(modifier = Modifier.size(320.dp).align(Alignment.BottomEnd).offset(60.dp, (-80).dp).clip(CircleShape).background(secondaryColor.copy(if (isLightMode) 0.14f else 0.22f)))
            }

            // 2. Основные экраны (Главная / Медиатека)
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 162.dp)) {
                AnimatedContent(targetState = selectedBottomTab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "") { tab ->
                    when (tab) {
                        0 -> HomeScreen(deviceTracks, recentTracks, artistSecondsAllTime, trackPlaysAllTime, dailyStats, listenedSecondsTotal / 60, firstListenDate, hasAudioPermission, isBatteryIgnored, cardShape, expressiveShape, themeConfig, primaryColor, textColor, surfaceColor, { permissionLauncher.launch(audioPermission) }, { requestIgnoreBatteryOptimizations() }, { themeConfig = it; StorageHelper.saveTheme(prefs, it) }, { playTrack(it) }, { trackToAddToPlaylist = it }, { exportLauncher.launch("${themeConfig.themeName}.mmpui") }, { importLauncher.launch(arrayOf("*/*")) })
                        1 -> LibraryScreen(deviceTracks, playlists, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, bgColor, { scope.launch { deviceTracks = scanDeviceMusic(context) } }, { playTrack(it) }, { trackToAddToPlaylist = it })
                    }
                }
            }

            // 3. Мини-плеер и док-панель
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniPlayerBar(currentTrack, currentMetadata, isPlaying, currentPositionMs, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, { if (currentTrack != null) showFullPlayerSheet = true }, { togglePlayPause() })
                FrostedDockBar(selectedBottomTab, cardShape, primaryColor, textColor, surfaceColor, { selectedBottomTab = it }, { showCreateStep1Sheet = true })
            }

            // 4. Полноэкранный плеер (ВЫШЕ дока, НИЖЕ модальных окон)
            AnimatedVisibility(visible = showFullPlayerSheet && currentTrack != null, enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)), exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260)) + fadeOut(tween(200))) {
                currentTrack?.let { activeTrack -> FullScreenPlayerOverlay(activeTrack, currentMetadata, isPlaying, currentPositionMs, expressiveShape, primaryColor, secondaryColor, bgColor, textColor, { showFullPlayerSheet = false }, { trackToAddToPlaylist = activeTrack }, { togglePlayPause() }, { newPos -> try { mediaPlayer.seekTo(newPos.toInt()); currentPositionMs = newPos } catch (_: Throwable) {} }, { val idx = deviceTracks.indexOfFirst { it.id == activeTrack.id }; if (idx != -1 && idx + 1 < deviceTracks.size) playTrack(deviceTracks[idx + 1]) }, { val idx = deviceTracks.indexOfFirst { it.id == activeTrack.id }; if (idx > 0) playTrack(deviceTracks[idx - 1]) }) }
            }

            // 5. МОДАЛЬНЫЕ ОКНА (НА САМОМ ВЕРХУ - Z-INDEX FIX)
            AnimatedVisibility(visible = trackToAddToPlaylist != null, enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)), exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })) {
                trackToAddToPlaylist?.let { targetTrack ->
                    AddToPlaylistSheet(targetTrack, playlists, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, { trackToAddToPlaylist = null }, { selectedPl ->
                        val updatedList = playlists.map { pl -> if (pl.name == selectedPl.name) pl.copy(trackIds = if (pl.trackIds.contains(targetTrack.id)) pl.trackIds else pl.trackIds + targetTrack.id) else pl }
                        playlists = updatedList; StorageHelper.savePlaylists(prefs, updatedList); Toast.makeText(context, "Добавлено в «${selectedPl.name}»!", Toast.LENGTH_SHORT).show(); trackToAddToPlaylist = null
                    }, { trackToAddToPlaylist = null; pendingPlaylistCoverUri = null; showCreateStep2Dialog = true })
                }
            }

            AnimatedVisibility(visible = showCreateStep1Sheet, enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)), exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })) {
                CreateMenuSheet(cardShape, expressiveShape, primaryColor, textColor, surfaceColor, { showCreateStep1Sheet = false }, { showCreateStep1Sheet = false; pendingPlaylistCoverUri = null; showCreateStep2Dialog = true })
            }

            if (showCreateStep2Dialog) {
                CreatePlaylistDetailsDialog(cardShape, expressiveShape, primaryColor, textColor, surfaceColor, pendingPlaylistCoverUri, { coverPickerLauncher.launch(arrayOf("image/*")) }, { showCreateStep2Dialog = false }, { name, coverUri ->
                    if (name.isNotBlank()) { val updated = playlists + PlaylistItem(name.trim(), coverUri, emptyList()); playlists = updated; StorageHelper.savePlaylists(prefs, updated) }
                    showCreateStep2Dialog = false
                })
            }
        }
    }
}
