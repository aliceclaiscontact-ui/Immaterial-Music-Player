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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import android.media.audiofx.BassBoost
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
    var savedScTracks by remember { mutableStateOf<List<Track>>(StorageHelper.loadSoundCloudTracks(prefs)) }
    var likedScTracks by remember {
        val rawLiked = prefs.getString("liked_sc_tracks_v1", null)
        val initialList: List<Track> = if (rawLiked != null) {
            StorageHelper.loadSoundCloudTracks(prefs).filter { trk -> rawLiked.contains(trk.id.toString()) }
        } else {
            emptyList()
        }
        mutableStateOf<List<Track>>(initialList)
    }
    var showAudioFxDialog by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var playbackPitch by remember { mutableFloatStateOf(1.0f) }
    var bassBoostStrength by remember { mutableIntStateOf(0) }
    var reverbPreset by remember { mutableIntStateOf(0) }
    val bassBoostEffect = remember { mutableStateOf<BassBoost?>(null) }
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
        if (granted) scope.launch { kotlinx.coroutines.delay(700)
                                        rawDeviceTracks = scanDeviceMusic(context) }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // Реальный звуковой движок: 5-полосный эквалайзер + BassBoost, реагирующий на настройки в реальном времени
    DisposableEffect(exoPlayer) {
        var sysEq: android.media.audiofx.Equalizer? = null
        var sysBass: android.media.audiofx.BassBoost? = null

        fun applySoundSettings() {
            try {
                val sessionId = exoPlayer.audioSessionId
                if (sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
                if (sysEq == null) {
                    sysEq = android.media.audiofx.Equalizer(0, sessionId)
                }
                if (sysBass == null) {
                    sysBass = android.media.audiofx.BassBoost(0, sessionId)
                }
                val enabled = prefs.getBoolean("eq_enabled", false)
                sysEq?.enabled = enabled
                sysBass?.enabled = enabled
                if (enabled) {
                    val eq = sysEq
                    if (eq != null) {
                        val range = eq.bandLevelRange
                        val minMb = range[0].toInt()
                        val maxMb = range[1].toInt()
                        val numBands = eq.numberOfBands.toInt()
                        val userBandsDb = listOf(
                            prefs.getFloat("eq_b0", 0f),
                            prefs.getFloat("eq_b1", 0f),
                            prefs.getFloat("eq_b2", 0f),
                            prefs.getFloat("eq_b3", 0f),
                            prefs.getFloat("eq_b4", 0f)
                        )
                        for (b in 0 until numBands) {
                            val db = userBandsDb.getOrElse(b) { 0f }
                            val targetMb = (db * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                            eq.setBandLevel(b.toShort(), targetMb)
                        }
                    }
                    val bassLvl = (prefs.getInt("bass_boost_level", 0) * 10).coerceIn(0, 1000).toShort()
                    if (sysBass?.strengthSupported == true) {
                        sysBass?.setStrength(bassLvl)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                try { sysEq?.release(); sysBass?.release() } catch (_: Throwable) {}
                sysEq = null; sysBass = null
                applySoundSettings()
            }
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                if (isPlayingNow) applySoundSettings()
            }
        }
        exoPlayer.addListener(listener)

        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("eq_") || key == "bass_boost_level")) {
                applySoundSettings()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        applySoundSettings()

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            exoPlayer.removeListener(listener)
            try { sysEq?.release() } catch (_: Throwable) {}
            try { sysBass?.release() } catch (_: Throwable) {}
        }
    }
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
        try {
            PlayerBridge.currentTitle = track.title
            PlayerBridge.currentArtist = track.artist
            PlayerBridge.currentBitmap = meta.coverArt?.takeIf { !it.isRecycled }
            PlayerBridge.isPlaying = playing
            MMPWidgetProvider.updateAllWidgets(context)
            val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
            if (playing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    val focusChangeListener = remember {
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    wasPlayingBeforeFocusLoss = false
                    try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (e: Throwable) {}
                    isPlaying = false
                    syncMediaService(currentTrack, currentMetadata, false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (isPlaying) {
                        wasPlayingBeforeFocusLoss = true
                        try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (e: Throwable) {}
                        isPlaying = false
                        syncMediaService(currentTrack, currentMetadata, false)
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    try { exoPlayer.setVolume(0.25f) } catch (e: Throwable) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    try { exoPlayer.setVolume(1.0f) } catch (e: Throwable) {}
                    if (wasPlayingBeforeFocusLoss && currentTrack != null && isMediaPlayerPrepared) {
                        wasPlayingBeforeFocusLoss = false
                        try {
                            exoPlayer.play()
                            isPlaying = true
                            syncMediaService(currentTrack, currentMetadata, true)
                        } catch (e: Throwable) {}
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
        } catch (e: Throwable) { true }
    }

    DisposableEffect(context) {
        val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                    try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (e: Throwable) {}
                    isPlaying = false
                    syncMediaService(currentTrack, currentMetadata, false)
                }
            }
        }
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        onDispose {
            try { context.unregisterReceiver(noisyReceiver) } catch (e: Throwable) {}
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

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            scope.launch {
                val rawUrl = track.filePath.ifBlank { track.uri.toString() }
                val finalUri = if (rawUrl.contains("api-v2.soundcloud.com")) {
                    val resolved = SoundCloudClient.resolveStreamUrl(rawUrl)
                    if (resolved != null) Uri.parse(resolved) else track.uri
                } else {
                    track.uri
                }
                val mediaItem = MediaItem.fromUri(finalUri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)
                exoPlayer.prepare()
                if (startOffsetMs > 0L && startOffsetMs < track.durationMs - 1000L) {
                    exoPlayer.seekTo(startOffsetMs)
                }
                exoPlayer.play()
                isMediaPlayerPrepared = true
                isPlaying = true
            }
            syncMediaService(track, currentMetadata, true)

            scope.launch {
                val meta = extractTrackMetadata(context, track)
                currentMetadata = meta
                syncMediaService(track, meta, true)
                if (meta.lyrics.isEmpty()) {
                    searchAndCacheOnlineLyrics(track, null, false)
                }
            }

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (stopAfterCurrentTrack) {
                            stopAfterCurrentTrack = false
                            isPlaying = false
                            currentPositionMs = 0L
                            syncMediaService(currentTrack, currentMetadata, false)
                            return
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
                                    syncMediaService(currentTrack, currentMetadata, false)
                                }
                            }
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isPlaying = false
                    Toast.makeText(context, "Ошибка потока ExoPlayer: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
                }
            })
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
            } catch (e: Throwable) {}
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
                try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (e: Throwable) {}
                isPlaying = false
                StorageHelper.saveLastPlayedState(prefs, cur, currentPositionMs)
                syncMediaService(cur, currentMetadata, false)
            } else {
                try {
                    requestAudioFocus()
                    if (exoPlayer.playbackState == Player.STATE_IDLE) {
                        exoPlayer.prepare()
                    } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0L)
                        currentPositionMs = 0L
                    }
                    exoPlayer.volume = 1f
                    exoPlayer.play()
                    isPlaying = true
                    syncMediaService(cur, currentMetadata, true)
                } catch (e: Throwable) {
                    e.printStackTrace()
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
                try { if (exoPlayer.isPlaying) exoPlayer.pause() } catch (e: Throwable) {}
                isPlaying = false
                syncMediaService(currentTrack, currentMetadata, false)
                Toast.makeText(context, "Таймер сна остановил музыку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        var tickCounter = 0
        while (isPlaying && currentTrack != null) {
            delay(40L)
            if (isMediaPlayerPrepared && (try { exoPlayer.isPlaying } catch (e: Throwable) { false })) {
                currentPositionMs = try { exoPlayer.currentPosition.coerceAtLeast(0L) } catch (e: Throwable) { currentPositionMs }
                // Плавное затухание и нарастание (Crossfade)
                try {
                    val cfSec = prefs.getInt("crossfade_sec", 0)
                    val dur = (currentTrack?.durationMs ?: 0L).coerceAtLeast(1L)
                    if (cfSec > 0 && dur > cfSec * 2000L) {
                        val cfMs = cfSec * 1000f
                        val pos = currentPositionMs.toFloat()
                        val rem = (dur - currentPositionMs).toFloat()
                        val targetVol = when {
                            pos < cfMs -> (pos / cfMs).coerceIn(0.15f, 1f)
                            rem < cfMs -> (rem / cfMs).coerceIn(0.05f, 1f)
                            else -> 1f
                        }
                        exoPlayer.volume = targetVol
                    } else if (exoPlayer.volume != 1f) {
                        exoPlayer.volume = 1f
                    }
                } catch (_: Throwable) {}
                tickCounter += 1
                if (tickCounter >= 25) {
                    tickCounter = 0
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
    }

    DisposableEffect(Unit) { onDispose { try { exoPlayer.release() } catch (e: Throwable) {} } }

    var selectedBottomTab by remember { mutableStateOf(0) }
    var showCreateStep1Sheet by remember { mutableStateOf(false) }
    var showCreateStep2Dialog by remember { mutableStateOf(false) }
    var pendingPlaylistCoverUri by remember { mutableStateOf<String?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Throwable) {}
            pendingPlaylistCoverUri = it.toString()
        }
    }

    val tagCoverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Throwable) {}
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
        try { if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } } catch (e: Throwable) {}
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
                            tracks = (deviceTracks + savedScTracks).distinctBy { it.id },
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
                            onRescanClick = { scope.launch { kotlinx.coroutines.delay(700)
                                        rawDeviceTracks = scanDeviceMusic(context) } },
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
                            likedScTracks = likedScTracks,
                            onPlaySoundCloudTrack = { scTrack, allResults ->
                                val progUrl = scTrack.streamProgressiveUrl ?: return@SoundCloudScreen
                                Toast.makeText(context, "Подключаемся к SoundCloud: ${scTrack.title}...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val directUrl = SoundCloudClient.resolveStreamUrl(progUrl)
                                    if (directUrl != null) {
                                        val playable = SoundCloudClient.toTrack(scTrack, progUrl).copy(uri = Uri.parse(directUrl))
                                        val queueTracks = allResults.mapNotNull { item ->
                                            val itemUrl = item.streamProgressiveUrl ?: return@mapNotNull null
                                            if (item.id == scTrack.id) playable else SoundCloudClient.toTrack(item, itemUrl)
                                        }
                                        if (savedScTracks.none { trk -> trk.id == playable.id }) {
                                            val updated = savedScTracks + playable
                                            savedScTracks = updated
                                            StorageHelper.saveSoundCloudTracks(prefs, updated)
                                        }
                                        playTrack(playable, queueTracks.ifEmpty { listOf(playable) })
                                    } else {
                                        Toast.makeText(context, "Не удалось получить аудиопоток SoundCloud", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onPlaySavedScTrack = { savedTrk ->
                                scope.launch {
                                    val metaUrl = savedTrk.filePath.ifBlank { savedTrk.uri.toString() }
                                    val freshDirect = if (metaUrl.contains("api-v2.soundcloud.com")) SoundCloudClient.resolveStreamUrl(metaUrl) else metaUrl
                                    if (freshDirect != null) {
                                        val readyTrack = savedTrk.copy(uri = Uri.parse(freshDirect))
                                        playTrack(readyTrack, likedScTracks.ifEmpty { listOf(readyTrack) })
                                    } else {
                                        Toast.makeText(context, "Ошибка обновления ссылки SoundCloud", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDownloadScTrack = { scTrack ->
                                val progUrl = scTrack.streamProgressiveUrl ?: return@SoundCloudScreen
                                Toast.makeText(context, "Скачиваем «${scTrack.title}» в память...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val file = SoundCloudClient.downloadTrackToDevice(context, scTrack.title, scTrack.artist, progUrl)
                                    if (file != null) {
                                        Toast.makeText(context, "Сохранено в Music/MMP_SoundCloud: ${file.name}", Toast.LENGTH_LONG).show()
                                        kotlinx.coroutines.delay(700)
                                        rawDeviceTracks = scanDeviceMusic(context)
                                    } else {
                                        Toast.makeText(context, "Не удалось скачать трек", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onToggleLikeScTrack = { scTrack ->
                                val progUrl = scTrack.streamProgressiveUrl ?: ""
                                val trkObj = SoundCloudClient.toTrack(scTrack, progUrl)
                                if (savedScTracks.none { trk -> trk.id == trkObj.id }) {
                                    val upd = savedScTracks + trkObj
                                    savedScTracks = upd
                                    StorageHelper.saveSoundCloudTracks(prefs, upd)
                                }
                                val exists = likedScTracks.any { trk -> trk.id == trkObj.id }
                                val nextLiked = if (exists) likedScTracks.filterNot { trk -> trk.id == trkObj.id } else likedScTracks + trkObj
                                likedScTracks = nextLiked
                                prefs.edit().putString("liked_sc_tracks_v1", nextLiked.joinToString(",") { trk -> trk.id.toString() }).apply()
                            },
                            onAddScToPlaylist = { scTrack ->
                                val progUrl = scTrack.streamProgressiveUrl ?: ""
                                val trkObj = SoundCloudClient.toTrack(scTrack, progUrl)
                                if (savedScTracks.none { trk -> trk.id == trkObj.id }) {
                                    val upd = savedScTracks + trkObj
                                    savedScTracks = upd
                                    StorageHelper.saveSoundCloudTracks(prefs, upd)
                                }
                                trackToAddToPlaylist = trkObj
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
                        onAddToPlaylistClick = {
                            if (activeTrack.folderName == "SoundCloud" && savedScTracks.none { trk -> trk.id == activeTrack.id }) {
                                val upd = savedScTracks + activeTrack
                                savedScTracks = upd
                                StorageHelper.saveSoundCloudTracks(prefs, upd)
                            }
                            trackToAddToPlaylist = activeTrack
                        },
                        onEditTagsClick = {
                            pendingTagCoverUri = activeTrack.customCoverUri
                            trackToEditTags = activeTrack
                        },
                        onOpenSleepTimerClick = { showSleepTimerDialog = true },
                        onOpenAudioFxClick = { showAudioFxDialog = true },
                        onDownloadCurrentScTrack = {
                            val savedMatch = savedScTracks.firstOrNull { it.id == activeTrack.id || (it.title == activeTrack.title && it.artist == activeTrack.artist) }
                            val candidateUrl = listOf(
                                savedMatch?.filePath,
                                activeTrack.filePath,
                                activeTrack.uri.toString()
                            ).firstOrNull { !it.isNullOrBlank() && it.contains("api-v2.soundcloud.com") }
                                ?: savedMatch?.filePath?.takeIf { it.isNotBlank() }
                                ?: activeTrack.filePath.ifBlank { activeTrack.uri.toString() }

                            Toast.makeText(context, "Скачиваем «${activeTrack.title}» в папку Music/MMP_SoundCloud...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val file = SoundCloudClient.downloadTrackToDevice(context, activeTrack.title, activeTrack.artist, candidateUrl)
                                if (file != null) {
                                    Toast.makeText(context, "Трек сохранён: ${file.name}", Toast.LENGTH_LONG).show()
                                    kotlinx.coroutines.delay(700)
                                    rawDeviceTracks = scanDeviceMusic(context)
                                } else {
                                    Toast.makeText(context, "Ошибка скачивания трека", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSearchOnlineLyricsClick = { customQuery ->
                            searchAndCacheOnlineLyrics(activeTrack, customQuery, true)
                        },
                        onOpenQueueClick = { showQueueSheet = true },
                        onToggleShuffle = { toggleShuffle() },
                        onToggleRepeat = { cycleRepeatMode() },
                        onPlayPause = { togglePlayPause() },
                        onSeek = { newPos ->
                            val safeMax = activeTrack.durationMs.coerceAtLeast(1000L)
                            val clampedPos = newPos.coerceIn(0L, safeMax - 250L)
                            currentPositionMs = clampedPos
                            if (isMediaPlayerPrepared) {
                                try {
                                    val dur = exoPlayer.duration
                                    val target = if (dur > 0L) clampedPos.coerceIn(0L, (dur - 200L).coerceAtLeast(0L)) else clampedPos
                                    exoPlayer.seekTo(target)
                                } catch (e: Throwable) {}
                            }
                        },
                        onNext = { playNextTrack() },
                        onPrev = { playPrevTrack() }
                    )
                }
            }

            if (showAudioFxDialog) {
                AudioFxDialog(
                    playbackSpeed = playbackSpeed,
                    playbackPitch = playbackPitch,
                    bassBoostStrength = bassBoostStrength,
                    reverbPreset = reverbPreset,
                    cardShape = cardShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onDismiss = { showAudioFxDialog = false },
                    onApplyFx = { spd, ptc, bass, rev ->
                        playbackSpeed = spd
                        playbackPitch = ptc
                        bassBoostStrength = bass
                        reverbPreset = rev
                        try {
                            exoPlayer.playbackParameters = PlaybackParameters(spd, ptc)
                            val sessionId = exoPlayer.audioSessionId
                            if (sessionId != 0) {
                                if (bassBoostEffect.value == null) {
                                    bassBoostEffect.value = BassBoost(0, sessionId).apply { enabled = true }
                                }
                                bassBoostEffect.value?.setStrength(bass.toShort())
                            }
                        } catch (_: Throwable) {}
                    }
                )
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