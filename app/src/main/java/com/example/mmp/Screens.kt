package com.example.mmp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun TrackCoverThumb(track: Track, shape: Shape, primaryColor: Color, sizeDp: Int = 48) {
    val context = LocalContext.current
    var bitmap by remember(track.id, track.customCoverUri) { mutableStateOf(CoverCache.get(track.id)) }
    LaunchedEffect(track.id, track.customCoverUri) {
        bitmap = loadTrackThumbnail(context, track)
    }
    Box(modifier = Modifier.size(sizeDp.dp).clip(shape).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = primaryColor, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

@Composable
fun PlaylistGridCover(trackIds: List<Long>, allTracks: List<Track>, primaryColor: Color, sizeDp: Int = 54) {
    val context = LocalContext.current
    val tracks = remember(trackIds, allTracks) { val map = allTracks.associateBy { it.id }; trackIds.mapNotNull { map[it] }.take(4) }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null, null, null) }
    LaunchedEffect(tracks) { tracks.forEachIndexed { index, track -> bitmaps[index] = loadTrackThumbnail(context, track) } }

    Box(modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp)).background(primaryColor.copy(0.2f))) {
        if (tracks.isEmpty()) {
            Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.align(Alignment.Center))
        } else if (tracks.size < 4) {
            if (bitmaps[0] != null) Image(bitmaps[0]!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) { if (bitmaps[0] != null) Image(bitmaps[0]!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    Box(modifier = Modifier.weight(1f)) { if (bitmaps[1] != null) Image(bitmaps[1]!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) { if (bitmaps[2] != null) Image(bitmaps[2]!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    Box(modifier = Modifier.weight(1f)) { if (bitmaps[3] != null) Image(bitmaps[3]!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    allRawTracks: List<Track>,
    recentTracks: List<Track>,
    artistSecondsAllTime: Map<String, Int>,
    dailyStats: Map<String, DailyStatRecord>,
    listenedMinutesTotal: Int,
    firstListenDate: String,
    hasPermission: Boolean,
    isBatteryIgnored: Boolean,
    minDurationSec: Int,
    excludedFolders: Set<String>,
    cardShape: Shape,
    expressiveShape: Shape,
    themeConfig: MMPThemeConfig,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onRequestPermission: () -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onMinDurationChange: (Int) -> Unit,
    onToggleFolderExclusion: (String) -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onExportTheme: () -> Unit,
    onImportTheme: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    var selectedDateFilter by remember { mutableStateOf("ALL") }
    val availableDates = run { listOf("ALL") + dailyStats.keys.sortedDescending() }
    val activeArtistSeconds = run { if (selectedDateFilter == "ALL") artistSecondsAllTime else dailyStats[selectedDateFilter]?.artistSeconds ?: emptyMap() }
    val totalArtistSec = activeArtistSeconds.values.sum()
    val topArtistEntry = activeArtistSeconds.maxByOrNull { it.value }
    val topArtistPercent = if (totalArtistSec > 0 && topArtistEntry != null) ((topArtistEntry.value.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100) else 0

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ExpressiveFilterPill("История прослушивания", activeSubTab == 0, primaryColor, textColor) { activeSubTab = 0 }
            IconButton(
                onClick = { activeSubTab = if (activeSubTab == 1) 0 else 1 },
                modifier = Modifier.size(42.dp).clip(CircleShape).background(if (activeSubTab == 1) primaryColor else surfaceColor).border(1.dp, if (activeSubTab == 1) primaryColor else textColor.copy(0.15f), CircleShape)
            ) {
                Icon(Icons.Filled.Settings, "Настройки", tint = if (activeSubTab == 1) Color.Black else textColor)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(targetState = activeSubTab, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) }, label = "") { subTab ->
            if (subTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    if (!hasPermission) {
                        item {
                            Button(onClick = onRequestPermission, shape = cardShape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), modifier = Modifier.fillMaxWidth()) {
                                Text("Разрешить доступ к музыке", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.AutoAwesome, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("СВЕДЕНИЯ О ПРОСЛУШИВАНИИ", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (topArtistEntry != null && totalArtistSec > 0) {
                                            Text(
                                                buildAnnotatedString {
                                                    append("Артист ")
                                                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Black)) { append(topArtistEntry.key) }
                                                    append(" играл $topArtistPercent% твоей музыки!")
                                                },
                                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor, lineHeight = 22.sp
                                            )
                                        } else {
                                            Text("Слушай любимые треки, чтобы увидеть главного артиста!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.22f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Audiotrack, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(primaryColor.copy(0.14f)).border(1.dp, primaryColor.copy(0.35f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.GraphicEq, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("$listenedMinutesTotal минут прослушано музыки с $firstListenDate", color = textColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column {
                            Text("Статистика по датам", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableDates) { dateKey ->
                                    ExpressiveFilterPill(if (dateKey == "ALL") "За всё время" else dateKey, selectedDateFilter == dateKey, primaryColor, textColor) { selectedDateFilter = dateKey }
                                }
                            }
                        }
                    }

                    if (activeArtistSeconds.isNotEmpty() && totalArtistSec > 0) {
                        item {
                            val sortedArtists = activeArtistSeconds.entries.sortedByDescending { it.value }.take(5)
                            Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Процент времени по артистам", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                sortedArtists.forEach { (artist, sec) ->
                                    val pct = ((sec.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(artist, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$pct% (${sec / 60}м ${sec % 60}с)", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    item { Text("Последние 5 треков (от 20 сек)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor) }
                    if (recentTracks.isEmpty()) {
                        item { Text("Здесь появятся треки, которые ты слушала дольше 20 секунд.", color = textColor.copy(0.55f), fontSize = 13.sp) }
                    } else {
                        items(recentTracks.take(5), key = { it.id }) { track ->
                            TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onTrackClick(track) }
                        }
                    }
                }
            } else {
                SettingsAndThemeEditor(
                    allRawTracks = allRawTracks,
                    themeConfig = themeConfig,
                    isBatteryIgnored = isBatteryIgnored,
                    minDurationSec = minDurationSec,
                    excludedFolders = excludedFolders,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onMinDurationChange = onMinDurationChange,
                    onToggleFolderExclusion = onToggleFolderExclusion,
                    onThemeChange = onThemeChange,
                    onRequestBatteryIgnore = onRequestBatteryIgnore,
                    onExport = onExportTheme,
                    onImport = onImportTheme
                )
            }
        }
    }
}

// -----------------------------------------------------
// ЧИСТЫЙ ДИЗАЙН ПЛЕЙЛИСТА И АЛЬБОМА (БЕЗ ГРАДИЕНТА И ЛИШНИХ КНОПОК)
// -----------------------------------------------------
@Composable
fun CleanHeaderLayout(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    customCoverUri: String?,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    expressiveShape: Shape,
    currentTrack: Track?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    onBack: () -> Unit,
    onPlayContext: (List<Track>, Track) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(title, customCoverUri) { mutableStateOf<Bitmap?>(null) }
    val isContextPlaying = isPlaying && currentTrack != null && tracks.any { it.id == currentTrack.id }

    LaunchedEffect(tracks, customCoverUri) {
        if (customCoverUri != null) {
            try { context.contentResolver.openInputStream(Uri.parse(customCoverUri))?.use { heroBitmap = BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
        } else {
            for (trk in tracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(surfaceColor)
                ) {
                    Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(expressiveShape)
                        .background(surfaceColor)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (heroBitmap != null) Image(heroBitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else if (customCoverUri == null && tracks.isNotEmpty()) PlaylistGridCover(tracks.map { it.id }, tracks, primaryColor, 210)
                    else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(90.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(title, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = textColor.copy(0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onToggleShuffle()
                            if (!isContextPlaying && tracks.isNotEmpty()) {
                                onPlayContext(tracks.shuffled(), tracks.random())
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isShuffleEnabled) primaryColor.copy(0.22f) else surfaceColor)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) primaryColor else textColor.copy(0.75f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .clickable {
                                if (isContextPlaying) onTogglePlay()
                                else if (tracks.isNotEmpty()) onPlayContext(tracks, tracks.first())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isContextPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("В этом списке пока нет треков.", color = textColor.copy(0.6f)) } }
        } else {
            items(tracks, key = { it.id }) { track ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TrackListItem(track, RoundedCornerShape(16.dp), expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onPlayContext(tracks, track) }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

// -----------------------------------------------------
// СТРАНИЦА АВТОРА В СТИЛЕ APPLE MUSIC С РАЗМЫТИЕМ И ЖИВЫМ PLAY/PAUSE
// -----------------------------------------------------
@Composable
fun AppleMusicArtistScreen(
    artistName: String,
    artistTracks: List<Track>,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    currentTrack: Track?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    onBack: () -> Unit,
    onPlayContext: (List<Track>, Track) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(artistName) { mutableStateOf<Bitmap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val isArtistPlaying = isPlaying && currentTrack != null && artistTracks.any { it.id == currentTrack.id }

    LaunchedEffect(artistTracks) {
        for (trk in artistTracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
    }

    val filteredTracks = run { if (searchQuery.isBlank()) artistTracks else artistTracks.filter { it.title.contains(searchQuery, ignoreCase = true) } }

    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(410.dp)) {
                if (heroBitmap != null) {
                    // Фоновый размытый слой для мягкого свечения
                    Image(
                        bitmap = heroBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(28.dp),
                        alpha = 0.7f
                    )
                    // Чёткий портрет поверх
                    Image(
                        bitmap = heroBitmap!!.asImageBitmap(),
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(surfaceColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = textColor.copy(0.3f), modifier = Modifier.size(110.dp))
                    }
                }
                // Плавный уход в цвет фона снизу как в Apple Music
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(0.25f), Color.Transparent, bgColor.copy(0.88f), bgColor)
                            )
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(0.45f))) {
                        Icon(Icons.Filled.ArrowBackIosNew, "Назад", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(artistName, color = textColor, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onToggleShuffle()
                                if (artistTracks.isNotEmpty()) onPlayContext(artistTracks.shuffled(), artistTracks.random())
                            }
                        ) {
                            Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (isShuffleEnabled) primaryColor else textColor, modifier = Modifier.size(28.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                                .clickable {
                                    if (isArtistPlaying) onTogglePlay()
                                    else if (artistTracks.isNotEmpty()) onPlayContext(artistTracks, artistTracks.first())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isArtistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Filled.Search, "Search", tint = textColor, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        if (showSearchBar) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск трека у $artistName...", color = textColor.copy(0.5f)) },
                    singleLine = true,
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Songs", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.6f))
            }
        }

        items(filteredTracks, key = { it.id }) { track ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onPlayContext(artistTracks, track) }
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    playlists: List<PlaylistItem>,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    currentTrack: Track?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    openedPlaylist: PlaylistItem?,
    openedArtist: String?,
    openedAlbum: String?,
    onRescanClick: () -> Unit,
    onPlayContext: (List<Track>, Track) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onSetOpenedPlaylist: (PlaylistItem?) -> Unit,
    onSetOpenedArtist: (String?) -> Unit,
    onSetOpenedAlbum: (String?) -> Unit
) {
    var category by remember { mutableStateOf(0) }
    val context = LocalContext.current

    if (openedArtist != null) {
        val artistTracks = run { tracks.filter { it.artist.equals(openedArtist, ignoreCase = true) } }
        AppleMusicArtistScreen(openedArtist, artistTracks, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, bgColor, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedArtist(null) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick)
        return
    }

    if (openedPlaylist != null) {
        val currentPl = playlists.find { it.name == openedPlaylist.name } ?: openedPlaylist
        val plTracks = run { val map = tracks.associateBy { it.id }; currentPl.trackIds.mapNotNull { map[it] } }
        CleanHeaderLayout(currentPl.name, "Ваш плейлист • ${plTracks.size} треков", plTracks, currentPl.coverUri, primaryColor, textColor, surfaceColor, bgColor, expressiveShape, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedPlaylist(null) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick)
        return
    }

    if (openedAlbum != null) {
        val albumTracks = run { tracks.filter { it.album == openedAlbum } }
        val artistName = albumTracks.firstOrNull()?.artist ?: "Неизвестный исполнитель"
        CleanHeaderLayout(openedAlbum, "Альбом • $artistName", albumTracks, null, primaryColor, textColor, surfaceColor, bgColor, expressiveShape, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedAlbum(null) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Моя медиатека", fontSize = 28.sp, fontWeight = FontWeight.Black, color = textColor)
            IconButton(onClick = onRescanClick, modifier = Modifier.clip(CircleShape).background(surfaceColor)) { Icon(Icons.Filled.Refresh, null, tint = textColor) }
        }
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item { ExpressiveFilterPill("Треки (${tracks.size})", category == 0, primaryColor, textColor) { category = 0 } }
            item { ExpressiveFilterPill("Плейлисты (${playlists.size})", category == 1, primaryColor, textColor) { category = 1 } }
            item { ExpressiveFilterPill("Авторы", category == 2, primaryColor, textColor) { category = 2 } }
            item { ExpressiveFilterPill("Альбомы", category == 3, primaryColor, textColor) { category = 3 } }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = category, label = "") { cat ->
            when (cat) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track -> TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onPlayContext(tracks, track) } }
                }
                1 -> {
                    if (playlists.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("У вас пока нет плейлистов! :-(", color = textColor.copy(0.65f), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(playlists) { pl ->
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedPlaylist(pl) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                val bmp = run { if (pl.coverUri != null) { try { context.contentResolver.openInputStream(Uri.parse(pl.coverUri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } } else null }
                                Box(modifier = Modifier.size(54.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else PlaylistGridCover(pl.trackIds, tracks, primaryColor, 54)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) { Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Треков: ${pl.trackIds.size}", color = textColor.copy(0.6f), fontSize = 12.sp) }
                                Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.5f))
                            }
                        }
                    }
                }
                2 -> {
                    val artists = run { tracks.groupBy { it.artist } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(artists.keys.toList()) { artist ->
                            val artistTrackList = artists[artist] ?: emptyList()
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedArtist(artist) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (artistTrackList.isNotEmpty()) { TrackCoverThumb(artistTrackList.first(), CircleShape, primaryColor, 46); Spacer(modifier = Modifier.width(12.dp)) }
                                Column(modifier = Modifier.weight(1f)) { Text(artist, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${artistTrackList.size} треков", color = textColor.copy(0.6f), fontSize = 12.sp) }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
                else -> {
                    val albums = run { tracks.groupBy { it.album } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(albums.keys.toList()) { album ->
                            val albumTrackList = albums[album] ?: emptyList()
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedAlbum(album) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (albumTrackList.isNotEmpty()) { TrackCoverThumb(albumTrackList.first(), expressiveShape, primaryColor, 50); Spacer(modifier = Modifier.width(12.dp)) }
                                Column(modifier = Modifier.weight(1f)) { Text(album, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${albumTrackList.firstOrNull()?.artist ?: ""} • ${albumTrackList.size} тр.", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// МИНИ-ПЛЕЕР С ЖЕСТАМИ (СВАЙП ВЛЕВО/ВПРАВО = ТРЕК, СВАЙП ВВЕРХ = ОТКРЫТЬ ПЛЕЕР)
// -----------------------------------------------------
@Composable
fun MiniPlayerBar(
    track: Track?,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onBarClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit
) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor.copy(0.96f))
            .border(1.dp, textColor.copy(0.14f), cardShape)
            .pointerInput(track?.id) {
                detectDragGestures(
                    onDragStart = { dragX = 0f; dragY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragY < -55f && abs(dragY) > abs(dragX)) {
                            onBarClick()
                        } else if (abs(dragX) > 65f && abs(dragX) > abs(dragY)) {
                            if (dragX < 0) onSwipeNext() else onSwipePrev()
                        }
                    }
                )
            }
            .clickable { onBarClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
            if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track?.title ?: "Выбери трек", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (track != null) "${track.artist} • ${metadata.formatName} • ${formatMillis(currentPosMs)}" else "Свайп вверх — плеер, влево/вправо — трек",
                color = textColor.copy(0.65f),
                style = TextStyle(fontFeatureSettings = "tnum"),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp).clip(expressiveShape).background(primaryColor)) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black)
        }
    }
}

// -----------------------------------------------------
// ПОЛНОЭКРАННЫЙ ПЛЕЕР С КАРАОКЕ (.LRC + БЛЮР), РАБОЧИМ REPEAT (1 / ALL), SHUFFLE, РЕДАКТОРОМ ТЕГОВ И ОЧЕРЕДЬЮ
// -----------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerOverlay(
    track: Track,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    expressiveShape: Shape,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onOpenQueueClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var showLyricsView by remember { mutableStateOf(false) }

    val lyricsList = metadata.lyrics
    val activeLyricIndex = remember(currentPosMs, lyricsList) {
        if (lyricsList.isEmpty()) -1
        else lyricsList.indexOfLast { it.timeMs <= currentPosMs }.coerceAtLeast(0)
    }
    val lyricListState = rememberLazyListState()

    LaunchedEffect(activeLyricIndex, showLyricsView) {
        if (showLyricsView && activeLyricIndex >= 0) {
            try {
                lyricListState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0))
            } catch (_: Throwable) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable(enabled = true, onClick = {})) {
        if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(75.dp), alpha = 0.38f)
        else Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(primaryColor.copy(0.28f), secondaryColor.copy(0.18f), bgColor))))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(textColor.copy(0.35f)))
            }

            // Переключение между Обложкой и Анимированным Текстом песни (с блюром и скейлом)
            AnimatedContent(
                targetState = showLyricsView,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(240)) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                label = "CoverOrLyrics"
            ) { isLyrics ->
                if (!isLyrics) {
                    val coverScale by animateFloatAsState(if (isPlaying) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((1f - coverScale).dp * 100)
                            .clip(expressiveShape)
                            .background(textColor.copy(0.08f))
                            .border(1.dp, textColor.copy(0.18f), expressiveShape)
                            .clickable { showLyricsView = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(105.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(0.35f))
                            .border(1.dp, textColor.copy(0.14f), RoundedCornerShape(28.dp))
                            .padding(18.dp)
                    ) {
                        if (lyricsList.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Lyrics, null, tint = primaryColor, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Для этого трека ещё нет текста (.lrc)", color = textColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Нажми на карандаш ✎ рядом с названием трека, чтобы вставить текст или таймкоды [00:12.00]", color = textColor.copy(0.65f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = onEditTagsClick, shape = RoundedCornerShape(50)) {
                                    Text("Добавить текст", color = primaryColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            LazyColumn(
                                state = lyricListState,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 70.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(lyricsList) { idx, line ->
                                    val isCurrent = idx == activeLyricIndex
                                    val distance = abs(idx - activeLyricIndex)
                                    val targetScale by animateFloatAsState(if (isCurrent) 1.06f else 0.94f, tween(300), label = "")
                                    val blurDp = if (isCurrent) 0.dp else (distance * 1.4f).coerceAtMost(4.5f).dp

                                    Text(
                                        text = line.text,
                                        color = if (isCurrent) textColor else textColor.copy(0.42f),
                                        fontSize = if (isCurrent) 22.sp else 17.sp,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scale(targetScale)
                                            .blur(blurDp)
                                            .clickable { onSeek(line.timeMs) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.basicMarquee())
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(track.artist, color = textColor.copy(0.72f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showLyricsView = !showLyricsView },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (showLyricsView) primaryColor else textColor.copy(0.14f))
                        ) {
                            Icon(Icons.Filled.Lyrics, "Текст песни", tint = if (showLyricsView) Color.Black else textColor, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onEditTagsClick,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(textColor.copy(0.14f))
                        ) {
                            Icon(Icons.Filled.Edit, "Изменить сведения", tint = textColor, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onAddToPlaylistClick,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(textColor.copy(0.14f))
                        ) {
                            Icon(Icons.Filled.Add, "В плейлист", tint = primaryColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(textColor.copy(0.14f))
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Свернуть", tint = textColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val safeDur = track.durationMs.coerceAtLeast(1L)
                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, safeDur.toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..safeDur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = textColor, activeTrackColor = primaryColor, inactiveTrackColor = textColor.copy(0.22f)),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(formatMillis(currentPosMs), color = textColor.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                    Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                        Text("${metadata.formatName} • ${metadata.bitrateKbps} • ${metadata.sampleRateStr}", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Text(formatMillis(safeDur), color = textColor.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                }
            }

            // Кнопки управления: Shuffle, Prev, Play/Pause, Next, Repeat (с 3 состояниями и цифрой 1)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isShuffleEnabled) primaryColor.copy(0.22f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) primaryColor else textColor.copy(0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPrev, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Filled.FastRewind, null, tint = textColor, modifier = Modifier.size(40.dp))
                }

                Box(
                    modifier = Modifier.size(76.dp).clip(expressiveShape).background(primaryColor).clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }

                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = textColor, modifier = Modifier.size(40.dp))
                }

                // Кнопка Repeat: OFF -> серый, ALL -> primaryColor, ONE -> контрастный цвет + бейдж 1 поверх
                val repeatAccent = when (repeatMode) {
                    RepeatMode.OFF -> textColor.copy(0.55f)
                    RepeatMode.ALL -> primaryColor
                    RepeatMode.ONE -> Color(0xFF00F5D4)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when (repeatMode) {
                                RepeatMode.OFF -> Color.Transparent
                                RepeatMode.ALL -> primaryColor.copy(0.22f)
                                RepeatMode.ONE -> Color(0xFF00F5D4).copy(0.22f)
                            }
                        )
                        .clickable { onToggleRepeat() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = repeatAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    if (repeatMode == RepeatMode.ONE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F5D4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.VolumeDown, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                Slider(
                    value = currentVol,
                    onValueChange = { currentVol = it; audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) },
                    valueRange = 0f..maxVol.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = textColor, activeTrackColor = textColor.copy(0.85f), inactiveTrackColor = textColor.copy(0.22f)),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.VolumeUp, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                IconButton(onClick = onOpenQueueClick) {
                    Icon(Icons.Filled.QueueMusic, "Очередь", tint = primaryColor, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

// -----------------------------------------------------
// НАСТРОЙКИ С РАБОЧИМ РАЗДЕЛОМ «МЕДИАТЕКА» (ФИЛЬТР 5–60 СЕК И ВЫБОР ПАПОК)
// -----------------------------------------------------
@Composable
fun SettingsCategoryItem(icon: ImageVector, title: String, subtitle: String, textColor: Color, primaryColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = primaryColor, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = textColor.copy(0.6f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.4f))
    }
}

@Composable
fun SettingsAndThemeEditor(
    allRawTracks: List<Track>,
    themeConfig: MMPThemeConfig,
    isBatteryIgnored: Boolean,
    minDurationSec: Int,
    excludedFolders: Set<String>,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onMinDurationChange: (Int) -> Unit,
    onToggleFolderExclusion: (String) -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var activeSettingsMenu by remember { mutableStateOf("MAIN") }
    val presets = listOf("Neon Pink" to 0xFFFF66C4, "Acid Lime" to 0xFFA6FF00, "Electric Violet" to 0xFF9D4EDD, "Cyber Cyan" to 0xFF00F5D4, "Sunset Coral" to 0xFFFF5E5B, "Pure Gold" to 0xFFFFBE0B)

    var showCustomColorPicker by remember { mutableStateOf(false) }
    var customRed by remember { mutableStateOf(((themeConfig.primaryHex shr 16) and 0xFF).toFloat()) }
    var customGreen by remember { mutableStateOf(((themeConfig.primaryHex shr 8) and 0xFF).toFloat()) }
    var customBlue by remember { mutableStateOf((themeConfig.primaryHex and 0xFF).toFloat()) }

    val foldersMap = remember(allRawTracks) {
        allRawTracks.groupBy { it.folderPath }
    }

    AnimatedContent(targetState = activeSettingsMenu, label = "") { menu ->
        when (menu) {
            "MAIN" -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Text("Настройки", color = textColor, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(16.dp)) }
                    item {
                        SettingsCategoryItem(Icons.Filled.ColorLens, "Интерфейс и Темы", "Цвета, MD3 формы, светлая/темная/Adaptive тема, импорт .mmpui", textColor, primaryColor) {
                            activeSettingsMenu = "UI"
                        }
                    }
                    item {
                        SettingsCategoryItem(Icons.Filled.FolderSpecial, "Медиатека и Папки", "Выбор папок с музыкой, пропуск треков от $minDurationSec сек", textColor, primaryColor) {
                            activeSettingsMenu = "LIBRARY"
                        }
                    }
                    if (!isBatteryIgnored) {
                        item {
                            SettingsCategoryItem(Icons.Filled.BatteryChargingFull, "Работа в фоне", "Отключить оптимизацию для стабильной работы", textColor, primaryColor) {
                                onRequestBatteryIgnore()
                            }
                        }
                    }
                }
            }
            "LIBRARY" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Медиатека и Папки", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Пропускать короткие аудио", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Короче $minDurationSec сек", color = primaryColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Исключает голосовые сообщения, рингтоны и короткие сэмплы (от 5 до 60 сек)", color = textColor.copy(0.6f), fontSize = 12.sp)
                            Slider(
                                value = minDurationSec.toFloat(),
                                onValueChange = { onMinDurationChange(it.toInt()) },
                                valueRange = 5f..60f,
                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                            )
                        }
                    }
                    item {
                        Text("Папки с музыкой на устройстве (${foldersMap.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Отключи папки (например, Telegram Audio или WhatsApp), которые не нужно показывать в плеере:", color = textColor.copy(0.6f), fontSize = 12.sp)
                    }
                    items(foldersMap.keys.toList()) { folderPath ->
                        val list = foldersMap[folderPath] ?: emptyList()
                        val folderName = list.firstOrNull()?.folderName ?: folderPath
                        val isEnabled = !excludedFolders.contains(folderPath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(surfaceColor)
                                .clickable { onToggleFolderExclusion(folderPath) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folderName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$folderPath • ${list.size} аудио", color = textColor.copy(0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleFolderExclusion(folderPath) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
            }
            "UI" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Интерфейс и Темы", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Text("Режим оформления", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(ThemeMode.entries) { mode ->
                                ExpressiveFilterPill(mode.label, themeConfig.themeMode == mode, primaryColor, textColor) {
                                    onThemeChange(themeConfig.copy(themeMode = mode, bgHex = if (mode == ThemeMode.LIGHT || mode == ThemeMode.ADAPTIVE_LIGHT) 0xFFF7F5FA else 0xFF0D0D12))
                                }
                            }
                        }
                    }
                    item {
                        Text("Акцентный цвет", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            items(presets) { (name, hex) ->
                                Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(Color(hex)).border(if (themeConfig.primaryHex == hex) 3.dp else 0.dp, textColor, expressiveShape).clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) })
                            }
                            item {
                                ExpressiveFilterPill("Свой RGB", showCustomColorPicker, primaryColor, textColor) {
                                    showCustomColorPicker = !showCustomColorPicker
                                }
                            }
                        }
                        AnimatedVisibility(visible = showCustomColorPicker) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(cardShape).background(surfaceColor).padding(14.dp)) {
                                val previewColor = Color(customRed.toInt(), customGreen.toInt(), customBlue.toInt())
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Палитра RGB", color = textColor, fontWeight = FontWeight.Bold)
                                    Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(previewColor))
                                }
                                Slider(value = customRed, onValueChange = { customRed = it }, valueRange = 40f..255f)
                                Slider(value = customGreen, onValueChange = { customGreen = it }, valueRange = 40f..255f)
                                Slider(value = customBlue, onValueChange = { customBlue = it }, valueRange = 40f..255f)
                                Button(
                                    onClick = {
                                        val hex = (0xFFL shl 24) or (customRed.toLong() shl 16) or (customGreen.toLong() shl 8) or customBlue.toLong()
                                        onThemeChange(themeConfig.copy(themeName = "Custom RGB", primaryHex = hex))
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = previewColor)
                                ) {
                                    Text("Применить", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item {
                        Text("Форма MD3 Expressive", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(ShapeStyle.entries) { style -> ExpressiveFilterPill(style.label, themeConfig.shapeStyle == style, primaryColor, textColor) { onThemeChange(themeConfig.copy(shapeStyle = style)) } }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                            Button(onClick = onExport, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Экспорт .mmpui", color = Color.Black, fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Text("Импорт", color = textColor) }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// ДИАЛОГ РЕДАКТИРОВАНИЯ СВЕДЕНИЙ О ТРЕКЕ (НАЗВАНИЕ, АРТИСТ, АЛЬБОМ, ОБЛОЖКА, ТЕКСТ .LRC)
// -----------------------------------------------------
@Composable
fun EditTrackTagsDialog(
    track: Track,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    selectedCoverUri: String?,
    onPickCoverClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var lyrics by remember { mutableStateOf(track.customLyrics ?: "") }
    val context = LocalContext.current

    val effectiveCoverUri = selectedCoverUri ?: track.customCoverUri
    val coverBitmap = run {
        if (effectiveCoverUri != null) {
            try { context.contentResolver.openInputStream(Uri.parse(effectiveCoverUri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        } else CoverCache.get(track.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = { Text("Изменить сведения о треке", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp) },
        text = {
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(expressiveShape)
                            .background(primaryColor.copy(0.2f))
                            .border(1.dp, primaryColor, expressiveShape)
                            .clickable { onPickCoverClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(coverBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddPhotoAlternate, null, tint = primaryColor)
                                Text("Обложка", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название трека") },
                        singleLine = true,
                        shape = cardShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Исполнитель") },
                        singleLine = true,
                        shape = cardShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text("Альбом") },
                        singleLine = true,
                        shape = cardShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text("Текст песни или .lrc ([00:12.00]Строка)") },
                        minLines = 3,
                        maxLines = 6,
                        shape = cardShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), artist.trim(), album.trim(), effectiveCoverUri, lyrics.ifBlank { null }) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) }
        }
    )
}

// -----------------------------------------------------
// УПРАВЛЕНИЕ ОЧЕРЕДЬЮ (ПЕРЕМЕЩЕНИЕ ВВЕРХ/ВНИЗ, УДАЛЕНИЕ ИЗ ОЧЕРЕДИ)
// -----------------------------------------------------
@Composable
fun QueueManagementSheet(
    queue: List<Track>,
    currentTrack: Track?,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onSelectTrack: (Track) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .navigationBarsPadding()
                .clip(cardShape)
                .background(surfaceColor)
                .border(1.dp, textColor.copy(0.15f), cardShape)
                .clickable(enabled = false, onClick = {})
                .padding(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Очередь воспроизведения (${queue.size})", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = textColor) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(queue) { index, trk ->
                    val isCurrent = currentTrack?.id == trk.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) primaryColor.copy(0.2f) else textColor.copy(0.05f))
                            .clickable { onSelectTrack(trk) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trk.title, color = if (isCurrent) primaryColor else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(trk.artist, color = textColor.copy(0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row {
                            if (index > 0) {
                                IconButton(onClick = { onMoveTrack(index, index - 1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = textColor.copy(0.75f))
                                }
                            }
                            if (index < queue.size - 1) {
                                IconButton(onClick = { onMoveTrack(index, index + 1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = textColor.copy(0.75f))
                                }
                            }
                            IconButton(onClick = { onRemoveTrack(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.DeleteOutline, null, tint = textColor.copy(0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrostedDockBar(selectedTab: Int, cardShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, onTabSelected: (Int) -> Unit, onCreateClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor.copy(0.95f)).border(1.dp, textColor.copy(0.14f), cardShape).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        DockVerticalItem(Icons.Filled.Home, "Главная", selectedTab == 0, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(0) }
        DockVerticalItem(Icons.Filled.LibraryMusic, "Медиатека", selectedTab == 1, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(1) }
        DockVerticalItem(Icons.Filled.AddCircleOutline, "Создать", false, primaryColor, textColor, Modifier.weight(1f), onCreateClick)
    }
}

@Composable
fun DockVerticalItem(icon: ImageVector, label: String, selected: Boolean, primaryColor: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (selected) primaryColor.copy(0.24f) else Color.Transparent), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) primaryColor else textColor.copy(0.7f), modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = if (selected) primaryColor else textColor.copy(0.75f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExpressiveFilterPill(text: String, selected: Boolean, primaryColor: Color, textColor: Color = Color.White, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) primaryColor else textColor.copy(0.08f)).border(1.dp, if (selected) primaryColor else textColor.copy(0.16f), RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = if (selected) Color.Black else textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
fun TrackListItem(track: Track, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color = Color.White, surfaceColor: Color = Color.White.copy(0.06f), badgeText: String? = null, showCoverThumb: Boolean = true, onAddToPlaylist: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCoverThumb) { TrackCoverThumb(track, expressiveShape, primaryColor, 44); Spacer(modifier = Modifier.width(12.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(if (badgeText != null) "$badgeText • ${track.artist}" else "${track.artist} • ${formatMillis(track.durationMs)}", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp).clip(CircleShape)) {
                Icon(Icons.Filled.Add, null, tint = primaryColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun AddToPlaylistSheet(track: Track, playlists: List<PlaylistItem>, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, onDismiss: () -> Unit, onSelectPlaylist: (PlaylistItem) -> Unit, onCreateNewPlaylistClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.15f), cardShape).clickable(enabled = false, onClick = {}).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(14.dp))
            Text("В какой плейлист добавить?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(track.title, color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(14.dp))
            if (playlists.isEmpty()) {
                Text("У вас пока нет плейлистов! :-(", color = textColor.copy(0.65f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onCreateNewPlaylistClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Создать первый плейлист", color = Color.Black, fontWeight = FontWeight.Bold) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 260.dp)) {
                    items(playlists) { pl ->
                        Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(textColor.copy(0.06f)).clickable { onSelectPlaylist(pl) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(38.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.size(20.dp)) }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column { Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("Треков: ${pl.trackIds.size}", color = textColor.copy(0.6f), fontSize = 11.sp) }
                            }
                            Icon(Icons.Filled.Add, null, tint = primaryColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateMenuSheet(cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, onDismiss: () -> Unit, onSelectPlaylist: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.15f), cardShape).clickable(enabled = false, onClick = {}).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Что создаём?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(primaryColor.copy(0.18f)).border(1.dp, primaryColor, cardShape).clickable { onSelectPlaylist() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(expressiveShape).background(primaryColor), contentAlignment = Alignment.Center) { Icon(Icons.Filled.LibraryAdd, null, tint = Color.Black) }
                Spacer(modifier = Modifier.width(14.dp))
                Column { Text("Плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Выбрать свою обложку и название", color = textColor.copy(0.65f), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun CreatePlaylistDetailsDialog(cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, selectedCoverUri: String?, onPickCoverClick: () -> Unit, onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coverBitmap = run { if (selectedCoverUri != null) { try { context.contentResolver.openInputStream(Uri.parse(selectedCoverUri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } } else null }

    AlertDialog(
        onDismissRequest = onDismiss, shape = cardShape, containerColor = surfaceColor,
        title = { Text("Новый плейлист", color = textColor, fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(110.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor, expressiveShape).clickable { onPickCoverClick() }, contentAlignment = Alignment.Center) {
                    if (coverBitmap != null) Image(coverBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = primaryColor, modifier = Modifier.size(32.dp)); Spacer(modifier = Modifier.height(4.dp)); Text("Обложка", color = textColor.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Название плейлиста", color = textColor.copy(0.45f)) }, singleLine = true, shape = cardShape, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = primaryColor, unfocusedBorderColor = textColor.copy(0.25f)))
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, selectedCoverUri) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Создать", color = Color.Black, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) } }
    )
}
