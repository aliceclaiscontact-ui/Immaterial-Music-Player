package com.example.mmp.ui.online

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
fun YouTubeMusicScreen(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    likedTracks: List<Track>,
    searchResults: List<YtMusicTrack>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchCommit: (String) -> Unit,
    onPlayYtTrack: (YtMusicTrack, List<YtMusicTrack>) -> Unit,
    onPlaySavedTrack: (Track) -> Unit,
    onDownloadYtTrack: (YtMusicTrack) -> Unit,
    onToggleLikeYtTrack: (YtMusicTrack) -> Unit,
    onAddYtToPlaylist: (YtMusicTrack) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            YouTubeMusicLogoBadge(34)
            Text("YouTube Music", fontSize = 26.sp, fontWeight = FontWeight.Black, color = textColor)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveFilterPill("Поиск музыки", activeTab == 0, primaryColor, textColor) { activeTab = 0 }
            ExpressiveFilterPill("Понравившееся (${likedTracks.size})", activeTab == 1, primaryColor, textColor) { activeTab = 1 }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Поиск песен...", color = textColor.copy(0.5f), fontSize = 13.sp) },
                    singleLine = true,
                    shape = cardShape,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { if (searchQuery.isNotBlank()) onSearchCommit(searchQuery.trim()) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Введи название трека или артиста", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(searchResults, key = { it.videoId }) { ytTrack ->
                        val trackId = ytTrack.videoId.hashCode().toLong()
                        val isLiked = likedTracks.any { it.id == trackId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(surfaceColor)
                                .clickable { onPlayYtTrack(ytTrack, searchResults) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dummyTrack = remember(ytTrack) {
                                Track(id = trackId, uri = Uri.EMPTY, title = ytTrack.title, artist = ytTrack.artist, album = "YouTube Music", durationMs = ytTrack.durationMs, mimeType = "audio/mp4", sizeBytes = 0L, customCoverUri = ytTrack.artworkUrl)
                            }
                            TrackCoverThumb(dummyTrack, expressiveShape, primaryColor, 50)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ytTrack.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val durStr = if (ytTrack.durationMs > 0L) " • ${formatDuration(ytTrack.durationMs)}" else ""
                                Text("${ytTrack.artist}$durStr", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { onToggleLikeYtTrack(ytTrack) }, modifier = Modifier.size(36.dp)) {
                                Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.Favorite, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDownloadYtTrack(ytTrack) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Download, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onAddYtToPlaylist(ytTrack) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Add, null, tint = textColor.copy(0.75f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        } else {
            if (likedTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нажми ❤️ рядом с треком, чтобы сохранить его сюда", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(likedTracks, key = { trackStableKey(it) }) { savedTrack ->
                        TrackListItem(
                            track = savedTrack,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            badgeText = "YT ❤️",
                            showCoverThumb = true,
                            isSelected = false,
                            isSelectionMode = false,
                            onAddToPlaylist = null,
                            onLongClick = {},
                            onClick = { onPlaySavedTrack(savedTrack) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundCloudScreen(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    likedScTracks: List<Track>,
    searchResults: List<SoundCloudTrack>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchCommit: (String, Int) -> Unit,
    onPlaySoundCloudTrack: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit,
    onPlaySavedScTrack: (Track) -> Unit,
    onDownloadScTrack: (SoundCloudTrack) -> Unit,
    onDownloadScPlaylist: (SoundCloudPlaylist) -> Unit,
    downloadingPlaylistTitle: String?,
    downloadPlaylistCurrent: Int,
    downloadPlaylistTotal: Int,
    onToggleLikeScTrack: (SoundCloudTrack) -> Unit,
    onAddScToPlaylist: (SoundCloudTrack) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var searchType by remember { mutableIntStateOf(0) }
    var playlistResults by remember { mutableStateOf<List<SoundCloudPlaylist>>(emptyList()) }
    var openedScPlaylist by remember { mutableStateOf<SoundCloudPlaylist?>(null) }
    var isLoadingPlaylistTracks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (openedScPlaylist != null) {
        val pl = openedScPlaylist!!
        BackHandler { openedScPlaylist = null }
        var currentPlTracks by remember(pl.id) { mutableStateOf(pl.tracks) }

        LaunchedEffect(pl.id) {
            if (currentPlTracks.size < pl.trackCount) {
                isLoadingPlaylistTracks = true
                val full = SoundCloudClient.fetchFullPlaylist(pl.id)
                if (full.isNotEmpty()) {
                    currentPlTracks = full
                }
                isLoadingPlaylistTracks = false
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { openedScPlaylist = null }) {
                            Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(pl.title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { onDownloadScPlaylist(pl.copy(tracks = currentPlTracks)) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Download, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (downloadingPlaylistTitle != null && downloadPlaylistTotal > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(primaryColor.copy(0.16f))
                            .border(1.dp, primaryColor, cardShape)
                            .padding(14.dp)
                    ) {
                        Text(
                            "Скачано $downloadPlaylistCurrent из $downloadPlaylistTotal треков",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (downloadPlaylistCurrent.toFloat() / downloadPlaylistTotal.toFloat()).coerceIn(0f, 1f) },
                            color = primaryColor,
                            trackColor = textColor.copy(0.15f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val plCoverTrack = remember(pl) {
                        Track(id = -pl.id, uri = Uri.EMPTY, title = pl.title, artist = pl.author, album = "SoundCloud", durationMs = 0L, mimeType = "audio/mpeg", sizeBytes = 0L, customCoverUri = pl.artworkUrl)
                    }
                    TrackCoverThumb(plCoverTrack, expressiveShape, primaryColor, 72)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Автор: ${pl.author}", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Треков: ${currentPlTracks.size} из ${pl.trackCount}", color = textColor.copy(0.6f), fontSize = 12.sp)
                        if (isLoadingPlaylistTracks) {
                            Text("Загружаем все треки...", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(currentPlTracks, key = { it.id }) { scTrack ->
                val isLiked = likedScTracks.any { kotlin.math.abs(it.id) == kotlin.math.abs(scTrack.id) }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onPlaySoundCloudTrack(scTrack, currentPlTracks) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dummyTrack = remember(scTrack) {
                        Track(id = -scTrack.id, uri = Uri.EMPTY, title = scTrack.title, artist = scTrack.artist, album = pl.title, durationMs = scTrack.durationMs, mimeType = "audio/mpeg", sizeBytes = 0L, customCoverUri = scTrack.artworkUrl ?: pl.artworkUrl)
                    }
                    TrackCoverThumb(dummyTrack, expressiveShape, primaryColor, 48)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scTrack.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${scTrack.artist} • ${formatDuration(scTrack.durationMs)}", color = textColor.copy(0.6f), fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { onToggleLikeScTrack(scTrack) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.Favorite, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onAddScToPlaylist(scTrack) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Add, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoundCloudLogoBadge(34)
            Text("SoundCloud", fontSize = 26.sp, fontWeight = FontWeight.Black, color = textColor)
        }

        if (downloadingPlaylistTitle != null && downloadPlaylistTotal > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(primaryColor.copy(0.16f))
                    .border(1.dp, primaryColor, cardShape)
                    .padding(14.dp)
            ) {
                Text(
                    "«$downloadingPlaylistTitle»: скачано $downloadPlaylistCurrent из $downloadPlaylistTotal треков",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (downloadPlaylistCurrent.toFloat() / downloadPlaylistTotal.toFloat()).coerceIn(0f, 1f) },
                    color = primaryColor,
                    trackColor = textColor.copy(0.15f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveFilterPill("Поиск в сети", activeTab == 0, primaryColor, textColor) { activeTab = 0 }
            ExpressiveFilterPill("Понравившееся (${likedScTracks.size})", activeTab == 1, primaryColor, textColor) { activeTab = 1 }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeTab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExpressiveFilterPill("Треки", searchType == 0, primaryColor, textColor) { searchType = 0 }
                ExpressiveFilterPill("Плейлисты", searchType == 1, primaryColor, textColor) { searchType = 1 }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text(if (searchType == 0) "Поиск треков..." else "Поиск плейлистов...", color = textColor.copy(0.5f), fontSize = 13.sp) },
                    singleLine = true,
                    shape = cardShape,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            if (searchType == 1) {
                                scope.launch {
                                    playlistResults = SoundCloudClient.searchPlaylists(searchQuery.trim())
                                }
                            } else {
                                onSearchCommit(searchQuery.trim(), searchType)
                            }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (searchType == 1) {
                if (playlistResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Введите запрос и нажмите поиск для плейлистов", color = textColor.copy(0.6f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(playlistResults, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { openedScPlaylist = pl }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val plCoverTrack = remember(pl) {
                                    Track(id = -pl.id, uri = Uri.EMPTY, title = pl.title, artist = pl.author, album = "SoundCloud", durationMs = 0L, mimeType = "audio/mpeg", sizeBytes = 0L, customCoverUri = pl.artworkUrl)
                                }
                                TrackCoverThumb(plCoverTrack, expressiveShape, primaryColor, 52)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${pl.author} • ${pl.trackCount} треков", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { onDownloadScPlaylist(pl) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Download, "Скачать плейлист", tint = primaryColor, modifier = Modifier.size(22.dp))
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
            } else {
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Введите название трека или имя исполнителя", color = textColor.copy(0.6f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(searchResults, key = { it.id }) { scTrack ->
                            val isLiked = likedScTracks.any { kotlin.math.abs(it.id) == kotlin.math.abs(scTrack.id) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(surfaceColor)
                                    .clickable { onPlaySoundCloudTrack(scTrack, searchResults) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dummyTrack = remember(scTrack) {
                                    Track(id = -scTrack.id, uri = Uri.EMPTY, title = scTrack.title, artist = scTrack.artist, album = "SoundCloud", durationMs = scTrack.durationMs, mimeType = "audio/mpeg", sizeBytes = 0L, customCoverUri = scTrack.artworkUrl)
                                }
                                TrackCoverThumb(dummyTrack, expressiveShape, primaryColor, 50)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(scTrack.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${scTrack.artist} • ${formatDuration(scTrack.durationMs)}", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { onToggleLikeScTrack(scTrack) }, modifier = Modifier.size(36.dp)) {
                                    Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.Favorite, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onDownloadScTrack(scTrack) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Download, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onAddScToPlaylist(scTrack) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Add, null, tint = textColor.copy(0.75f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (likedScTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нажми ❤️ рядом с треком, чтобы сохранить его сюда", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(likedScTracks, key = { trackStableKey(it) }) { savedTrack ->
                        TrackListItem(
                            track = savedTrack,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            badgeText = "SC ❤️",
                            showCoverThumb = true,
                            isSelected = false,
                            isSelectionMode = false,
                            onAddToPlaylist = {
                                onAddScToPlaylist(
                                    SoundCloudTrack(
                                        id = kotlin.math.abs(savedTrack.id),
                                        title = savedTrack.title,
                                        artist = savedTrack.artist,
                                        artworkUrl = savedTrack.customCoverUri,
                                        durationMs = savedTrack.durationMs,
                                        streamProgressiveUrl = savedTrack.filePath.ifBlank { savedTrack.uri.toString() }
                                    )
                                )
                            },
                            onLongClick = {},
                            onClick = { onPlaySavedScTrack(savedTrack) }
                        )
                    }
                }
            }
        }
    }
}
