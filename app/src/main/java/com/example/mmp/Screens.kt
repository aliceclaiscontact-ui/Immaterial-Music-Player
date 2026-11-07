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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun TrackCoverThumb(track: Track, shape: Shape, primaryColor: Color, sizeDp: Int = 48) {
    val context = LocalContext.current
    var bitmap by remember(track.id) { mutableStateOf(CoverCache.get(track.id)) }
    LaunchedEffect(track.id) { if (bitmap == null) bitmap = loadTrackThumbnail(context, track) }
    Box(modifier = Modifier.size(sizeDp.dp).clip(shape).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = primaryColor, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

// Генерация сетки из 4 обложек для плейлистов
@Composable
fun PlaylistGridCover(trackIds: List<Long>, allTracks: List<Track>, primaryColor: Color, sizeDp: Int = 54) {
    val context = LocalContext.current
    val tracks = remember(trackIds, allTracks) {
        val map = allTracks.associateBy { it.id }
        trackIds.mapNotNull { map[it] }.take(4)
    }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null, null, null) }
    
    LaunchedEffect(tracks) {
        tracks.forEachIndexed { index, track -> bitmaps[index] = loadTrackThumbnail(context, track) }
    }

    Box(modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(primaryColor.copy(0.2f))) {
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
    allTracks: List<Track>, recentTracks: List<Track>, artistSecondsAllTime: Map<String, Int>, trackPlaysAllTime: Map<Long, Int>, dailyStats: Map<String, DailyStatRecord>,
    listenedMinutesTotal: Int, firstListenDate: String, hasPermission: Boolean, isBatteryIgnored: Boolean, cardShape: Shape, expressiveShape: Shape,
    themeConfig: MMPThemeConfig, primaryColor: Color, textColor: Color, surfaceColor: Color,
    onRequestPermission: () -> Unit, onRequestBatteryIgnore: () -> Unit, onThemeChange: (MMPThemeConfig) -> Unit, onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit, onExportTheme: () -> Unit, onImportTheme: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    var selectedDateFilter by remember { mutableStateOf("ALL") }
    val availableDates = run { listOf("ALL") + dailyStats.keys.sortedDescending() }
    val activeArtistSeconds = run { if (selectedDateFilter == "ALL") artistSecondsAllTime else dailyStats[selectedDateFilter]?.artistSeconds ?: emptyMap() }
    val activeTrackPlays = run { if (selectedDateFilter == "ALL") trackPlaysAllTime else dailyStats[selectedDateFilter]?.trackPlays ?: emptyMap() }
    val totalArtistSec = activeArtistSeconds.values.sum()
    val topArtistEntry = activeArtistSeconds.maxByOrNull { it.value }
    val topArtistPercent = if (totalArtistSec > 0 && topArtistEntry != null) ((topArtistEntry.value.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100) else 0

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ExpressiveFilterPill("История прослушивания", activeSubTab == 0, primaryColor, textColor) { activeSubTab = 0 }
            IconButton(onClick = { activeSubTab = if (activeSubTab == 1) 0 else 1 }, modifier = Modifier.size(42.dp).clip(CircleShape).background(if (activeSubTab == 1) primaryColor else surfaceColor).border(1.dp, if (activeSubTab == 1) primaryColor else textColor.copy(0.15f), CircleShape)) {
                Icon(Icons.Filled.Settings, "Настройки", tint = if (activeSubTab == 1) Color.Black else textColor)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(targetState = activeSubTab, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) }, label = "") { subTab ->
            if (subTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    if (!hasPermission) item { Button(onClick = onRequestPermission, shape = cardShape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ к музыке", color = Color.Black, fontWeight = FontWeight.Bold) } }
                    item {
                        Box(modifier = Modifier.fillMaxWidth().clip(cardShape).background(Brush.linearGradient(listOf(primaryColor.copy(0.28f), Color(themeConfig.secondaryHex).copy(0.20f)))).border(1.dp, textColor.copy(0.14f), cardShape).padding(16.dp)) {
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
                                            Text(buildAnnotatedString { append("Артист "); withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Black)) { append(topArtistEntry.key) }; append(" играл $topArtistPercent% твоей музыки!") }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor, lineHeight = 22.sp)
                                        } else {
                                            Text("Слушай любимые треки, чтобы увидеть главного артиста!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.28f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Audiotrack, null, tint = primaryColor, modifier = Modifier.size(22.dp)) }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(surfaceColor.copy(0.75f)).border(1.dp, primaryColor.copy(0.4f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                                items(availableDates) { dateKey -> ExpressiveFilterPill(if (dateKey == "ALL") "За всё время" else dateKey, selectedDateFilter == dateKey, primaryColor, textColor) { selectedDateFilter = dateKey } }
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
                    val top10Tracks = run {
                        val map = allTracks.associateBy { it.id }
                        activeTrackPlays.entries.sortedByDescending { it.value }.take(10).mapNotNull { entry -> map[entry.key]?.let { it to entry.value } }
                    }
                    if (top10Tracks.isNotEmpty()) {
                        item { Text("Топ-10 треков", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor) }
                        itemsIndexed(top10Tracks) { idx, (track, count) -> TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, "#${idx + 1} • $count просл.", true, { onAddTrackToPlaylistClick(track) }) { onTrackClick(track) } }
                    }
                    item { Text("Последние 5 треков (от 20 сек)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor) }
                    if (recentTracks.isEmpty()) item { Text("Здесь появятся треки, которые ты слушала дольше 20 секунд.", color = textColor.copy(0.55f), fontSize = 13.sp) }
                    else items(recentTracks.take(5), key = { it.id }) { track -> TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onTrackClick(track) } }
                }
            } else {
                SettingsAndThemeEditor(themeConfig, isBatteryIgnored, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, onThemeChange, onRequestBatteryIgnore, onExportTheme, onImportTheme)
            }
        }
    }
}

// Универсальный заголовок в стиле Spotify для Альбомов и Плейлистов (MD3)
@Composable
fun SpotifyStyleHeaderLayout(
    title: String, subtitle: String, tracks: List<Track>, customCoverUri: String?, primaryColor: Color, textColor: Color, bgColor: Color, expressiveShape: Shape,
    onBack: () -> Unit, onTrackClick: (Track) -> Unit, onAddTrackToPlaylistClick: (Track) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(title) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(tracks, customCoverUri) {
        if (customCoverUri != null) {
            try { context.contentResolver.openInputStream(Uri.parse(customCoverUri))?.use { heroBitmap = BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
        } else {
            for (trk in tracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Brush.verticalGradient(listOf(primaryColor.copy(0.35f), bgColor)))) {
                Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.Start) {
                    Box(modifier = Modifier.size(220.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)).align(Alignment.CenterHorizontally)) {
                        if (heroBitmap != null) Image(heroBitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else if (customCoverUri == null && tracks.isNotEmpty()) PlaylistGridCover(tracks.map { it.id }, tracks, primaryColor, 220)
                        else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(100.dp).align(Alignment.Center))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(title, color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, tint = textColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(subtitle, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = primaryColor, modifier = Modifier.size(30.dp))
                            Icon(Icons.Filled.Share, null, tint = textColor.copy(0.7f), modifier = Modifier.size(28.dp))
                            Icon(Icons.Filled.MoreVert, null, tint = textColor.copy(0.7f), modifier = Modifier.size(28.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Shuffle, null, tint = primaryColor, modifier = Modifier.size(28.dp))
                            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(primaryColor).clickable { if (tracks.isNotEmpty()) onTrackClick(tracks.first()) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier.padding(top = 40.dp, start = 16.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(0.3f))) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("В этом списке пока нет треков.", color = textColor.copy(0.6f)) } }
        } else {
            items(tracks, key = { it.id }) { track ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TrackListItem(track, RoundedCornerShape(12.dp), expressiveShape, primaryColor, textColor, Color.Transparent, null, false, { onAddTrackToPlaylistClick(track) }) { onTrackClick(track) }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<Track>, playlists: List<PlaylistItem>, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, bgColor: Color,
    onRescanClick: () -> Unit, onTrackClick: (Track) -> Unit, onAddTrackToPlaylistClick: (Track) -> Unit
) {
    var category by remember { mutableStateOf(0) }
    var openedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var openedArtist by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (openedArtist != null) {
        val artistTracks = run { tracks.filter { it.artist.equals(openedArtist, ignoreCase = true) } }
        ArtistDetailScreen(openedArtist!!, artistTracks, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, bgColor, { openedArtist = null }, onTrackClick, onAddTrackToPlaylistClick)
        return
    }

    if (openedPlaylist != null) {
        val plTracks = run { val map = tracks.associateBy { it.id }; openedPlaylist!!.trackIds.mapNotNull { map[it] } }
        SpotifyStyleHeaderLayout(openedPlaylist!!.name, "Ваш плейлист • ${plTracks.size} треков", plTracks, openedPlaylist!!.coverUri, primaryColor, textColor, bgColor, expressiveShape, { openedPlaylist = null }, onTrackClick, onAddTrackToPlaylistClick)
        return
    }

    if (openedAlbum != null) {
        val albumTracks = run { tracks.filter { it.album == openedAlbum } }
        val artistName = albumTracks.firstOrNull()?.artist ?: "Неизвестный исполнитель"
        SpotifyStyleHeaderLayout(openedAlbum!!, "Альбом • $artistName", albumTracks, null, primaryColor, textColor, bgColor, expressiveShape, { openedAlbum = null }, onTrackClick, onAddTrackToPlaylistClick)
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
                    items(tracks, key = { it.id }) { track -> TrackListItem(track, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, null, true, { onAddTrackToPlaylistClick(track) }) { onTrackClick(track) } }
                }
                1 -> {
                    if (playlists.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("У вас пока нет плейлистов! :-(", color = textColor.copy(0.65f), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(playlists) { pl ->
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { openedPlaylist = pl }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                val bmp = run { if (pl.coverUri != null) { try { context.contentResolver.openInputStream(Uri.parse(pl.coverUri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } } else null }
                                Box(modifier = Modifier.size(54.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else PlaylistGridCover(pl.trackIds, tracks, primaryColor, 54)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Треков: ${pl.trackIds.size}", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
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
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { openedArtist = artist }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (artistTrackList.isNotEmpty()) { TrackCoverThumb(artistTrackList.first(), CircleShape, primaryColor, 46); Spacer(modifier = Modifier.width(12.dp)) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artist, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${artistTrackList.size} треков", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
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
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { openedAlbum = album }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (albumTrackList.isNotEmpty()) { TrackCoverThumb(albumTrackList.first(), expressiveShape, primaryColor, 50); Spacer(modifier = Modifier.width(12.dp)) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${albumTrackList.firstOrNull()?.artist ?: ""} • ${albumTrackList.size} тр.", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistName: String, artistTracks: List<Track>, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, bgColor: Color,
    onBack: () -> Unit, onTrackClick: (Track) -> Unit, onAddTrackToPlaylistClick: (Track) -> Unit
) {
    SpotifyStyleHeaderLayout(artistName, "Исполнитель • ${artistTracks.size} треков", artistTracks, null, primaryColor, textColor, bgColor, expressiveShape, onBack, onTrackClick, onAddTrackToPlaylistClick)
}

@Composable
fun MiniPlayerBar(track: Track?, metadata: DetailedMetadata, isPlaying: Boolean, currentPosMs: Long, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, onBarClick: () -> Unit, onPlayPause: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor.copy(0.96f)).border(1.dp, textColor.copy(0.14f), cardShape).clickable { onBarClick() }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
            if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track?.title ?: "Выбери трек", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (track != null) "${track.artist} • ${metadata.formatName}" else "Нажми для воспроизведения", color = textColor.copy(0.65f), style = TextStyle(fontFeatureSettings = "tnum"), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp).clip(expressiveShape).background(primaryColor)) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerOverlay(
    track: Track, metadata: DetailedMetadata, isPlaying: Boolean, currentPosMs: Long, expressiveShape: Shape, primaryColor: Color, secondaryColor: Color, bgColor: Color, textColor: Color,
    onDismiss: () -> Unit, onAddToPlaylistClick: () -> Unit, onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onNext: () -> Unit, onPrev: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable(enabled = true, onClick = {})) {
        if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(75.dp), alpha = 0.38f)
        else Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(primaryColor.copy(0.30f), secondaryColor.copy(0.20f), bgColor))))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(textColor.copy(0.35f)))
            }
            val coverScale by animateFloatAsState(if (isPlaying) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "")
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding((1f - coverScale).dp * 100).clip(expressiveShape).background(textColor.copy(0.08f)).border(1.dp, textColor.copy(0.18f), expressiveShape), contentAlignment = Alignment.Center) {
                if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(105.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.basicMarquee())
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(track.artist, color = textColor.copy(0.72f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onAddToPlaylistClick, modifier = Modifier.size(32.dp).clip(CircleShape).background(textColor.copy(0.14f))) { Icon(Icons.Filled.Add, null, tint = primaryColor, modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val safeDur = track.durationMs.coerceAtLeast(1L)
                Slider(value = currentPosMs.toFloat().coerceIn(0f, safeDur.toFloat()), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..safeDur.toFloat(), colors = SliderDefaults.colors(thumbColor = textColor, activeTrackColor = primaryColor, inactiveTrackColor = textColor.copy(0.22f)), modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Text(formatMillis(currentPosMs), color = textColor.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum")) }
                    Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) { Text("${metadata.formatName} • ${metadata.bitrateKbps} • ${metadata.sampleRateStr}", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Text(formatMillis(safeDur), color = textColor.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum")) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Shuffle, null, tint = textColor.copy(0.7f), modifier = Modifier.size(24.dp)) }
                IconButton(onClick = onPrev, modifier = Modifier.size(54.dp)) { Icon(Icons.Filled.FastRewind, null, tint = textColor, modifier = Modifier.size(40.dp)) }
                Box(modifier = Modifier.size(76.dp).clip(expressiveShape).background(primaryColor).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Filled.FastForward, null, tint = textColor, modifier = Modifier.size(40.dp)) }
                IconButton(onClick = {}, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Repeat, null, tint = textColor.copy(0.7f), modifier = Modifier.size(24.dp)) }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.VolumeDown, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                Slider(value = currentVol, onValueChange = { currentVol = it; audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVol.toFloat(), colors = SliderDefaults.colors(thumbColor = textColor, activeTrackColor = textColor.copy(0.85f), inactiveTrackColor = textColor.copy(0.22f)), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.VolumeUp, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                IconButton(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

// Меню настроек с категориями (в стиле AIMP)
@Composable
fun SettingsCategoryItem(icon: ImageVector, title: String, subtitle: String, textColor: Color, primaryColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = primaryColor, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = textColor.copy(0.6f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun SettingsAndThemeEditor(
    themeConfig: MMPThemeConfig, isBatteryIgnored: Boolean, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color,
    onThemeChange: (MMPThemeConfig) -> Unit, onRequestBatteryIgnore: () -> Unit, onExport: () -> Unit, onImport: () -> Unit
) {
    var activeSettingsMenu by remember { mutableStateOf("MAIN") }
    val presets = listOf("Neon Pink" to 0xFFFF66C4, "Acid Lime" to 0xFFA6FF00, "Electric Violet" to 0xFF9D4EDD, "Cyber Cyan" to 0xFF00F5D4, "Sunset Coral" to 0xFFFF5E5B, "Pure Gold" to 0xFFFFBE0B)
    
    AnimatedContent(targetState = activeSettingsMenu, label = "") { menu ->
        if (menu == "MAIN") {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { Text("Настройки", color = textColor, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(16.dp)) }
                item { SettingsCategoryItem(Icons.Filled.ColorLens, "Интерфейс и Темы", "Цвета, MD3 формы, светлая/темная тема, импорт тем", textColor, primaryColor) { activeSettingsMenu = "UI" } }
                item { SettingsCategoryItem(Icons.Filled.GraphicEq, "Звук (В разработке)", "Эквалайзер, Gapless, Crossfade", textColor, primaryColor) {} }
                item { SettingsCategoryItem(Icons.Filled.FolderSpecial, "Медиатека", "Фильтры по длительности, черные списки папок", textColor, primaryColor) {} }
                if (!isBatteryIgnored) item { SettingsCategoryItem(Icons.Filled.BatteryChargingFull, "Работа в фоне", "Отключить оптимизацию для стабильной работы", textColor, primaryColor) { onRequestBatteryIgnore() } }
            }
        } else if (menu == "UI") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                        IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBack, null, tint = textColor) }
                        Text("Интерфейс", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
                item {
                    Text("Режим оформления", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(ThemeMode.entries) { mode ->
                            ExpressiveFilterPill(mode.label, themeConfig.themeMode == mode, primaryColor, textColor) {
                                val newBg = if (mode == ThemeMode.LIGHT) 0xFFF7F5FA else 0xFF0D0D12
                                onThemeChange(themeConfig.copy(themeMode = mode, bgHex = newBg))
                            }
                        }
                    }
                }
                item {
                    Text("Акцентный цвет (RGB)", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(presets) { (name, hex) ->
                            Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(Color(hex)).border(if (themeConfig.primaryHex == hex) 3.dp else 0.dp, textColor, expressiveShape).clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) })
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                        Button(onClick = onExport, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Экспорт .mmpui", color = Color.Black, fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Text("Импорт", color = textColor) }
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
        if (onAddToPlaylist != null) IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp).clip(CircleShape).background(textColor.copy(0.1f))) { Icon(Icons.Filled.Add, null, tint = primaryColor, modifier = Modifier.size(18.dp)) }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.size(34.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(20.dp)) }
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
