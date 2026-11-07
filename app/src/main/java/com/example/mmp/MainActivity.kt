package com.example.mmp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val firstLaunchDate = remember { StorageHelper.getOrInitFirstLaunchDate(context, prefs) }
    var themeConfig by remember { mutableStateOf(StorageHelper.loadTheme(prefs)) }
    var rawDeviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var minDurationSec by remember { mutableStateOf(StorageHelper.loadMinDurationSec(prefs)) }
    var excludedFolders by remember { mutableStateOf(StorageHelper.loadExcludedFolders(prefs)) }
    var tagOverrides by remember { mutableStateOf(StorageHelper.loadTagOverrides(prefs)) }

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
        if (granted) scope.launch { rawDeviceTracks = scanDeviceMusic(context) }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentMetadata by remember { mutableStateOf(DetailedMetadata()) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMediaPlayerPrepared by remember { mutableStateOf(false) }
    var wasPlayingBeforeFocusLoss by remember { mutableStateOf(false) }
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

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerRemainingSec by remember { mutableStateOf(0) }
    var stopAfterCurrentTrack by remember { mutableStateOf(false) }
    var isSearchingOnlineLyrics by remember { mutableStateOf(false) }

    var openedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var openedArtist by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<String?>(null) }

    val deviceTracks = remember(rawDeviceTracks, minDurationSec, excludedFolders, tagOverrides) {
        rawDeviceTracks
            .filter { it.durationMs >= minDurationSec * 1000L && !excludedFolders.contains(it.folderPath) }
            .map { applyOverridesToTrack(it, tagOverrides) }
    }

    // При старте приложения сканируем музыку и восстанавливаем последний игравший трек в мини-плеер
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted
        if (granted) {
            val scanned = scanDeviceMusic(context)
            rawDeviceTracks = scanned
        } else {
            permissionLauncher.launch(audioPermission)
        }

        val lastSaved = StorageHelper.loadLastPlayedState(prefs)
        if (lastSaved != null && currentTrack == null) {
            val (savedTrack, savedPos) = lastSaved
            val updatedTrack = applyOverridesToTrack(savedTrack, tagOverrides)
            currentTrack = updatedTrack
            currentPositionMs = savedPos
            isMediaPlayerPrepared = false
            currentMetadata = extractTrackMetadata(context, updatedTrack)
        }
    }

    fun syncMediaService(track: Track?, meta: DetailedMetadata, playing: Boolean) {
        if (track == null) return
        PlayerBridge.currentTitle = track.title
        PlayerBridge.currentArtist = track.artist
        PlayerBridge.currentBitmap = meta.coverArt
        PlayerBridge.isPlaying = playing
        MMPWidgetProvider.updateAllWidgets(context)
        try {
            val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (_: Throwable) {}
    }

    val focusChangeListener = remember {
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    wasPlayingBeforeFocusLoss = false
                    try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (_: Throwable) {}
                    isPlaying = false
                    syncMediaService(currentTrack, currentMetadata, false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (isPlaying) {
                        wasPlayingBeforeFocusLoss = true
                        try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (_: Throwable) {}
                        isPlaying = false
                        syncMediaService(currentTrack, currentMetadata, false)
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    try { exoPlayer.volume = 0.25f } catch (_: Throwable) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    try { exoPlayer.volume = 1.0f } catch (_: Throwable) {}
                    if (wasPlayingBeforeFocusLoss && currentTrack != null && isMediaPlayerPrepared) {
                        wasPlayingBeforeFocusLoss = false
                        try {
                            exoPlayer.play()
                            isPlaying = true
                            syncMediaService(currentTrack, currentMetadata, true)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Throwable) { true }
    }

    DisposableEffect(context) {
        val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                    try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (_: Throwable) {}
                    isPlaying = false
                    syncMediaService(currentTrack, currentMetadata, false)
                }
            }
        }
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        onDispose {
            try { context.unregisterReceiver(noisyReceiver) } catch (_: Throwable) {}
        }
    }

    fun searchAndCacheOnlineLyrics(track: Track, customQuery: String? = null, showFeedback: Boolean = false) {
        scope.launch {
            isSearchingOnlineLyrics = true
            val foundLrc = fetchLyricsFromLrclib(track.artist, track.title, track.durationMs, customQuery)
            isSearchingOnlineLyrics = false
            if (!foundLrc.isNullOrBlank()) {
                val updatedMap = tagOverrides.toMutableMap()
                val existing = updatedMap[track.id]
                updatedMap[track.id] = TrackTagOverride(
                    title = existing?.title ?: track.title,
                    artist = existing?.artist ?: track.artist,
                    album = existing?.album ?: track.album,
                    coverUri = existing?.coverUri ?: track.customCoverUri,
                    lyrics = foundLrc
                )
                tagOverrides = updatedMap
                StorageHelper.saveTagOverrides(prefs, updatedMap)
                val parsed = parseLrcText(foundLrc)
                if (currentTrack?.id == track.id) {
                    currentTrack = applyOverridesToTrack(track, updatedMap)
                    currentMetadata = currentMetadata.copy(lyrics = parsed)
                }
                if (showFeedback) {
                    Toast.makeText(context, "Текст найден в базе LRCLIB и сохранён!", Toast.LENGTH_SHORT).show()
                }
            } else if (showFeedback) {
                Toast.makeText(context, "Текст в базе LRCLIB не найден", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track>? = null, startOffsetMs: Long = 0L) {
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
            requestAudioFocus()
            isPlaying = false
            isMediaPlayerPrepared = false
            currentTrack = track
            currentPositionMs = startOffsetMs
            currentTrackPlayedSeconds = 0
            currentTrackRecorded20Sec = false
            StorageHelper.saveLastPlayedState(prefs, track, startOffsetMs)

            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            if (track.uri.scheme?.startsWith("http") == true) {
                val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                mediaPlayer.setDataSource(context, track.uri, headers)
            } else {
                mediaPlayer.setDataSource(context, track.uri)
            }

            mediaPlayer.setOnErrorListener { _, what, extra ->
                isPlaying = false
                isMediaPlayerPrepared = false
                Toast.makeText(context, "Ошибка потока: $what / $extra", Toast.LENGTH_SHORT).show()
                true
            }

            mediaPlayer.setOnPreparedListener { mp ->
                isMediaPlayerPrepared = true
                if (startOffsetMs > 0L && startOffsetMs < track.durationMs - 1000L) {
                    try { mp.seekTo(startOffsetMs.toInt()) } catch (_: Throwable) {}
                }
                mp.setVolume(1.0f, 1.0f)
                mp.start()
                isPlaying = true
                syncMediaService(track, currentMetadata, true)
            }

            mediaPlayer.prepareAsync()

            scope.launch {
                val meta = extractTrackMetadata(context, track)
                currentMetadata = meta
                syncMediaService(track, meta, true)
                if (meta.lyrics.isEmpty()) {
                    searchAndCacheOnlineLyrics(track, null, false)
                }
            }

            mediaPlayer.setOnCompletionListener {
                try {
                    if (stopAfterCurrentTrack) {
                        stopAfterCurrentTrack = false
                        isPlaying = false
                        currentPositionMs = 0L
                        try { exoPlayer.seekTo(0L) } catch (_: Throwable) {}
                        syncMediaService(currentTrack, currentMetadata, false)
                        return@setOnCompletionListener
                    }

                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            exoPlayer.seekTo(0L)
                            exoPlayer.play()
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
                                isPlaying = false
                                currentPositionMs = 0L
                                try { exoPlayer.seekTo(0L) } catch (_: Throwable) {}
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
        if (currentPositionMs > 4000L && isMediaPlayerPrepared) {
            try {
                exoPlayer.seekTo(0L)
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
            if (!isMediaPlayerPrepared) {
                playTrack(cur, null, currentPositionMs)
                return
            }
            if (isPlaying) {
                try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (_: Throwable) {}
                isPlaying = false
                StorageHelper.saveLastPlayedState(prefs, cur, currentPositionMs)
                syncMediaService(cur, currentMetadata, false)
            } else {
                try {
                    requestAudioFocus()
                    val dur = cur.durationMs
                    val pos = try { exoPlayer.currentPosition } catch (_: Throwable) { 0L }
                    if (dur > 0 && pos >= dur - 800L) {
                        exoPlayer.seekTo(0L)
                        currentPositionMs = 0L
                    }
                    exoPlayer.play()
                    isPlaying = true
                    syncMediaService(cur, currentMetadata, true)
                } catch (_: Throwable) {
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

    LaunchedEffect(sleepTimerRemainingSec) {
        if (sleepTimerRemainingSec > 0) {
            delay(1000L)
            sleepTimerRemainingSec -= 1
            if (sleepTimerRemainingSec == 0 && isPlaying) {
                try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (_: Throwable) {}
                isPlaying = false
                syncMediaService(currentTrack, currentMetadata, false)
                Toast.makeText(context, "Таймер сна остановил музыку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        while (isPlaying && currentTrack != null) {
            delay(1000L)
            if (isMediaPlayerPrepared && (try { exoPlayer.isPlaying } catch (_: Throwable) { false })) {
                currentPositionMs = try { exoPlayer.currentPosition } catch (_: Throwable) { currentPositionMs }
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
                    trackPlaysAllTime[trk.id] = (trackPlaysAllTime[trk.id] ?: 0) + 1
                    todayRec.trackPlays[trk.id] = (todayRec.trackPlays[trk.id] ?: 0) + 1
                    dailyStats[today] = todayRec
                    StorageHelper.saveTrackPlayCounts(prefs, trackPlaysAllTime.toMap())
                }

                if (listenedSecondsTotal % 5 == 0) {
                    prefs.edit().putInt("listened_seconds_total", listenedSecondsTotal).apply()
                    StorageHelper.saveArtistSeconds(prefs, artistSecondsAllTime.toMap())
                    StorageHelper.saveDailyStats(prefs, dailyStats.toMap())
                    StorageHelper.saveLastPlayedState(prefs, trk, currentPositionMs)
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { try { exoPlayer.release() } catch (_: Throwable) {} } }

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

            val hideDock = selectedBottomTab == 1 && (openedPlaylist != null || openedArtist != null || openedAlbum != null)

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = if (hideDock) 92.dp else 162.dp)) {
                AnimatedContent(targetState = selectedBottomTab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "") { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            allRawTracks = rawDeviceTracks,
                            deviceTracks = deviceTracks,
                            trackPlaysAllTime = trackPlaysAllTime,
                            artistSecondsAllTime = artistSecondsAllTime,
                            dailyStats = dailyStats,
                            listenedMinutesTotal = listenedSecondsTotal / 60,
                            firstLaunchDate = firstLaunchDate,
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
                        2 -> SoundCloudScreen(
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            onPlaySoundCloudTrack = { scTrack ->
                                val progUrl = scTrack.streamProgressiveUrl
                                if (progUrl == null) {
                                    Toast.makeText(context, "Нет ссылки на поток", Toast.LENGTH_SHORT).show()
                                    return@SoundCloudScreen
                                }
                                Toast.makeText(context, "Загрузка потока...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val directUrl = SoundCloudClient.resolveStreamUrl(progUrl)
                                    if (!directUrl.isNullOrBlank()) {
                                        val playable = SoundCloudClient.toTrack(scTrack, directUrl)
                                        playTrack(playable, listOf(playable))
                                    } else {
                                        Toast.makeText(context, "Поток недоступен для воспроизведения", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
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
                        sleepTimerRemainingSec = sleepTimerRemainingSec,
                        stopAfterCurrentTrack = stopAfterCurrentTrack,
                        isSearchingOnlineLyrics = isSearchingOnlineLyrics,
                        cardShape = cardShape,
                        expressiveShape = expressiveShape,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        bgColor = bgColor,
                        textColor = textColor,
                        surfaceColor = surfaceColor,
                        onDismiss = { showFullPlayerSheet = false },
                        onAddToPlaylistClick = { trackToAddToPlaylist = activeTrack },
                        onEditTagsClick = {
                            pendingTagCoverUri = activeTrack.customCoverUri
                            trackToEditTags = activeTrack
                        },
                        onOpenSleepTimerClick = { showSleepTimerDialog = true },
                        onSearchOnlineLyricsClick = { customQuery ->
                            searchAndCacheOnlineLyrics(activeTrack, customQuery, true)
                        },
                        onOpenQueueClick = { showQueueSheet = true },
                        onToggleShuffle = { toggleShuffle() },
                        onToggleRepeat = { cycleRepeatMode() },
                        onPlayPause = { togglePlayPause() },
                        onSeek = { newPos ->
                            currentPositionMs = newPos
                            if (isMediaPlayerPrepared) {
                                try { exoPlayer.seekTo(newPos) } catch (_: Throwable) {}
                            }
                        },
                        onNext = { playNextTrack() },
                        onPrev = { playPrevTrack() }
                    )
                }
            }

            if (showSleepTimerDialog) {
                CurvedSleepTimerDialog(
                    currentMinutes = (sleepTimerRemainingSec + 59) / 60,
                    stopAfterCurrentTrack = stopAfterCurrentTrack,
                    cardShape = cardShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onDismiss = { showSleepTimerDialog = false },
                    onApplyTimer = { mins, stopEnd ->
                        sleepTimerRemainingSec = mins * 60
                        stopAfterCurrentTrack = stopEnd
                        showSleepTimerDialog = false
                        val msg = when {
                            mins > 0 -> "Таймер сна запущен на $mins мин"
                            stopEnd -> "Плеер остановится после конца трека"
                            else -> "Таймер сна отключён"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
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
