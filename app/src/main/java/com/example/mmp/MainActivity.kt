package com.example.mmp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MMPApp(onThemeLightChanged = { isLight ->
                val barStyle = if (isLight) {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            })
        }
    }
}

@Composable
fun MMPApp(onThemeLightChanged: (Boolean) -> Unit = {}) {
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

    var downloadingPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var downloadPlaylistCurrent by remember { mutableIntStateOf(0) }
    var downloadPlaylistTotal by remember { mutableIntStateOf(0) }

    var scSearchQuery by remember { mutableStateOf(prefs.getString("sc_saved_query", "") ?: "") }
    var scSearchResults by remember {
        val cachedJson = prefs.getString("sc_cached_results_json", null)
        val list = mutableListOf<SoundCloudTrack>()
        if (cachedJson != null) {
            try {
                val arr = JSONArray(cachedJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        SoundCloudTrack(
                            id = o.getLong("id"),
                            title = o.getString("title"),
                            artist = o.getString("artist"),
                            artworkUrl = o.optString("artworkUrl", "").takeIf { it.isNotBlank() },
                            durationMs = o.getLong("durationMs"),
                            streamProgressiveUrl = o.optString("streamUrl", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (_: Throwable) {}
        }
        mutableStateOf<List<SoundCloudTrack>>(list)
    }

    fun saveScSearchResults(results: List<SoundCloudTrack>) {
        try {
            val arr = JSONArray()
            for (item in results.take(40)) {
                val o = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("artworkUrl", item.artworkUrl ?: "")
                    put("durationMs", item.durationMs)
                    put("streamUrl", item.streamProgressiveUrl ?: "")
                }
                arr.put(o)
            }
            prefs.edit().putString("sc_cached_results_json", arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    var showAudioFxDialog by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("saved_speed", 1.0f)) }
    var playbackPitch by remember { mutableFloatStateOf(prefs.getFloat("saved_pitch", 1.0f)) }
    var bassBoostStrength by remember { mutableIntStateOf(prefs.getInt("saved_fx_bass", 0)) }
    var reverbPreset by remember { mutableIntStateOf(prefs.getInt("saved_fx_reverb", 0)) }

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
        if (granted) scope.launch { delay(700); rawDeviceTracks = scanDeviceMusic(context) }
    }

    val exoPlayer = remember {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentMetadata by remember { mutableStateOf(DetailedMetadata()) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMediaPlayerPrepared by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    var currentTrackPlayedSeconds by remember { mutableStateOf(0) }
    var currentTrackRecorded20Sec by remember { mutableStateOf(false) }
    var listenedSecondsTotal by remember { mutableStateOf(prefs.getInt("listened_seconds_total", 0)) }

    var currentQueue by remember { mutableStateOf(StorageHelper.loadLastQueue(prefs)) }
    var isShuffleEnabled by remember { mutableStateOf(prefs.getBoolean("saved_shuffle_enabled", false)) }
    var repeatMode by remember {
        val ord = prefs.getInt("saved_repeat_mode", 0)
        mutableStateOf(RepeatMode.entries.getOrElse(ord) { RepeatMode.OFF })
    }
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

    var activePlayJob by remember { mutableStateOf<Job?>(null) }
    var activeMetaJob by remember { mutableStateOf<Job?>(null) }
    var crossfadeTriggeredForTrackId by remember { mutableStateOf<Long?>(null) }

    val globalEqRef = remember { mutableStateOf<Equalizer?>(null) }
    val globalBassRef = remember { mutableStateOf<BassBoost?>(null) }
    val boundSessionId = remember { mutableIntStateOf(-1) }

    fun applySystemEqualizer(sessionId: Int) {
        val targetSession = if (sessionId > 0) sessionId else 0
        try {
            val enabled = prefs.getBoolean("eq_enabled", false)
            if (globalEqRef.value == null || boundSessionId.intValue != targetSession) {
                try { globalEqRef.value?.release() } catch (_: Throwable) {}
                try { globalBassRef.value?.release() } catch (_: Throwable) {}
                globalEqRef.value = Equalizer(0, targetSession)
                globalBassRef.value = BassBoost(0, targetSession)
                boundSessionId.intValue = targetSession
            }

            val eq = globalEqRef.value
            val bass = globalBassRef.value
            eq?.enabled = enabled
            bass?.enabled = (enabled || bassBoostStrength > 0)

            if (enabled && eq != null) {
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                val bands = listOf(
                    prefs.getFloat("eq_b0", 0f),
                    prefs.getFloat("eq_b1", 0f),
                    prefs.getFloat("eq_b2", 0f),
                    prefs.getFloat("eq_b3", 0f),
                    prefs.getFloat("eq_b4", 0f)
                )
                val numBands = eq.numberOfBands.toInt()
                for (b in 0 until numBands) {
                    val db = bands.getOrElse(b) { 0f }
                    val mb = (db * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(b.toShort(), mb)
                }
            }
            if (bass != null && bass.strengthSupported) {
                val prefBass = if (enabled) prefs.getInt("bass_boost_level", 0) * 10 else 0
                val effectiveBass = maxOf(bassBoostStrength, prefBass)
                bass.setStrength(effectiveBass.coerceIn(0, 1000).toShort())
            }
        } catch (_: Throwable) {}
    }

    DisposableEffect(Unit) {
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (key != null && (key.startsWith("eq_") || key == "bass_boost_level")) {
                applySystemEqualizer(exoPlayer.audioSessionId)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            try { globalEqRef.value?.release() } catch (_: Throwable) {}
            try { globalBassRef.value?.release() } catch (_: Throwable) {}
        }
    }

    val deviceTracks = remember(rawDeviceTracks, minDurationSec, excludedFolders, tagOverrides) {
        rawDeviceTracks
            .filter { it.durationMs >= minDurationSec * 1000L && !excludedFolders.contains(it.folderPath) }
            .map { applyOverridesToTrack(it, tagOverrides) }
    }

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

    var isServiceStarted by remember { mutableStateOf(false) }

    fun syncMediaService(track: Track?, meta: DetailedMetadata, playing: Boolean) {
        if (track == null) return
        try {
            PlayerBridge.currentTitle = track.title
            PlayerBridge.currentArtist = track.artist
            PlayerBridge.currentBitmap = meta.coverArt?.takeIf { !it.isRecycled }
            PlayerBridge.isPlaying = playing
            PlayerBridge.currentPositionMs = currentPositionMs
            PlayerBridge.durationMs = track.durationMs
            PlayerBridge.isShuffleEnabled = isShuffleEnabled
            PlayerBridge.repeatModeOrdinal = repeatMode.ordinal
            MMPWidgetProvider.updateAllWidgets(context)
            val intent = Intent(context, MediaPlaybackService::class.java).setAction("ACTION_UPDATE")
            if (playing || !isServiceStarted) {
                isServiceStarted = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (_: Throwable) {}
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
            val builtQueue = if (isShuffleEnabled) {
                listOf(track) + newQueue.filter { it.id != track.id }.shuffled()
            } else {
                newQueue
            }
            currentQueue = builtQueue
            StorageHelper.saveLastQueue(prefs, builtQueue)
        } else if (currentQueue.isEmpty()) {
            currentQueue = deviceTracks
            StorageHelper.saveLastQueue(prefs, deviceTracks)
        }

        activePlayJob?.cancel()
        activeMetaJob?.cancel()

        crossfadeTriggeredForTrackId = null
        currentTrack = track
        currentPositionMs = startOffsetMs
        currentTrackPlayedSeconds = 0
        currentTrackRecorded20Sec = false
        StorageHelper.saveLastPlayedState(prefs, track, startOffsetMs)

        activePlayJob = scope.launch {
            try {
                val rawUrl = track.filePath.ifBlank { track.uri.toString() }
                val finalUri = if (rawUrl.contains("api-v2.soundcloud.com")) {
                    val resolved = SoundCloudClient.resolveStreamUrl(rawUrl)
                    if (resolved != null) Uri.parse(resolved) else track.uri
                } else {
                    track.uri
                }

                val mediaItem = MediaItem.fromUri(finalUri)
                exoPlayer.setMediaItem(mediaItem, startOffsetMs.coerceAtLeast(0L))
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)

                val cfSec = prefs.getInt("crossfade_sec", 0)
                exoPlayer.volume = if (cfSec > 0 && startOffsetMs == 0L) 0.15f else 1f
                exoPlayer.prepare()
                exoPlayer.play()

                isMediaPlayerPrepared = true
                isPlaying = true
                syncMediaService(track, currentMetadata, true)
            } catch (e: Throwable) {
                isPlaying = false
                isMediaPlayerPrepared = false
                Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
            }
        }

        activeMetaJob = scope.launch {
            val meta = extractTrackMetadata(context, track)
            if (currentTrack?.id == track.id) {
                currentMetadata = meta
                syncMediaService(track, meta, isPlaying)
                if (meta.lyrics.isEmpty()) {
                    searchAndCacheOnlineLyrics(track, null, false)
                }
            }
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
                if (exoPlayer.playbackState == Player.STATE_READY) {
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                    syncMediaService(cur, currentMetadata, isPlaying)
                    return
                }
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

    fun safeSeekTo(targetMs: Long) {
        val cur = currentTrack ?: return
        val maxDur = cur.durationMs.coerceAtLeast(1L)
        val clamped = targetMs.coerceIn(0L, (maxDur - 300L).coerceAtLeast(0L))
        currentPositionMs = clamped
        StorageHelper.saveLastPlayedState(prefs, cur, clamped)
        if (isMediaPlayerPrepared) {
            try {
                val st = exoPlayer.playbackState
                if (st == Player.STATE_READY || st == Player.STATE_BUFFERING) {
                    val playerDur = exoPlayer.duration
                    val finalPos = if (playerDur > 0L) clamped.coerceIn(0L, (playerDur - 300L).coerceAtLeast(0L)) else clamped
                    exoPlayer.seekTo(finalPos)
                    syncMediaService(cur, currentMetadata, isPlaying)
                }
            } catch (_: Throwable) {}
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                val cur = currentTrack
                if (cur != null) {
                    syncMediaService(cur, currentMetadata, playing)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applySystemEqualizer(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isMediaPlayerPrepared = true
                    applySystemEqualizer(exoPlayer.audioSessionId)
                    currentTrack?.let { trk ->
                        val realDur = exoPlayer.duration
                        if (realDur > 1000L && trk.durationMs <= 0L) {
                            currentTrack = trk.copy(durationMs = realDur)
                        }
                        syncMediaService(currentTrack, currentMetadata, isPlaying)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (stopAfterCurrentTrack) {
                        stopAfterCurrentTrack = false
                        isPlaying = false
                        currentPositionMs = 0L
                        syncMediaService(currentTrack, currentMetadata, false)
                        return
                    }
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            try {
                                exoPlayer.seekTo(0L)
                                exoPlayer.volume = 1f
                                exoPlayer.play()
                                currentPositionMs = 0L
                            } catch (_: Throwable) {}
                        }
                        RepeatMode.ALL -> {
                            val cur = currentTrack
                            val idx = currentQueue.indexOfFirst { it.id == cur?.id }
                            if (idx != -1 && idx + 1 < currentQueue.size) {
                                playTrack(currentQueue[idx + 1])
                            } else if (currentQueue.isNotEmpty()) {
                                playTrack(currentQueue.first())
                            }
                        }
                        RepeatMode.OFF -> {
                            val cur = currentTrack
                            val idx = currentQueue.indexOfFirst { it.id == cur?.id }
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
                isMediaPlayerPrepared = false
                SoundCloudClient.clearCache()
                Toast.makeText(context, "Ошибка потока: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    fun setPlayingState(wantPlay: Boolean) {
        val cur = currentTrack
        if (cur != null) {
            if (!isMediaPlayerPrepared || exoPlayer.playbackState == Player.STATE_IDLE) {
                if (wantPlay) playTrack(cur, null, currentPositionMs)
                return
            }
            if (!wantPlay) {
                isPlaying = false
                try { exoPlayer.pause() } catch (_: Throwable) {}
                StorageHelper.saveLastPlayedState(prefs, cur, currentPositionMs)
                syncMediaService(cur, currentMetadata, false)
            } else {
                isPlaying = true
                try {
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0L)
                        currentPositionMs = 0L
                    }
                    exoPlayer.volume = 1f
                    exoPlayer.play()
                    syncMediaService(cur, currentMetadata, true)
                } catch (_: Throwable) {}
            }
        } else if (wantPlay && deviceTracks.isNotEmpty()) {
            playTrack(deviceTracks.first(), deviceTracks)
        }
    }

    fun togglePlayPause() {
        setPlayingState(!isPlaying)
    }

    fun toggleShuffle() {
        val newShuffle = !isShuffleEnabled
        isShuffleEnabled = newShuffle
        prefs.edit().putBoolean("saved_shuffle_enabled", newShuffle).apply()
        val cur = currentTrack
        if (newShuffle && currentQueue.size > 1) {
            val shuffled = if (cur != null) {
                listOf(cur) + currentQueue.filter { it.id != cur.id }.shuffled()
            } else {
                currentQueue.shuffled()
            }
            currentQueue = shuffled
            StorageHelper.saveLastQueue(prefs, shuffled)
        }
        syncMediaService(cur, currentMetadata, isPlaying)
    }

    fun cycleRepeatMode() {
        val nextMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatMode = nextMode
        prefs.edit().putInt("saved_repeat_mode", nextMode.ordinal).apply()
        syncMediaService(currentTrack, currentMetadata, isPlaying)
    }

    fun startMyWave5050() {
        Toast.makeText(context, "Настраиваем «Мою волну 50/50»...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val localPool = if (deviceTracks.isNotEmpty()) deviceTracks.shuffled().take(10) else emptyList()
            val favoriteArtist = artistSecondsAllTime.maxByOrNull { it.value }?.key
                ?: currentTrack?.let { splitArtists(it.artist).firstOrNull() }
                ?: "Hyperpop"

            val scCandidates = SoundCloudClient.searchTracks(favoriteArtist, limit = 25)
            val scPool = scCandidates.mapNotNull { scTrk ->
                val progUrl = scTrk.streamProgressiveUrl ?: return@mapNotNull null
                SoundCloudClient.toTrack(scTrk, progUrl)
            }.shuffled().take(10)

            val mixed = mutableListOf<Track>()
            val maxLen = maxOf(localPool.size, scPool.size)
            for (i in 0 until maxLen) {
                if (i < localPool.size) mixed.add(localPool[i])
                if (i < scPool.size) mixed.add(scPool[i])
            }

            if (mixed.isNotEmpty()) {
                playTrack(mixed.first(), mixed)
                Toast.makeText(context, "«Моя волна» играет!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не удалось сформировать волну", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SideEffect {
        PlayerBridge.onPlay = { setPlayingState(true) }
        PlayerBridge.onPause = { setPlayingState(false) }
        PlayerBridge.onToggle = { togglePlayPause() }
        PlayerBridge.onNext = { playNextTrack() }
        PlayerBridge.onPrev = { playPrevTrack() }
        PlayerBridge.onSeekTo = { pos -> safeSeekTo(pos) }
        PlayerBridge.onToggleShuffle = { toggleShuffle() }
        PlayerBridge.onCycleRepeat = { cycleRepeatMode() }
    }

    LaunchedEffect(sleepTimerRemainingSec) {
        if (sleepTimerRemainingSec > 0) {
            delay(1000L)
            sleepTimerRemainingSec -= 1
            if (sleepTimerRemainingSec == 0 && isPlaying) {
                setPlayingState(false)
                Toast.makeText(context, "Таймер сна остановил музыку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        var tickCounter = 0
        while (isPlaying && currentTrack != null) {
            delay(40L)
            if (isMediaPlayerPrepared && (try { exoPlayer.isPlaying } catch (_: Throwable) { false })) {
                val trk = currentTrack ?: break
                currentPositionMs = try { exoPlayer.currentPosition.coerceAtLeast(0L) } catch (_: Throwable) { currentPositionMs }
                PlayerBridge.currentPositionMs = currentPositionMs

                try {
                    val cfSec = prefs.getInt("crossfade_sec", 0)
                    val playerDur = exoPlayer.duration
                    val dur = if (playerDur > 1000L) playerDur else trk.durationMs.coerceAtLeast(1L)
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

                        if (rem in 1f..350f && crossfadeTriggeredForTrackId != trk.id && !stopAfterCurrentTrack) {
                            crossfadeTriggeredForTrackId = trk.id
                            when (repeatMode) {
                                RepeatMode.ONE -> {
                                    exoPlayer.seekTo(0L)
                                    currentPositionMs = 0L
                                    crossfadeTriggeredForTrackId = null
                                }
                                RepeatMode.ALL -> {
                                    val idx = currentQueue.indexOfFirst { it.id == trk.id }
                                    if (idx != -1 && idx + 1 < currentQueue.size) playTrack(currentQueue[idx + 1])
                                    else if (currentQueue.isNotEmpty()) playTrack(currentQueue.first())
                                }
                                RepeatMode.OFF -> {
                                    val idx = currentQueue.indexOfFirst { it.id == trk.id }
                                    if (idx != -1 && idx + 1 < currentQueue.size) playTrack(currentQueue[idx + 1])
                                }
                            }
                        }
                    } else if (exoPlayer.volume != 1f) {
                        exoPlayer.volume = 1f
                    }
                } catch (_: Throwable) {}

                tickCounter += 1
                if (tickCounter >= 25) {
                    tickCounter = 0
                    listenedSecondsTotal += 1
                    currentTrackPlayedSeconds += 1
                    val today = StorageHelper.getTodayDateString()
                    val todayRec = dailyStats.getOrPut(today) { DailyStatRecord() }

                    val artistsList = splitArtists(trk.artist)
                    for (art in artistsList) {
                        artistSecondsAllTime[art] = (artistSecondsAllTime[art] ?: 0) + 1
                        todayRec.artistSeconds[art] = (todayRec.artistSeconds[art] ?: 0) + 1
                    }
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

    DisposableEffect(Unit) { onDispose { try { exoPlayer.release() } catch (_: Throwable) {} } }

    var selectedBottomTab by remember { mutableIntStateOf(0) }
    var showCreateStep1Sheet by remember { mutableStateOf(false) }
    var showCreateStep2Dialog by remember { mutableStateOf(false) }
    var pendingPlaylistCoverUri by remember { mutableStateOf<String?>(null) }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistToEdit by remember { mutableStateOf<PlaylistItem?>(null) }

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
    LaunchedEffect(isLightMode) { onThemeLightChanged(isLightMode) }
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

    val allMergedTracks = remember(deviceTracks, savedScTracks) { (deviceTracks + savedScTracks).distinctBy { it.id } }

    MaterialTheme(colorScheme = if (isLightMode) lightColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor) else darkColorScheme(primary = primaryColor, secondary = secondaryColor, background = bgColor)) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            Box(modifier = Modifier.fillMaxSize().blur(themeConfig.blurIntensity.dp * 3)) {
                Box(modifier = Modifier.size(280.dp).offset((-50).dp, 40.dp).clip(CircleShape).background(primaryColor.copy(if (isLightMode) 0.16f else 0.24f)))
                Box(modifier = Modifier.size(320.dp).align(Alignment.BottomEnd).offset(60.dp, (-80).dp).clip(CircleShape).background(secondaryColor.copy(if (isLightMode) 0.12f else 0.18f)))
            }

            val hideDock = selectedBottomTab == 1 && (openedPlaylist != null || openedArtist != null || openedAlbum != null)

            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = if (hideDock) 82.dp else 152.dp)) {
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
                            onAddTrackToPlaylistClick = { tracksToAddToPlaylist = listOf(it) },
                            onStartMyWaveClick = { startMyWave5050() },
                            onExportTheme = { exportLauncher.launch("${themeConfig.themeName}.mmpui") },
                            onImportTheme = { importLauncher.launch(arrayOf("*/*")) }
                        )
                        1 -> LibraryScreen(
                            tracks = allMergedTracks,
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
                            onPlayContext = { queue, track -> playTrack(track, queue) },
                            onTogglePlay = { togglePlayPause() },
                            onToggleShuffle = { toggleShuffle() },
                            onAddTrackToPlaylistClick = { tracksToAddToPlaylist = listOf(it) },
                            onAddMultipleTracksToPlaylist = { tracksToAddToPlaylist = it },
                            onSetOpenedPlaylist = { openedPlaylist = it },
                            onSetOpenedArtist = { openedArtist = it },
                            onSetOpenedAlbum = { openedAlbum = it },
                            onEditPlaylist = { pl ->
                                pendingPlaylistCoverUri = pl.coverUri
                                playlistToEdit = pl
                            },
                            onCreateNewPlaylistClick = {
                                pendingPlaylistCoverUri = null
                                showCreateStep2Dialog = true
                            },
                            onRescanClick = {
                                Toast.makeText(context, "Обновляем медиатеку...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    rawDeviceTracks = scanDeviceMusic(context)
                                    Toast.makeText(context, "Медиатека обновлена!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        2 -> SoundCloudScreen(
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            likedScTracks = likedScTracks,
                            searchResults = scSearchResults,
                            searchQuery = scSearchQuery,
                            onQueryChange = {
                                scSearchQuery = it
                                prefs.edit().putString("sc_saved_query", it).apply()
                            },
                            onSearchCommit = { q, _ ->
                                scope.launch {
                                    val found = SoundCloudClient.searchTracks(q)
                                    scSearchResults = found
                                    saveScSearchResults(found)
                                }
                            },
                            onPlaySoundCloudTrack = { scTrack, allResults ->
                                val progUrl = scTrack.streamProgressiveUrl ?: return@SoundCloudScreen
                                Toast.makeText(context, "Подключаемся к SoundCloud...", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Не удалось получить аудиопоток", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Ошибка обновления ссылки", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDownloadScTrack = { scTrack ->
                                val progUrl = scTrack.streamProgressiveUrl ?: return@SoundCloudScreen
                                Toast.makeText(context, "Скачиваем «${scTrack.title}»...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val file = SoundCloudClient.downloadTrackToDevice(context, scTrack.title, scTrack.artist, progUrl)
                                    if (file != null) {
                                        Toast.makeText(context, "Сохранено: ${file.name}", Toast.LENGTH_LONG).show()
                                        delay(700)
                                        rawDeviceTracks = scanDeviceMusic(context)
                                    } else {
                                        Toast.makeText(context, "Не удалось скачать трек", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDownloadScPlaylist = { scPl ->
                                if (downloadingPlaylistTitle != null) {
                                    Toast.makeText(context, "Дождитесь завершения текущего скачивания", Toast.LENGTH_SHORT).show()
                                    return@SoundCloudScreen
                                }
                                downloadingPlaylistTitle = scPl.title
                                downloadPlaylistCurrent = 0
                                downloadPlaylistTotal = scPl.trackCount
                                scope.launch {
                                    val count = SoundCloudClient.downloadEntirePlaylist(context, scPl) { cur, tot ->
                                        downloadPlaylistCurrent = cur
                                        downloadPlaylistTotal = tot
                                    }
                                    downloadingPlaylistTitle = null
                                    Toast.makeText(context, "Плейлист сохранён: $count треков в Music/Playlists", Toast.LENGTH_LONG).show()
                                    delay(700)
                                    rawDeviceTracks = scanDeviceMusic(context)
                                }
                            },
                            downloadingPlaylistTitle = downloadingPlaylistTitle,
                            downloadPlaylistCurrent = downloadPlaylistCurrent,
                            downloadPlaylistTotal = downloadPlaylistTotal,
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
                                tracksToAddToPlaylist = listOf(trkObj)
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            tracksToAddToPlaylist = listOf(activeTrack)
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

                            Toast.makeText(context, "Скачиваем трек...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val file = SoundCloudClient.downloadTrackToDevice(context, activeTrack.title, activeTrack.artist, candidateUrl)
                                if (file != null) {
                                    Toast.makeText(context, "Трек сохранён: ${file.name}", Toast.LENGTH_LONG).show()
                                    delay(700)
                                    rawDeviceTracks = scanDeviceMusic(context)
                                } else {
                                    Toast.makeText(context, "Ошибка скачивания", Toast.LENGTH_SHORT).show()
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
                        onSeek = { newPos -> safeSeekTo(newPos) },
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
                        prefs.edit()
                            .putFloat("saved_speed", spd)
                            .putFloat("saved_pitch", ptc)
                            .putInt("saved_fx_bass", bass)
                            .putInt("saved_fx_reverb", rev)
                            .apply()
                        try {
                            exoPlayer.playbackParameters = PlaybackParameters(spd, ptc)
                            applySystemEqualizer(exoPlayer.audioSessionId)
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
                        StorageHelper.saveLastQueue(prefs, mutable)
                    },
                    onRemoveTrack = { idx ->
                        val mutable = currentQueue.toMutableList()
                        mutable.removeAt(idx)
                        currentQueue = mutable
                        StorageHelper.saveLastQueue(prefs, mutable)
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
                        StorageHelper.saveLastQueue(prefs, currentQueue)
                        trackToEditTags = null
                        Toast.makeText(context, "Сведения о треке обновлены!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (playlistToEdit != null) {
                val targetPl = playlistToEdit!!
                EditPlaylistDialog(
                    playlist = targetPl,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    selectedCoverUri = pendingPlaylistCoverUri,
                    onPickCoverClick = { coverPickerLauncher.launch(arrayOf("image/*")) },
                    onDismiss = { playlistToEdit = null },
                    onSave = { newName, newDesc, newCover ->
                        val updated = playlists.map { pl ->
                            if (pl.name == targetPl.name) pl.copy(name = newName, description = newDesc, coverUri = newCover) else pl
                        }
                        playlists = updated
                        StorageHelper.savePlaylists(prefs, updated)
                        if (openedPlaylist?.name == targetPl.name) {
                            openedPlaylist = targetPl.copy(name = newName, description = newDesc, coverUri = newCover)
                        }
                        playlistToEdit = null
                        Toast.makeText(context, "Плейлист обновлён!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            AnimatedVisibility(
                visible = tracksToAddToPlaylist.isNotEmpty(),
                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                AddToPlaylistSheet(
                    tracks = tracksToAddToPlaylist,
                    allTracks = allMergedTracks,
                    playlists = playlists,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onDismiss = { tracksToAddToPlaylist = emptyList() },
                    onSelectPlaylist = { selectedPl ->
                        val newIds = tracksToAddToPlaylist.map { it.id }
                        val updatedList = playlists.map { pl ->
                            if (pl.name == selectedPl.name) {
                                val merged = (pl.trackIds + newIds).distinct()
                                pl.copy(trackIds = merged)
                            } else pl
                        }
                        playlists = updatedList
                        StorageHelper.savePlaylists(prefs, updatedList)
                        Toast.makeText(context, "Добавлено в «${selectedPl.name}»!", Toast.LENGTH_SHORT).show()
                        tracksToAddToPlaylist = emptyList()
                    },
                    onCreateNewPlaylistClick = {
                        pendingPlaylistCoverUri = null
                        showCreateStep2Dialog = true
                    }
                )
            }

            AnimatedVisibility(
                visible = showCreateStep1Sheet,
                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                CreateMenuSheet(cardShape, expressiveShape, primaryColor, textColor, surfaceColor, { showCreateStep1Sheet = false }, { showCreateStep1Sheet = false; pendingPlaylistCoverUri = null; showCreateStep2Dialog = true })
            }

            if (showCreateStep2Dialog) {
                CreatePlaylistDetailsDialog(cardShape, expressiveShape, primaryColor, textColor, surfaceColor, pendingPlaylistCoverUri, { coverPickerLauncher.launch(arrayOf("image/*")) }, { showCreateStep2Dialog = false }, { name, desc, coverUri ->
                    if (name.isNotBlank()) {
                        val initIds = tracksToAddToPlaylist.map { it.id }
                        val newPl = PlaylistItem(name.trim(), coverUri, initIds, desc.trim())
                        val updated = playlists + newPl
                        playlists = updated
                        StorageHelper.savePlaylists(prefs, updated)
                        if (initIds.isNotEmpty()) {
                            Toast.makeText(context, "Плейлист «${name.trim()}» создан, треки добавлены!", Toast.LENGTH_SHORT).show()
                        }
                        tracksToAddToPlaylist = emptyList()
                    }
                    showCreateStep2Dialog = false
                })
            }
        }
    }
}
