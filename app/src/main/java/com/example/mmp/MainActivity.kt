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
    var rawDeviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var minDurationSec by remember { mutableStateOf(StorageHelper.loadMinDurationSec(prefs)) }
    var excludedFolders by remember { mutableStateOf(StorageHelper.loadExcludedFolders(prefs)) }
    var tagOverrides by remember { mutableStateOf(StorageHelper.loadTagOverrides(prefs)) }

    var recentTrackIds by remember { mutableStateOf(StorageHelper.loadRecentIds(prefs)) }
    var playlists by remember { mutableStateOf(StorageHelper.loadPlaylists(prefs)) }
    val artistSecondsAllTime = remember { mutableStateMapOf<String, Int>().apply { putAll(StorageHelper.loadArtistSeconds(prefs)) } }
    val dailyStats = remember { mutableStateMapOf<String, DailyStatRecord>().apply { putAll(StorageHelper.loadDailyStats(prefs)) } }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isBatteryIgnored by remember { mutableStateOf(prefs.getBoolean("battery_opt_dismissed", false) || powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) scope.launch { rawDeviceTracks = scanDeviceMusic(context) }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted
        if (granted) rawDeviceTracks = scanDeviceMusic(context) else permissionLauncher.launch(audioPermission)
    }

    // Применяем фильтр папок, минимальной длительности (5-60 сек) и переопределения тегов
    val deviceTracks = remember(rawDeviceTracks, minDurationSec, excludedFolders, tagOverrides) {
        rawDeviceTracks
            .filter { it.durationMs >= minDurationSec * 1000L && !excludedFolders.contains(it.folderPath) }
            .map { applyOverridesToTrack(it, tagOverrides) }
    }

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
    var currentTrackPlayedSeconds by remember { mutableStateOf(0) }
    var currentTrackRecorded20Sec by remember { mutableStateOf(false) }
    var listenedSecondsTotal by remember { mutableStateOf(prefs.getInt("listened_seconds_total", 0)) }

    var currentQueue by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var trackToEditTags by remember { mutableStateOf<Track?>(null) }
    var pendingTagCoverUri by remember { mutableStateOf<String?>(null) }

    var openedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var openedArtist by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<String?>(null) }

    fun syncMediaService(track: Track?, meta: DetailedMetadata, playing: Boolean) {
        if (track == null) return
        PlayerBridge.currentTitle = track.title
        PlayerBridge.currentArtist = track.artist
        PlayerBridge.currentBitmap = meta.coverArt
        PlayerBridge.isPlaying = playing
        try {
            val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (_: Throwable) {}
    }

    fun playTrack(track: Track, newQueue: List<Track>? = null) {
        if (newQueue != null) {
            currentQueue = if (isShuffleEnabled) {
                listOf(track) + newQueue.filter { it.id != track.id }.shuffled()
            } else {
                newQueue
            }
        } else if (currentQueue.isEmpty()) {
            currentQueue = deviceTracks
        }

        try {
            isPlaying = false
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, track.uri)
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentTrack = track
            currentPositionMs = 0L
            currentTrackPlayedSeconds = 0
            currentTrackRecorded20Sec = false
            isPlaying = true

            scope.launch {
                val meta = extractTrackMetadata(context, track)
                currentMetadata = meta
                syncMediaService(track, meta, true)
            }

            mediaPlayer.setOnCompletionListener {
                try {
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            mediaPlayer.seekTo(0)
                            mediaPlayer.start()
                            currentPositionMs = 0L
                            isPlaying = true
                        }
                        RepeatMode.ALL -> {
                            val idx = currentQueue.indexOfFirst { it.id == track.id }
                            if (idx != -1 && idx + 1 < currentQueue.size) {
                                playTrack(currentQueue[idx + 1])
                            } else if (currentQueue.isNotEmpty()) {
                                playTrack(currentQueue.first())
                            }
                        }
                        RepeatMode.OFF -> {
                            val idx = currentQueue.indexOfFirst { it.id == track.id }
                            if (idx != -1 && idx + 1 < currentQueue.size) {
                                playTrack(currentQueue[idx + 1])
                            } else {
                                // БЕЗОПАСНАЯ ОСТАНОВКА В КОНЦЕ АЛЬБОМА БЕЗ ВЫЛЕТА ПРИ ПОВТОРНОМ PLAY
                                isPlaying = false
                                currentPositionMs = 0L
                                try { mediaPlayer.seekTo(0) } catch (_: Throwable) {}
                                syncMediaService(currentTrack, currentMetadata, false)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    isPlaying = false
                }
            }
        } catch (e: Throwable) {
            isPlaying = false
            Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
        }
    }

    fun playNextTrack() {
        val cur = currentTrack ?: return
        if (currentQueue.isEmpty()) currentQueue = deviceTracks
        val idx = currentQueue.indexOfFirst { it.id == cur.id }
        if (idx != -1 && idx + 1 < currentQueue.size) {
            playTrack(currentQueue[idx + 1])
        } else if (repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty()) {
            playTrack(currentQueue.first())
        }
    }

    fun playPrevTrack() {
        val cur = currentTrack ?: return
        if (currentPositionMs > 4000L) {
            try {
                mediaPlayer.seekTo(0)
                currentPositionMs = 0L
                return
            } catch (_: Throwable) {}
        }
        if (currentQueue.isEmpty()) currentQueue = deviceTracks
        val idx = currentQueue.indexOfFirst { it.id == cur.id }
        if (idx > 0) {
            playTrack(currentQueue[idx - 1])
        } else if (repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty()) {
            playTrack(currentQueue.last())
        }
    }

    fun togglePlayPause() {
        val cur = currentTrack
        if (cur != null) {
            if (isPlaying) {
                try { if (mediaPlayer.isPlaying) mediaPlayer.pause() } catch (_: Throwable) {}
                isPlaying = false
                syncMediaService(cur, currentMetadata, false)
            } else {
                try {
                    val dur = cur.durationMs
                    val pos = try { mediaPlayer.currentPosition.toLong() } catch (_: Throwable) { 0L }
                    if (dur > 0 && pos >= dur - 800L) {
                        mediaPlayer.seekTo(0)
                        currentPositionMs = 0L
                    }
                    mediaPlayer.start()
                    isPlaying = true
                    syncMediaService(cur, currentMetadata, true)
                } catch (_: Throwable) {
                    // Если плеер был сброшен или завершён — перезапускаем трек начисто без краша
                    playTrack(cur)
                }
            }
        } else if (deviceTracks.isNotEmpty()) {
            playTrack(deviceTracks.first(), deviceTracks)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !isShuffleEnabled
        isShuffleEnabled = newShuffle
        val cur = currentTrack
        if (newShuffle && currentQueue.size > 1) {
            currentQueue = if (cur != null) {
                listOf(cur) + currentQueue.filter { it.id != cur.id }.shuffled()
            } else {
                currentQueue.shuffled()
            }
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    SideEffect {
        PlayerBridge.onPlayPause = { togglePlayPause() }
        PlayerBridge.onNext = { playNextTrack() }
        PlayerBridge.onPrev = { playPrevTrack() }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        while (isPlaying && currentTrack != null) {
            delay(1000L)
            if (try { mediaPlayer.isPlaying } catch (_: Throwable) { false }) {
                currentPositionMs = try { mediaPlayer.currentPosition.toLong() } catch (_: Throwable) { currentPositionMs }
                listenedSecondsTotal += 1
                currentTrackPlayedSeconds += 1
                val trk = currentTrack!!
                val today = StorageHelper.getTodayDateString()
                artistSecondsAllTime[trk.artist] = (artistSecondsAllTime[trk.artist] ?: 0) + 1
                val todayRec = dailyStats.getOrPut(today) { DailyStatRecord() }
                todayRec.artistSeconds[trk.artist] = (todayRec.artistSeconds[trk.artist] ?: 0) + 1
                dailyStats[today] = todayRec
                if (currentTrackPlayedSeconds >= 20 && !currentTrackRecorded20Sec) {
                    currentTrackRecorded20Sec = true
                    val updatedRecent = (listOf(trk.id) + recentTrackIds.filter { it != trk.id }).take(5)
                    recentTrackIds = updatedRecent
                    StorageHelper.saveRecentIds(prefs, updatedRecent)
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
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            pendingPlaylistCoverUri = it.toString()
        }
    }

    val tagCoverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            pendingTagCoverUri = it.toString()
        }
    }

    val cardShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toCardShape() }
    val expressiveShape = remember(themeConfig.shapeStyle) { themeConfig.shapeStyle.toExpressiveShape() }
    val effectivePrimaryHex = remember(themeConfig.themeMode, themeConfig.primaryHex, currentMetadata.coverArt) {
        if ((themeConfig.themeMode == ThemeMode.ADAPTIVE || themeConfig.themeMode == ThemeMode.ADAPTIVE_LIGHT) && currentMetadata.coverArt != null) {
            extractDominantColor(currentMetadata.coverArt, themeConfig.primaryHex)
        } else themeConfig.primaryHex
    }
    val primaryColor by animateColorAsState(Color(effectivePrimaryHex), tween(500), label = "")
    val secondaryColor = Color(themeConfig.secondaryHex)
    val isLightMode = themeConfig.themeMode == ThemeMode.LIGHT || themeConfig.themeMode == ThemeMode.ADAPTIVE_LIGHT
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
        prefs.edit().putBoolean("battery_opt_dismissed", true).apply()
        isBatteryIgnored = true
        try { if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } } catch (_: Throwable) {}
    }

    MaterialTheme(colorScheme = if (isLightMode) lightColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor) else darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor)) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            Box(modifier = Modifier.fillMaxSize().blur(themeConfig.blurIntensity.dp * 3)) {
                Box(modifier = Modifier.size(280.dp).offset((-50).dp, 40.dp).clip(CircleShape).background(primaryColor.copy(if (isLightMode) 0.16f else 0.24f)))
                Box(modifier = Modifier.size(320.dp).align(Alignment.BottomEnd).offset(60.dp, (-80).dp).clip(CircleShape).background(secondaryColor.copy(if (isLightMode) 0.12f else 0.18f)))
            }

            val hideDock = openedPlaylist != null || openedArtist != null || openedAlbum != null

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = if (hideDock) 92.dp else 162.dp)) {
                AnimatedContent(targetState = selectedBottomTab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "") { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            allRawTracks = rawDeviceTracks,
                            recentTracks = recentTracks,
                            artistSecondsAllTime = artistSecondsAllTime,
                            dailyStats = dailyStats,
                            listenedMinutesTotal = listenedSecondsTotal / 60,
                            firstListenDate = firstListenDate,
                            hasPermission = hasAudioPermission,
                            isBatteryIgnored = isBatteryIgnored,
                            minDurationSec = minDurationSec,
                            excludedFolders = excludedFolders,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            themeConfig = themeConfig,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            onRequestPermission = { permissionLauncher.launch(audioPermission) },
                            onRequestBatteryIgnore = { requestIgnoreBatteryOptimizations() },
                            onMinDurationChange = {
                                minDurationSec = it
                                StorageHelper.saveMinDurationSec(prefs, it)
                            },
                            onToggleFolderExclusion = { folder ->
                                val next = if (excludedFolders.contains(folder)) excludedFolders - folder else excludedFolders + folder
                                excludedFolders = next
                                StorageHelper.saveExcludedFolders(prefs, next)
                            },
                            onThemeChange = { themeConfig = it; StorageHelper.saveTheme(prefs, it) },
                            onTrackClick = { playTrack(it, deviceTracks) },
                            onAddTrackToPlaylistClick = { trackToAddToPlaylist = it },
                            onExportTheme = { exportLauncher.launch("${themeConfig.themeName}.mmpui") },
                            onImportTheme = { importLauncher.launch(arrayOf("*/*")) }
                        )
                        1 -> LibraryScreen(
                            tracks = deviceTracks,
                            playlists = playlists,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            bgColor = bgColor,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            isShuffleEnabled = isShuffleEnabled,
                            openedPlaylist = openedPlaylist,
                            openedArtist = openedArtist,
                            openedAlbum = openedAlbum,
                            onRescanClick = { scope.launch { rawDeviceTracks = scanDeviceMusic(context) } },
                            onPlayContext = { queue, track -> playTrack(track, queue) },
                            onTogglePlay = { togglePlayPause() },
                            onToggleShuffle = { toggleShuffle() },
                            onAddTrackToPlaylistClick = { trackToAddToPlaylist = it },
                            onSetOpenedPlaylist = { openedPlaylist = it },
                            onSetOpenedArtist = { openedArtist = it },
                            onSetOpenedAlbum = { openedAlbum = it }
                        )
                    }
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniPlayerBar(
                    track = currentTrack,
                    metadata = currentMetadata,
                    isPlaying = isPlaying,
                    currentPosMs = currentPositionMs,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onBarClick = { if (currentTrack != null) showFullPlayerSheet = true },
                    onPlayPause = { togglePlayPause() },
                    onSwipeNext = { playNextTrack() },
                    onSwipePrev = { playPrevTrack() }
                )

                if (!hideDock) {
                    FrostedDockBar(selectedBottomTab, cardShape, primaryColor, textColor, surfaceColor, { selectedBottomTab = it }, { showCreateStep1Sheet = true })
                }
            }

            AnimatedVisibility(
                visible = showFullPlayerSheet && currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260)) + fadeOut(tween(200))
            ) {
                currentTrack?.let { activeTrack ->
                    FullScreenPlayerOverlay(
                        track = activeTrack,
                        metadata = currentMetadata,
                        isPlaying = isPlaying,
                        currentPosMs = currentPositionMs,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        expressiveShape = expressiveShape,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        bgColor = bgColor,
                        textColor = textColor,
                        onDismiss = { showFullPlayerSheet = false },
                        onAddToPlaylistClick = { trackToAddToPlaylist = activeTrack },
                        onEditTagsClick = {
                            pendingTagCoverUri = activeTrack.customCoverUri
                            trackToEditTags = activeTrack
                        },
                        onOpenQueueClick = { showQueueSheet = true },
                        onToggleShuffle = { toggleShuffle() },
                        onToggleRepeat = { cycleRepeatMode() },
                        onPlayPause = { togglePlayPause() },
                        onSeek = { newPos -> try { mediaPlayer.seekTo(newPos.toInt()); currentPositionMs = newPos } catch (_: Throwable) {} },
                        onNext = { playNextTrack() },
                        onPrev = { playPrevTrack() }
                    )
                }
            }

            if (showQueueSheet) {
                QueueManagementSheet(
                    queue = currentQueue,
                    currentTrack = currentTrack,
                    cardShape = cardShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onDismiss = { showQueueSheet = false },
                    onSelectTrack = { playTrack(it) },
                    onMoveTrack = { from, to ->
                        val mutable = currentQueue.toMutableList()
                        val item = mutable.removeAt(from)
                        mutable.add(to, item)
                        currentQueue = mutable
                    },
                    onRemoveTrack = { idx ->
                        val mutable = currentQueue.toMutableList()
                        mutable.removeAt(idx)
                        currentQueue = mutable
                    }
                )
            }

            if (trackToEditTags != null) {
                val target = trackToEditTags!!
                EditTrackTagsDialog(
                    track = target,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    selectedCoverUri = pendingTagCoverUri,
                    onPickCoverClick = { tagCoverPickerLauncher.launch(arrayOf("image/*")) },
                    onDismiss = { trackToEditTags = null },
                    onSave = { newTitle, newArtist, newAlbum, newCover, newLyrics ->
                        val updatedMap = tagOverrides.toMutableMap()
                        updatedMap[target.id] = TrackTagOverride(newTitle, newArtist, newAlbum, newCover, newLyrics)
                        tagOverrides = updatedMap
                        StorageHelper.saveTagOverrides(prefs, updatedMap)
                        CoverCache.remove(target.id)
                        val updatedTrack = applyOverridesToTrack(target, updatedMap)
                        if (currentTrack?.id == target.id) {
                            currentTrack = updatedTrack
                            scope.launch { currentMetadata = extractTrackMetadata(context, updatedTrack) }
                        }
                        currentQueue = currentQueue.map { if (it.id == target.id) updatedTrack else it }
                        trackToEditTags = null
                        Toast.makeText(context, "Сведения о треке обновлены!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            AnimatedVisibility(
                visible = trackToAddToPlaylist != null,
                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                trackToAddToPlaylist?.let { targetTrack ->
                    AddToPlaylistSheet(targetTrack, playlists, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, { trackToAddToPlaylist = null }, { selectedPl ->
                        val updatedList = playlists.map { pl -> if (pl.name == selectedPl.name) pl.copy(trackIds = if (pl.trackIds.contains(targetTrack.id)) pl.trackIds else pl.trackIds + targetTrack.id) else pl }
                        playlists = updatedList; StorageHelper.savePlaylists(prefs, updatedList); Toast.makeText(context, "Добавлено в «${selectedPl.name}»!", Toast.LENGTH_SHORT).show(); trackToAddToPlaylist = null
                    }, { trackToAddToPlaylist = null; pendingPlaylistCoverUri = null; showCreateStep2Dialog = true })
                }
            }

            AnimatedVisibility(
                visible = showCreateStep1Sheet,
                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
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
