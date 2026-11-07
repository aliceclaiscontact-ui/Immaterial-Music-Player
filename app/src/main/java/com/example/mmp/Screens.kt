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
import androidx.compose.ui.graphics.toArgb
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
fun TrackCoverThumb(
    track: Track,
    shape: Shape,
    primaryColor: Color,
    sizeDp: Int = 48
) {
    val context = LocalContext.current
    var bitmap by remember(track.id) { mutableStateOf(CoverCache.get(track.id)) }

    LaunchedEffect(track.id) {
        if (bitmap == null) {
            bitmap = loadTrackThumbnail(context, track)
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(primaryColor.copy(0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size((sizeDp * 0.5f).dp)
            )
        }
    }
}

@Composable
fun HomeScreen(
    allTracks: List<Track>,
    recentTracks: List<Track>,
    artistSecondsAllTime: Map<String, Int>,
    trackPlaysAllTime: Map<Long, Int>,
    dailyStats: Map<String, DailyStatRecord>,
    listenedMinutesTotal: Int,
    firstListenDate: String,
    hasPermission: Boolean,
    isBatteryIgnored: Boolean,
    cardShape: Shape,
    expressiveShape: Shape,
    themeConfig: MMPThemeConfig,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onRequestPermission: () -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onExportTheme: () -> Unit,
    onImportTheme: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    var selectedDateFilter by remember { mutableStateOf("ALL") }

    val availableDates = remember(dailyStats.keys.toList()) {
        listOf("ALL") + dailyStats.keys.sortedDescending()
    }

    val activeArtistSeconds: Map<String, Int> = remember(selectedDateFilter, artistSecondsAllTime, dailyStats) {
        if (selectedDateFilter == "ALL") artistSecondsAllTime
        else dailyStats[selectedDateFilter]?.artistSeconds ?: emptyMap()
    }

    val activeTrackPlays: Map<Long, Int> = remember(selectedDateFilter, trackPlaysAllTime, dailyStats) {
        if (selectedDateFilter == "ALL") trackPlaysAllTime
        else dailyStats[selectedDateFilter]?.trackPlays ?: emptyMap()
    }

    val totalArtistSec = activeArtistSeconds.values.sum()
    val topArtistEntry = activeArtistSeconds.maxByOrNull { it.value }
    val topArtistName = topArtistEntry?.key
    val topArtistPercent = if (totalArtistSec > 0 && topArtistEntry != null) {
        ((topArtistEntry.value.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100)
    } else 0

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        // Верхняя панель: без надписи MMP, справа иконка шестерёнки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveFilterPill(
                text = "История прослушивания",
                selected = activeSubTab == 0,
                primaryColor = primaryColor,
                textColor = textColor
            ) { activeSubTab = 0 }

            IconButton(
                onClick = { activeSubTab = if (activeSubTab == 1) 0 else 1 },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (activeSubTab == 1) primaryColor else surfaceColor)
                    .border(1.dp, if (activeSubTab == 1) primaryColor else textColor.copy(0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = if (activeSubTab == 1) Color.Black else textColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = activeSubTab,
            transitionSpec = {
                fadeIn(tween(240)) togetherWith fadeOut(tween(180))
            },
            label = "HomeSubTab"
        ) { subTab ->
            if (subTab == 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!hasPermission) {
                        item {
                            Button(
                                onClick = onRequestPermission,
                                shape = cardShape,
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Разрешить доступ к музыке", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Карточка сводки (компактнее + пилюля с аудиоволной и датой снизу)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(primaryColor.copy(0.28f), Color(themeConfig.secondaryHex).copy(0.20f))
                                    )
                                )
                                .border(1.dp, textColor.copy(0.14f), cardShape)
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("СВЕДЕНИЯ О ПРОСЛУШИВАНИИ", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (topArtistName != null && totalArtistSec > 0) {
                                            Text(
                                                text = buildAnnotatedString {
                                                    append("Артист ")
                                                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Black)) {
                                                        append(topArtistName)
                                                    }
                                                    append(" играл $topArtistPercent% твоей музыки!")
                                                },
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                lineHeight = 22.sp
                                            )
                                        } else {
                                            Text(
                                                text = "Слушай любимые треки, чтобы увидеть главного артиста!",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.28f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Audiotrack, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Пилюля снизу: знак аудиоволны XX минут прослушано музыки с dd.mm.yyyy
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(surfaceColor.copy(0.75f))
                                        .border(1.dp, primaryColor.copy(0.4f), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.GraphicEq,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$listenedMinutesTotal минут прослушано музыки с $firstListenDate",
                                            color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Фильтр по датам
                    item {
                        Column {
                            Text("Статистика по датам", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableDates) { dateKey ->
                                    val label = if (dateKey == "ALL") "За всё время" else dateKey
                                    ExpressiveFilterPill(
                                        text = label,
                                        selected = selectedDateFilter == dateKey,
                                        primaryColor = primaryColor,
                                        textColor = textColor
                                    ) { selectedDateFilter = dateKey }
                                }
                            }
                        }
                    }

                    // Распределение процентов по артистам за выбранную дату
                    if (activeArtistSeconds.isNotEmpty() && totalArtistSec > 0) {
                        item {
                            val sortedArtists = activeArtistSeconds.entries.sortedByDescending { it.value }.take(5)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(surfaceColor)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Процент времени по артистам", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                sortedArtists.forEach { (artist, sec) ->
                                    val pct = ((sec.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100)
                                    val mins = sec / 60
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(artist, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$pct% (${mins}м ${sec % 60}с)", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Топ-10 самых прослушиваемых треков с количеством прослушек
                    val top10Tracks = run {
                        val map = allTracks.associateBy { it.id }
                        activeTrackPlays.entries
                            .sortedByDescending { it.value }
                            .take(10)
                            .mapNotNull { entry -> map[entry.key]?.let { it to entry.value } }
                    }

                    if (top10Tracks.isNotEmpty()) {
                        item {
                            Text("Топ-10 треков", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        itemsIndexed(top10Tracks) { idx, (track, count) ->
                            TrackListItem(
                                track = track,
                                cardShape = cardShape,
                                expressiveShape = expressiveShape,
                                primaryColor = primaryColor,
                                textColor = textColor,
                                surfaceColor = surfaceColor,
                                badgeText = "#${idx + 1} • $count просл.",
                                onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                                onClick = { onTrackClick(track) }
                            )
                        }
                    }

                    // Последние 5 треков (только те, что играли > 20 секунд)
                    item {
                        Text("Последние 5 треков (от 20 сек)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    if (recentTracks.isEmpty()) {
                        item {
                            Text(
                                "Здесь появятся треки, которые ты слушала дольше 20 секунд.",
                                color = textColor.copy(0.55f),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(recentTracks.take(5), key = { it.id }) { track ->
                            TrackListItem(
                                track = track,
                                cardShape = cardShape,
                                expressiveShape = expressiveShape,
                                primaryColor = primaryColor,
                                textColor = textColor,
                                surfaceColor = surfaceColor,
                                onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                                onClick = { onTrackClick(track) }
                            )
                        }
                    }
                }
            } else {
                SettingsAndThemeEditor(
                    themeConfig = themeConfig,
                    isBatteryIgnored = isBatteryIgnored,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onThemeChange = onThemeChange,
                    onRequestBatteryIgnore = onRequestBatteryIgnore,
                    onExport = onExportTheme,
                    onImport = onImportTheme
                )
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
    onRescanClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit
) {
    var category by remember { mutableStateOf(0) }
    var openedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var openedArtist by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Если открыта страница Автора (в стиле Apple Music по фото)
    if (openedArtist != null) {
        val artistTracks = tracks.filter { it.artist.equals(openedArtist, ignoreCase = true) }
        ArtistDetailScreen(
            artistName = openedArtist!!,
            artistTracks = artistTracks,
            cardShape = cardShape,
            expressiveShape = expressiveShape,
            primaryColor = primaryColor,
            textColor = textColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            onBack = { openedArtist = null },
            onTrackClick = onTrackClick,
            onAddTrackToPlaylistClick = onAddTrackToPlaylistClick
        )
        return
    }

    // Если открыт конкретный плейлист
    if (openedPlaylist != null) {
        val currentPl = playlists.find { it.name == openedPlaylist!!.name } ?: openedPlaylist!!
        val plTracks = run {
            val map = tracks.associateBy { it.id }
            currentPl.trackIds.mapNotNull { map[it] }
        }
        BackHandler { openedPlaylist = null }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { openedPlaylist = null },
                    modifier = Modifier.clip(CircleShape).background(surfaceColor)
                ) {
                    Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(currentPl.name, fontSize = 24.sp, fontWeight = FontWeight.Black, color = textColor)
                    Text("Треков: ${plTracks.size}", fontSize = 13.sp, color = textColor.copy(0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (plTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "В этом плейлисте пока нет треков.\nНажми «+» рядом с любой песней, чтобы добавить её сюда!",
                        color = textColor.copy(0.6f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(plTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
        }
        return
    }

    // Если открыт конкретный альбом
    if (openedAlbum != null) {
        val albumTracks = run {
            tracks.filter { it.album == openedAlbum }
        }
        BackHandler { openedAlbum = null }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { openedAlbum = null },
                    modifier = Modifier.clip(CircleShape).background(surfaceColor)
                ) {
                    Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(openedAlbum!!, fontSize = 22.sp, fontWeight = FontWeight.Black, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Треков: ${albumTracks.size}", fontSize = 13.sp, color = textColor.copy(0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(albumTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        cardShape = cardShape,
                        expressiveShape = expressiveShape,
                        primaryColor = primaryColor,
                        textColor = textColor,
                        surfaceColor = surfaceColor,
                        onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Моя медиатека", fontSize = 28.sp, fontWeight = FontWeight.Black, color = textColor)
            IconButton(
                onClick = onRescanClick,
                modifier = Modifier.clip(CircleShape).background(surfaceColor)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = textColor)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item { ExpressiveFilterPill("Треки (${tracks.size})", category == 0, primaryColor, textColor) { category = 0 } }
            item { ExpressiveFilterPill("Плейлисты (${playlists.size})", category == 1, primaryColor, textColor) { category = 1 } }
            item { ExpressiveFilterPill("Авторы", category == 2, primaryColor, textColor) { category = 2 } }
            item { ExpressiveFilterPill("Альбомы", category == 3, primaryColor, textColor) { category = 3 } }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = category, label = "LibCategory") { cat ->
            when (cat) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            cardShape = cardShape,
                            expressiveShape = expressiveShape,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
                1 -> {
                    if (playlists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "У вас пока нет плейлистов! :-(",
                                color = textColor.copy(0.65f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                            items(playlists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(surfaceColor)
                                        .clickable { openedPlaylist = pl }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        val bmp = run {
                                            pl.coverUri?.let { uriStr ->
                                                try {
                                                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
                                                } catch (_: Exception) { null }
                                            }
                                        }
                                        Box(modifier = Modifier.size(54.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                                            if (bmp != null) {
                                                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Icon(Icons.Filled.QueueMusic, null, tint = primaryColor)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Треков: ${pl.trackIds.size}", color = textColor.copy(0.6f), fontSize = 12.sp)
                                        }
                                    }
                                    Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.5f))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val artists = run { tracks.groupBy { it.artist } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(artists.keys.toList()) { artist ->
                            val artistTrackList = artists[artist] ?: emptyList()
                            val firstTrack = artistTrackList.firstOrNull()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(surfaceColor)
                                    .clickable { openedArtist = artist }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (firstTrack != null) {
                                        TrackCoverThumb(track = firstTrack, shape = CircleShape, primaryColor = primaryColor, sizeDp = 46)
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(artist, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${artistTrackList.size} треков", color = textColor.copy(0.6f), fontSize = 12.sp)
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                }
                else -> {
                    // Раздел Альбомы с миниатюрой обложки слева
                    val albums = run { tracks.groupBy { it.album } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(albums.keys.toList()) { album ->
                            val albumTrackList = albums[album] ?: emptyList()
                            val representativeTrack = albumTrackList.firstOrNull()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(surfaceColor)
                                    .clickable { openedAlbum = album }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (representativeTrack != null) {
                                        TrackCoverThumb(track = representativeTrack, shape = expressiveShape, primaryColor = primaryColor, sizeDp = 50)
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${representativeTrack?.artist ?: ""} • ${albumTrackList.size} тр.",
                                            color = textColor.copy(0.6f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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

// Страница Автора в стиле Apple Music (по прикреплённому скриншоту)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    artistTracks: List<Track>,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(artistName) { mutableStateOf<Bitmap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(artistTracks) {
        for (trk in artistTracks) {
            val bmp = loadTrackThumbnail(context, trk)
            if (bmp != null) {
                heroBitmap = bmp
                break
            }
        }
    }

    val filteredTracks = run {
        if (searchQuery.isBlank()) artistTracks
        else artistTracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                if (heroBitmap != null) {
                    Image(
                        bitmap = heroBitmap!!.asImageBitmap(),
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(primaryColor.copy(0.45f), bgColor))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = textColor.copy(0.3f), modifier = Modifier.size(110.dp))
                    }
                }

                // Плавный градиент в цвет фона снизу как в Apple Music
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(0.2f),
                                    Color.Transparent,
                                    bgColor.copy(0.85f),
                                    bgColor
                                )
                            )
                        )
                )

                // Верхняя кнопка Назад
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.45f))
                    ) {
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Назад", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Имя артиста и кнопки Shuffle / Play / Search по центру внизу шапки
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = artistName,
                        color = textColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (artistTracks.isNotEmpty()) onTrackClick(artistTracks.random())
                            }
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = textColor, modifier = Modifier.size(28.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(expressiveShape)
                                .background(primaryColor)
                                .clickable {
                                    if (artistTracks.isNotEmpty()) onTrackClick(artistTracks.first())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = textColor, modifier = Modifier.size(28.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Songs", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.6f))
            }
        }

        items(filteredTracks, key = { it.id }) { track ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                TrackListItem(
                    track = track,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    showCoverThumb = true,
                    onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

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
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor.copy(0.96f))
            .border(1.dp, textColor.copy(0.14f), cardShape)
            .clickable { onBarClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)),
                contentAlignment = Alignment.Center
            ) {
                if (metadata.coverArt != null) {
                    Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Выбери трек",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (track != null) "${track.artist} • ${metadata.formatName} • ${formatMillis(currentPosMs)}" else "Нажми для воспроизведения",
                    color = textColor.copy(0.65f),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(44.dp).clip(expressiveShape).background(primaryColor)
        ) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black)
        }
    }
}

// Полноэкранный плеер: бегущая строка, уменьшенные кнопки + и вниз, стабильная строка формата и общее время справа
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerOverlay(
    track: Track,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(enabled = true, onClick = {})
    ) {
        if (metadata.coverArt != null) {
            Image(
                bitmap = metadata.coverArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(75.dp),
                alpha = 0.38f
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(primaryColor.copy(0.30f), secondaryColor.copy(0.20f), bgColor)))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(textColor.copy(0.35f))
                )
            }

            val coverScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "CoverBounce"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding((1f - coverScale).dp * 100)
                    .clip(expressiveShape)
                    .background(textColor.copy(0.08f))
                    .border(1.dp, textColor.copy(0.18f), expressiveShape),
                contentAlignment = Alignment.Center
            ) {
                if (metadata.coverArt != null) {
                    Image(
                        bitmap = metadata.coverArt.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(105.dp))
                }
            }

            // Название с бегущей строкой и уменьшенные кнопки "+" и стрелка вниз (32.dp)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = textColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = textColor.copy(0.72f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onAddToPlaylistClick,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Добавить в плейлист",
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Свернуть",
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val safeDur = track.durationMs.coerceAtLeast(1L)

                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, safeDur.toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..safeDur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = textColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = textColor.copy(0.22f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3 фиксированные колонки + табличные цифры tnum: надпись про формат и кбпс больше никогда не дёргается
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = formatMillis(currentPosMs),
                            color = textColor.copy(0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                    Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${metadata.formatName} • ${metadata.bitrateKbps}",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = formatMillis(safeDur),
                            color = textColor.copy(0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Filled.FastRewind, contentDescription = null, tint = textColor, modifier = Modifier.size(40.dp))
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(expressiveShape)
                        .background(primaryColor)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Filled.FastForward, contentDescription = null, tint = textColor, modifier = Modifier.size(40.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.VolumeDown, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
                Slider(
                    value = currentVol,
                    onValueChange = {
                        currentVol = it
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    },
                    valueRange = 0f..maxVol.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = textColor,
                        activeTrackColor = textColor.copy(0.85f),
                        inactiveTrackColor = textColor.copy(0.22f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.VolumeUp, null, tint = textColor.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SettingsAndThemeEditor(
    themeConfig: MMPThemeConfig,
    isBatteryIgnored: Boolean,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val presets = listOf(
        "Neon Pink" to 0xFFFF66C4,
        "Acid Lime" to 0xFFA6FF00,
        "Electric Violet" to 0xFF9D4EDD,
        "Cyber Cyan" to 0xFF00F5D4,
        "Sunset Coral" to 0xFFFF5E5B,
        "Pure Gold" to 0xFFFFBE0B
    )

    var showCustomColorPicker by remember { mutableStateOf(false) }
    var customRed by remember { mutableStateOf(((themeConfig.primaryHex shr 16) and 0xFF).toFloat()) }
    var customGreen by remember { mutableStateOf(((themeConfig.primaryHex shr 8) and 0xFF).toFloat()) }
    var customBlue by remember { mutableStateOf((themeConfig.primaryHex and 0xFF).toFloat()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Настройки и темы MD3 Expressive", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }

        // Выбор режима темы: Тёмная, Светлая, Adaptive
        item {
            Text("Режим оформления", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ThemeMode.entries) { mode ->
                    ExpressiveFilterPill(
                        text = mode.label,
                        selected = themeConfig.themeMode == mode,
                        primaryColor = primaryColor,
                        textColor = textColor
                    ) {
                        val newBg = if (mode == ThemeMode.LIGHT) 0xFFF7F5FA else 0xFF0D0D12
                        onThemeChange(themeConfig.copy(themeMode = mode, bgHex = newBg))
                    }
                }
            }
        }

        // Палитра + свой цвет RGB
        item {
            Text("Акцентный цвет интерфейса", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                items(presets) { (name, hex) ->
                    val selected = themeConfig.primaryHex == hex
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(expressiveShape)
                            .background(Color(hex))
                            .border(if (selected) 3.dp else 0.dp, textColor, expressiveShape)
                            .clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) }
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(surfaceColor)
                            .border(1.dp, primaryColor, RoundedCornerShape(50))
                            .clickable { showCustomColorPicker = !showCustomColorPicker }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Свой цвет", color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showCustomColorPicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(cardShape)
                        .background(surfaceColor)
                        .padding(14.dp)
                ) {
                    val previewColor = Color(customRed.toInt(), customGreen.toInt(), customBlue.toInt())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Палитра RGB", color = textColor, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(previewColor))
                    }
                    Slider(value = customRed, onValueChange = { customRed = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(activeTrackColor = Color.Red))
                    Slider(value = customGreen, onValueChange = { customGreen = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(activeTrackColor = Color.Green))
                    Slider(value = customBlue, onValueChange = { customBlue = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(activeTrackColor = Color.Blue))
                    Button(
                        onClick = {
                            val hex = (0xFFL shl 24) or (customRed.toLong() shl 16) or (customGreen.toLong() shl 8) or customBlue.toLong()
                            onThemeChange(themeConfig.copy(themeName = "Custom RGB", primaryHex = hex))
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = previewColor)
                    ) {
                        Text("Применить свой цвет", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Формы MD3
        item {
            Text("Форма элементов (MD3 Expressive Shapes)", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(ShapeStyle.entries) { style ->
                    ExpressiveFilterPill(
                        text = style.label,
                        selected = themeConfig.shapeStyle == style,
                        primaryColor = primaryColor,
                        textColor = textColor
                    ) {
                        onThemeChange(themeConfig.copy(shapeStyle = style))
                    }
                }
            }
        }

        // Пункт про закрепление в фоне скрывается навсегда, если пользователь его уже нажал
        if (!isBatteryIgnored) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(surfaceColor)
                        .border(1.dp, textColor.copy(0.12f), cardShape)
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Работа в фоне", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Отключи ограничение батареи один раз, чтобы плеер никогда не закрывался в фоне:", color = textColor.copy(0.65f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRequestBatteryIgnore,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Filled.BatteryChargingFull, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Закрепить в фоне навсегда", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onExport,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Экспорт .mmpui", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onImport,
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Импорт темы", color = textColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun FrostedDockBar(
    selectedTab: Int,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onTabSelected: (Int) -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor.copy(0.95f))
            .border(1.dp, textColor.copy(0.14f), cardShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockVerticalItem(
            icon = Icons.Filled.Home,
            label = "Главная",
            selected = selectedTab == 0,
            primaryColor = primaryColor,
            textColor = textColor,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(0) }
        )
        DockVerticalItem(
            icon = Icons.Filled.LibraryMusic,
            label = "Медиатека",
            selected = selectedTab == 1,
            primaryColor = primaryColor,
            textColor = textColor,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
        DockVerticalItem(
            icon = Icons.Filled.AddCircleOutline,
            label = "Создать",
            selected = false,
            primaryColor = primaryColor,
            textColor = textColor,
            modifier = Modifier.weight(1f),
            onClick = onCreateClick
        )
    }
}

@Composable
fun DockVerticalItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    primaryColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) primaryColor.copy(0.24f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) primaryColor else textColor.copy(0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) primaryColor else textColor.copy(0.75f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExpressiveFilterPill(
    text: String,
    selected: Boolean,
    primaryColor: Color,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(if (selected) primaryColor else textColor.copy(0.08f))
            .border(1.dp, if (selected) primaryColor else textColor.copy(0.16f), pillShape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// Элемент трека с кнопкой "+" для добавления в плейлист
@Composable
fun TrackListItem(
    track: Track,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color = Color.White,
    surfaceColor: Color = Color.White.copy(0.06f),
    badgeText: String? = null,
    showCoverThumb: Boolean = true,
    onAddToPlaylist: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (showCoverThumb) {
                TrackCoverThumb(track = track, shape = expressiveShape, primaryColor = primaryColor, sizeDp = 44)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sub = if (badgeText != null) "$badgeText • ${track.artist}" else "${track.artist} • ${formatMillis(track.durationMs)}"
                Text(
                    text = sub,
                    color = textColor.copy(0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(textColor.copy(0.1f))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "В плейлист", tint = primaryColor, modifier = Modifier.size(18.dp))
                }
            }
            Box(
                modifier = Modifier.size(34.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Плавно выплывающее окно "В какой плейлист добавить?"
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<PlaylistItem>,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistItem) -> Unit,
    onCreateNewPlaylistClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .clip(cardShape)
                .background(surfaceColor)
                .border(1.dp, textColor.copy(0.15f), cardShape)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(14.dp))
            Text("В какой плейлист добавить?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(track.title, color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(14.dp))

            if (playlists.isEmpty()) {
                Text("У вас пока нет плейлистов! :-(", color = textColor.copy(0.65f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCreateNewPlaylistClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Создать первый плейлист", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(playlists) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(textColor.copy(0.06f))
                                .clickable { onSelectPlaylist(pl) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(38.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Треков: ${pl.trackIds.size}", color = textColor.copy(0.6f), fontSize = 11.sp)
                                }
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
fun CreateMenuSheet(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onSelectPlaylist: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .clip(cardShape)
                .background(surfaceColor)
                .border(1.dp, textColor.copy(0.15f), cardShape)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Что создаём?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(primaryColor.copy(0.18f))
                    .border(1.dp, primaryColor, cardShape)
                    .clickable { onSelectPlaylist() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clip(expressiveShape).background(primaryColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LibraryAdd, null, tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Выбрать свою обложку и название", color = textColor.copy(0.65f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDetailsDialog(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    selectedCoverUri: String?,
    onPickCoverClick: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coverBitmap = run {
        selectedCoverUri?.let {
            try {
                context.contentResolver.openInputStream(Uri.parse(it))?.use { s -> BitmapFactory.decodeStream(s) }
            } catch (_: Exception) { null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = { Text("Новый плейлист", color = textColor, fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
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
                            Icon(Icons.Filled.AddPhotoAlternate, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Обложка", color = textColor.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Название плейлиста", color = textColor.copy(0.45f)) },
                    singleLine = true,
                    shape = cardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = textColor.copy(0.25f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedCoverUri) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Создать", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) }
        }
    )
}
