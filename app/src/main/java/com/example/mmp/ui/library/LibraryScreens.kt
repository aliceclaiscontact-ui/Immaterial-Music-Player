package com.example.mmp.ui.library

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.zIndex
import com.example.mmp.*
import com.example.mmp.data.local.*
import com.example.mmp.data.remote.*
import com.example.mmp.domain.*
import com.example.mmp.domain.model.*
import com.example.mmp.ui.components.*
import com.example.mmp.ui.dialogs.*
import com.example.mmp.ui.home.*
import com.example.mmp.ui.library.*
import com.example.mmp.ui.onboarding.*
import com.example.mmp.ui.online.*
import com.example.mmp.ui.player.*
import com.example.mmp.ui.settings.*
import com.example.mmp.ui.theme.*
import java.text.Collator
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ArtworkAtmosphere(
    bitmap: Bitmap?,
    accentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.62f
) {
    // Keep the application backdrop neutral; album colors belong to accents, not the background.
    Box(modifier = modifier.background(backgroundColor))
}

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
    onEditPlaylistClick: (() -> Unit)? = null,
    onPlayContext: (List<Track>, Track) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onAddMultipleTracksToPlaylist: (List<Track>) -> Unit,
    onOpenPlaylistTrackPicker: (() -> Unit)? = null,
    onToggleTrackInPlaylist: ((Track) -> Unit)? = null,
    showAddButton: Boolean = true,
    albumRepeatEnabled: Boolean = false,
    onToggleAlbumRepeat: (() -> Unit)? = null,
    isArtistPage: Boolean = false
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(title, customCoverUri) { mutableStateOf<Bitmap?>(null) }
    val isContextActive = currentTrack != null && tracks.any { it.id == currentTrack.id }
    val isContextPlaying = isContextActive && isPlaying

    LaunchedEffect(tracks, customCoverUri) {
        heroBitmap = null
        if (customCoverUri != null) {
            heroBitmap = loadBitmapFromUriOrHttp(context, customCoverUri, 512)
        } else {
            for (trk in tracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            val validHero = heroBitmap?.takeIf { !it.isRecycled }
            Column(modifier = Modifier.fillMaxWidth().background(bgColor)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isArtistPage) 286.dp else 300.dp)
                ) {
                    ArtworkAtmosphere(
                        bitmap = validHero,
                        accentColor = primaryColor,
                        backgroundColor = bgColor,
                        modifier = Modifier.fillMaxSize(),
                        overlayAlpha = if (isArtistPage) 0.18f else 0.34f
                    )

                    if (isArtistPage) {
                        if (validHero != null) {
                            Image(
                                bitmap = validHero.asImageBitmap(),
                                contentDescription = "$title — обложка исполнителя",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(primaryColor.copy(0.35f), surfaceColor, bgColor))
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, null, tint = textColor.copy(0.45f), modifier = Modifier.size(88.dp))
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, bgColor.copy(alpha = 0.14f), bgColor.copy(alpha = 0.96f))
                                )
                            )
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(224.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(surfaceColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (validHero != null) {
                                    Image(validHero.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else if (customCoverUri == null && tracks.isNotEmpty()) {
                                    PlaylistGridCover(tracks, primaryColor, 224)
                                } else {
                                    Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(72.dp))
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp)
                            .size(42.dp).clip(CircleShape).background(surfaceColor.copy(alpha = 0.82f))
                    ) {
                        Icon(Icons.Filled.ArrowBackIosNew, "Назад", tint = textColor, modifier = Modifier.size(18.dp))
                    }
                    if (onEditPlaylistClick != null) {
                        IconButton(
                            onClick = onEditPlaylistClick,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 12.dp)
                                .size(42.dp).clip(CircleShape).background(surfaceColor.copy(alpha = 0.82f))
                        ) {
                            Icon(Icons.Filled.Edit, "Изменить", tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(title, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, color = textColor.copy(0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (isShuffleEnabled) primaryColor.copy(0.20f) else surfaceColor).clickable {
                            onToggleShuffle()
                            if (!isContextActive && tracks.isNotEmpty()) onPlayContext(tracks.shuffled(), tracks.random())
                        }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (isShuffleEnabled) primaryColor else textColor, modifier = Modifier.size(25.dp))
                        }
                        Box(modifier = Modifier.weight(1.55f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(primaryColor).clickable {
                            if (isContextActive) onTogglePlay() else if (tracks.isNotEmpty()) onPlayContext(tracks, tracks.first())
                        }, contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(
                                    if (isContextPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isContextPlaying) "Пауза" else if (isContextActive) "Продолжить" else "Играть",
                                    tint = Color.Black,
                                    modifier = Modifier.size(27.dp)
                                )
                                Text(
                                    if (isContextPlaying) "Пауза" else if (isContextActive) "Продолжить" else "Играть",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        if (showAddButton) {
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(surfaceColor).clickable {
                                if (onOpenPlaylistTrackPicker != null) onOpenPlaylistTrackPicker() else if (tracks.isNotEmpty()) onAddMultipleTracksToPlaylist(tracks)
                            }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, "Добавить треки", tint = textColor, modifier = Modifier.size(23.dp))
                            }
                        } else {
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (albumRepeatEnabled) primaryColor.copy(0.18f) else surfaceColor)
                                .clickable { onToggleAlbumRepeat?.invoke() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Repeat, "Повтор альбома", tint = if (albumRepeatEnabled) primaryColor else textColor, modifier = Modifier.size(23.dp))
                            }
                        }
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("В этом списке пока нет треков.", color = textColor.copy(0.6f)) } }
        } else {
            items(tracks, key = { trackStableKey(it) }) { track ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TrackListItem(track, RoundedCornerShape(16.dp), expressiveShape, primaryColor, textColor, surfaceColor, null, true, false, false, onToggleTrackInPlaylist != null, { (onToggleTrackInPlaylist ?: onAddTrackToPlaylistClick)(track) }, {}) { onPlayContext(tracks, track) }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

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
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onAddMultipleTracksToPlaylist: (List<Track>) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(artistName) { mutableStateOf<Bitmap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val isArtistPlaying = isPlaying && currentTrack != null && artistTracks.any { it.id == currentTrack.id }

    val selectedTrackIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedTrackIds.isNotEmpty()

    LaunchedEffect(artistTracks) {
        for (trk in artistTracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
    }

    val filteredTracks = run { if (searchQuery.isBlank()) artistTracks else artistTracks.filter { it.title.contains(searchQuery, ignoreCase = true) } }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(CircleShape).background(surfaceColor)) { Icon(Icons.Filled.ArrowBackIosNew, "Назад", tint = textColor, modifier = Modifier.size(18.dp)) }
                        Spacer(modifier = Modifier.size(42.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(264.dp), contentAlignment = Alignment.Center) {
                        val validHero = heroBitmap?.takeIf { !it.isRecycled }
                        ArtworkAtmosphere(
                            bitmap = validHero,
                            accentColor = primaryColor,
                            backgroundColor = bgColor,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                            overlayAlpha = 0.68f
                        )
                        Box(Modifier.size(224.dp).clip(RoundedCornerShape(24.dp)).background(surfaceColor), contentAlignment = Alignment.Center) {
                            if (validHero != null) Image(bitmap = validHero.asImageBitmap(), contentDescription = artistName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Filled.Person, null, tint = textColor.copy(0.3f), modifier = Modifier.size(72.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(artistName, color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Исполнитель • ${artistTracks.size} треков", color = textColor.copy(0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (isShuffleEnabled) primaryColor.copy(0.20f) else surfaceColor).clickable {
                            onToggleShuffle(); if (artistTracks.isNotEmpty()) onPlayContext(artistTracks.shuffled(), artistTracks.random())
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (isShuffleEnabled) primaryColor else textColor, modifier = Modifier.size(25.dp)) }
                        Box(modifier = Modifier.weight(1.55f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(primaryColor).clickable {
                            if (isArtistPlaying) onTogglePlay() else if (artistTracks.isNotEmpty()) onPlayContext(artistTracks, artistTracks.first())
                        }, contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(if (isArtistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isArtistPlaying) "Пауза" else "Играть", tint = Color.Black, modifier = Modifier.size(27.dp))
                                Text(if (isArtistPlaying) "Пауза" else "Играть", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(surfaceColor).clickable { showSearchBar = !showSearchBar }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, "Поиск треков", tint = textColor, modifier = Modifier.size(25.dp)) }
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
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Популярные треки", color = textColor, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.6f))
                    }
                    if (isSelectionMode) {
                        TextButton(onClick = {
                            if (selectedTrackIds.size == artistTracks.size) selectedTrackIds.clear()
                            else {
                                selectedTrackIds.clear()
                                selectedTrackIds.addAll(artistTracks.map { it.id })
                            }
                        }) {
                            Text(if (selectedTrackIds.size == artistTracks.size) "Снять всё" else "Выбрать всё", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(filteredTracks, key = { trackStableKey(it) }) { track ->
                val isSel = selectedTrackIds.contains(track.id)
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TrackListItem(
                        track = track,
                        cardShape = cardShape,
                        expressiveShape = expressiveShape,
                        primaryColor = primaryColor,
                        textColor = textColor,
                        surfaceColor = surfaceColor,
                        badgeText = null,
                        showCoverThumb = true,
                        isSelected = isSel,
                        isSelectionMode = isSelectionMode,
                        onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                        onLongClick = {
                            if (!isSel) selectedTrackIds.add(track.id)
                        },
                        onClick = {
                            if (isSelectionMode) {
                                if (isSel) selectedTrackIds.remove(track.id) else selectedTrackIds.add(track.id)
                            } else {
                                onPlayContext(artistTracks, track)
                            }
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(140.dp)) }
        }

        if (isSelectionMode) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Выбрано: ${selectedTrackIds.size}", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedTrackIds.clear() }) {
                            Text("Отмена", color = textColor.copy(0.7f))
                        }
                        Button(
                            onClick = {
                                val selectedTracks = artistTracks.filter { selectedTrackIds.contains(it.id) }
                                onAddMultipleTracksToPlaylist(selectedTracks)
                                selectedTrackIds.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("В плейлист", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
    onPlayContext: (List<Track>, Track) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onAddMultipleTracksToPlaylist: (List<Track>) -> Unit,
    onOpenPlaylistTrackPicker: (PlaylistItem) -> Unit,
    onToggleTrackInPlaylist: (PlaylistItem, Track) -> Unit,
    onSetOpenedPlaylist: (PlaylistItem?) -> Unit,
    onSetOpenedArtist: (String?) -> Unit,
    onSetOpenedAlbum: (String?) -> Unit,
    onEditPlaylist: (PlaylistItem) -> Unit,
    onCreateNewPlaylistClick: () -> Unit,
    onRescanClick: () -> Unit,
    albumRepeatEnabled: Boolean = false,
    onToggleAlbumRepeat: (() -> Unit)? = null
) {
    var category by remember { mutableIntStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(TrackSortMode.DATE_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }

    val selectedTrackIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedTrackIds.isNotEmpty()

    if (openedArtist != null) {
        val artistTracks = remember(tracks, openedArtist) {
            tracks.filter { trk -> splitArtists(trk.artist).any { it.equals(openedArtist, ignoreCase = true) } }
        }
        CleanHeaderLayout(
            title = openedArtist,
            subtitle = "Исполнитель • ${artistTracks.size} треков",
            tracks = artistTracks,
            customCoverUri = null,
            primaryColor = primaryColor,
            textColor = textColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            expressiveShape = expressiveShape,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            onBack = { onSetOpenedArtist(null) },
            onPlayContext = onPlayContext,
            onTogglePlay = onTogglePlay,
            onToggleShuffle = onToggleShuffle,
            onAddTrackToPlaylistClick = onAddTrackToPlaylistClick,
            onAddMultipleTracksToPlaylist = onAddMultipleTracksToPlaylist,
            showAddButton = true,
            isArtistPage = true
        )
        return
    }

    if (openedPlaylist != null) {
        val currentPl = playlists.find { it.name == openedPlaylist.name } ?: openedPlaylist
        val plTracks = remember(currentPl, tracks) { resolvePlaylistTracks(currentPl, tracks) }
        CleanHeaderLayout(currentPl.name, if (currentPl.description.isNotBlank()) currentPl.description else "Плейлист • ${plTracks.size} треков", plTracks, currentPl.coverUri, primaryColor, textColor, surfaceColor, bgColor, expressiveShape, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedPlaylist(null) }, { onEditPlaylist(currentPl) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick, onAddMultipleTracksToPlaylist, { onOpenPlaylistTrackPicker(currentPl) }, { onToggleTrackInPlaylist(currentPl, it) })
        return
    }

    if (openedAlbum != null) {
        val albumTracks = remember(tracks, openedAlbum) { tracks.filter { it.album == openedAlbum } }
        val artistName = albumTracks.firstOrNull()?.artist ?: "Неизвестный исполнитель"
        CleanHeaderLayout(
            openedAlbum,
            "Альбом • $artistName",
            albumTracks,
            null,
            primaryColor,
            textColor,
            surfaceColor,
            bgColor,
            expressiveShape,
            currentTrack,
            isPlaying,
            isShuffleEnabled,
            { onSetOpenedAlbum(null) },
            null,
            onPlayContext,
            onTogglePlay,
            onToggleShuffle,
            onAddTrackToPlaylistClick,
            onAddMultipleTracksToPlaylist,
            showAddButton = false,
            albumRepeatEnabled = albumRepeatEnabled,
            onToggleAlbumRepeat = onToggleAlbumRepeat
        )
        return
    }

    val sortedTracks = remember(tracks, sortMode, searchQuery) {
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
        when (sortMode) {
            TrackSortMode.DATE_ADDED -> filtered.sortedWith { first, second ->
                val dateOrder = second.dateAdded.compareTo(first.dateAdded)
                if (dateOrder != 0) dateOrder else collator.compare(first.title.trim(), second.title.trim())
            }
            TrackSortMode.TITLE -> filtered.sortedWith { first, second ->
                val titleOrder = collator.compare(first.title.trim(), second.title.trim())
                if (titleOrder != 0) titleOrder else collator.compare(first.artist.trim(), second.artist.trim())
            }
            TrackSortMode.ARTIST -> filtered.sortedWith { first, second ->
                val firstArtist = splitArtists(first.artist).firstOrNull().orEmpty()
                val secondArtist = splitArtists(second.artist).firstOrNull().orEmpty()
                val artistOrder = collator.compare(firstArtist.trim(), secondArtist.trim())
                if (artistOrder != 0) artistOrder else collator.compare(first.title.trim(), second.title.trim())
            }
            TrackSortMode.DURATION -> filtered.sortedWith { first, second ->
                val durationOrder = second.durationMs.compareTo(first.durationMs)
                if (durationOrder != 0) durationOrder else collator.compare(first.title.trim(), second.title.trim())
            }
        }
    }

    val filteredPlaylists = remember(playlists, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) playlists else playlists.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }

    val filteredArtists = remember(tracks, searchQuery) {
        val map = LinkedHashMap<String, MutableList<Track>>()
        for (trk in tracks) {
            for (art in splitArtists(trk.artist)) {
                map.getOrPut(art) { mutableListOf() }.add(trk)
            }
        }
        val query = searchQuery.trim()
        if (query.isBlank()) map else map.filterKeys { it.contains(query, ignoreCase = true) }
    }

    val filteredAlbums = remember(tracks, searchQuery) {
        val allAlb = tracks.groupBy { it.album }
        val query = searchQuery.trim()
        if (query.isBlank()) allAlb else allAlb.filterKeys { it.contains(query, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Медиатека", fontSize = 26.sp, fontWeight = FontWeight.Black, color = textColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRescanClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(surfaceColor)) {
                        Icon(Icons.Filled.Refresh, "Рескан", tint = primaryColor, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { isSearchActive = !isSearchActive }, modifier = Modifier.size(42.dp).clip(CircleShape).background(if (isSearchActive) primaryColor else surfaceColor)) {
                        Icon(Icons.Filled.Search, "Поиск", tint = if (isSearchActive) Color.Black else textColor, modifier = Modifier.size(20.dp))
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(42.dp).clip(CircleShape).background(surfaceColor)) {
                            Icon(Icons.Filled.Sort, "Сортировка", tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, modifier = Modifier.background(surfaceColor)) {
                            TrackSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label, color = if (sortMode == mode) primaryColor else textColor, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        sortMode = mode
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по медиатеке...", color = textColor.copy(0.5f), fontSize = 13.sp) },
                    singleLine = true,
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("Треки", Icons.Filled.MusicNote, 0),
                    Triple("Плейлисты", Icons.Filled.QueueMusic, 1),
                    Triple("Артисты", Icons.Filled.Person, 2),
                    Triple("Альбомы", Icons.Filled.Album, 3)
                ).forEach { (label, icon, index) ->
                    val selected = category == index
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) primaryColor else surfaceColor)
                            .border(1.dp, if (selected) primaryColor.copy(0.35f) else textColor.copy(0.07f), RoundedCornerShape(16.dp))
                            .clickable { category = index }
                            .padding(horizontal = 3.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, label, tint = if (selected) Color.Black else textColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            label,
                            color = if (selected) Color.Black else textColor,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val cat = category
                when (cat) {
                    0 -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(sortedTracks, key = { trackStableKey(it) }) { track ->
                            val isSel = selectedTrackIds.contains(track.id)
                            TrackListItem(
                                track = track,
                                cardShape = cardShape,
                                expressiveShape = expressiveShape,
                                primaryColor = primaryColor,
                                textColor = textColor,
                                surfaceColor = surfaceColor,
                                badgeText = null,
                                showCoverThumb = true,
                                isSelected = isSel,
                                isSelectionMode = isSelectionMode,
                                onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                                onLongClick = {
                                    if (!isSel) selectedTrackIds.add(track.id)
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSel) selectedTrackIds.remove(track.id) else selectedTrackIds.add(track.id)
                                    } else {
                                        onPlayContext(sortedTracks, track)
                                    }
                                }
                            )
                        }
                    }
                    1 -> Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(primaryColor.copy(0.14f))
                                .border(1.dp, primaryColor.copy(0.55f), cardShape)
                                .clickable { onCreateNewPlaylistClick() }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, "Создать", tint = Color.Black)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Новый плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Своя музыка, свой порядок", color = textColor.copy(0.62f), fontSize = 11.sp)
                            }
                            Icon(Icons.Filled.ArrowForward, null, tint = primaryColor)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ТВОЯ КОЛЛЕКЦИЯ", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                                Text("${filteredPlaylists.size} плейлистов", color = textColor.copy(0.58f), fontSize = 12.sp)
                            }
                            Icon(Icons.Filled.GridView, null, tint = textColor.copy(0.5f), modifier = Modifier.size(20.dp))
                        }

                        if (filteredPlaylists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (searchQuery.isNotBlank()) "Ничего не найдено" else "Пока нет плейлистов. Создай первый!",
                                    color = textColor.copy(0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(filteredPlaylists, key = { it.name }) { pl ->
                                    val plCount = remember(pl, tracks) { resolvePlaylistTracks(pl, tracks).size }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(surfaceColor)
                                            .clickable { onSetOpenedPlaylist(pl) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(surfaceColor)
                                        ) {
                                            PlaylistThumb(pl, tracks, expressiveShape, primaryColor, 148, fillBounds = true)
                                        }
                                        Spacer(modifier = Modifier.height(9.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                pl.name,
                                                modifier = Modifier.weight(1f),
                                                color = textColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = { onEditPlaylist(pl) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Filled.Edit, "Изменить плейлист", tint = textColor.copy(0.62f), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(
                                            "$plCount треков",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = textColor.copy(0.68f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(filteredArtists.keys.toList()) { artist ->
                            val artistTrackList = filteredArtists[artist] ?: emptyList()
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(cardShape)
                                    .background(surfaceColor)
                                    .border(1.dp, textColor.copy(0.08f), cardShape)
                                    .clickable { onSetOpenedArtist(artist) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (artistTrackList.isNotEmpty()) {
                                    TrackCoverThumb(artistTrackList.firstOrNull { it.customCoverUri != null } ?: artistTrackList.first(), expressiveShape, primaryColor, 46)
                                    Spacer(modifier = Modifier.width(14.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artist, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${artistTrackList.size} треков", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(filteredAlbums.keys.toList()) { album ->
                            val albumTrackList = filteredAlbums[album] ?: emptyList()
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(cardShape)
                                    .background(surfaceColor)
                                    .border(1.dp, textColor.copy(0.08f), cardShape)
                                    .clickable { onSetOpenedAlbum(album) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (albumTrackList.isNotEmpty()) {
                                    TrackCoverThumb(albumTrackList.firstOrNull { it.customCoverUri != null } ?: albumTrackList.first(), expressiveShape, primaryColor, 46)
                                    Spacer(modifier = Modifier.width(14.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${albumTrackList.firstOrNull()?.artist ?: ""} • ${albumTrackList.size} тр.", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Выбрано: ${selectedTrackIds.size}", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedTrackIds.clear() }) {
                            Text("Отмена", color = textColor.copy(0.7f), fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                val selectedTracks = tracks.filter { selectedTrackIds.contains(it.id) }
                                onAddMultipleTracksToPlaylist(selectedTracks)
                                selectedTrackIds.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("В плейлист", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
