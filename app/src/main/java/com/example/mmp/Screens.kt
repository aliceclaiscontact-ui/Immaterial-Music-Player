package com.example.mmp

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    recentTracks: List<Track>,
    allTracksCount: Int,
    artistPlayCounts: Map<String, Int>,
    listenedMinutes: Int,
    listenedSeconds: Int,
    hasPermission: Boolean,
    cardShape: Shape,
    expressiveShape: Shape,
    themeConfig: MMPThemeConfig,
    onRequestPermission: () -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onTrackClick: (Track) -> Unit,
    onExportTheme: () -> Unit,
    onImportTheme: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    val primaryColor = Color(themeConfig.primaryHex)

    val insightText = remember(artistPlayCounts.toMap()) {
        val total = artistPlayCounts.values.sum()
        val top = artistPlayCounts.maxByOrNull { it.value }
        if (total > 0 && top != null) {
            "Артист ${top.key} играл ${((top.value.toFloat() / total) * 100).toInt()}% твоей музыки!"
        } else {
            "Включи любой трек, чтобы собрать статистику!"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MMP", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.5.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primaryColor.copy(0.18f))
                    .border(1.dp, primaryColor.copy(0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${listenedMinutes}м ${listenedSeconds}с в музыке", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                ExpressiveFilterPill("История прослушивания", activeSubTab == 0, primaryColor) { activeSubTab = 0 }
            }
            item {
                ExpressiveFilterPill("Настройки и MD3", activeSubTab == 1, primaryColor) { activeSubTab = 1 }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = activeSubTab,
            transitionSpec = {
                fadeIn(tween(260)) + slideInHorizontally { if (targetState > initialState) it / 5 else -it / 5 } togetherWith
                        fadeOut(tween(200))
            },
            label = "HomeSubTab"
        ) { subTab ->
            if (subTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
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
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(Brush.linearGradient(listOf(primaryColor.copy(0.35f), Color(themeConfig.secondaryHex).copy(0.25f))))
                                .border(1.dp, Color.White.copy(0.18f), cardShape)
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("СВЕДЕНИЯ О ПРОСЛУШИВАНИИ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(insightText, fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 26.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Треков в памяти телефона: $allTracksCount", fontSize = 13.sp, color = Color.White.copy(0.75f))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier.size(58.dp).clip(expressiveShape).background(primaryColor.copy(0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Audiotrack, null, tint = primaryColor, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                    item {
                        Text("Последние 5 треков", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (recentTracks.isEmpty()) {
                        item {
                            Text("Ты ещё не включала треки. Выбери песню в Медиатеке!", color = Color.White.copy(0.55f), fontSize = 14.sp)
                        }
                    } else {
                        items(recentTracks.take(5), key = { it.id }) { track ->
                            TrackListItem(track, cardShape, expressiveShape, primaryColor) { onTrackClick(track) }
                        }
                    }
                }
            } else {
                SettingsAndThemeEditor(
                    themeConfig = themeConfig,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
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
    onRescanClick: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    var category by remember { mutableStateOf(0) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Моя медиатека", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            IconButton(
                onClick = onRescanClick,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(0.1f))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // LazyRow гарантирует, что кнопки никогда не сожмутся в "Аль-бом-ы"
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item { ExpressiveFilterPill("Треки (${tracks.size})", category == 0, primaryColor) { category = 0 } }
            item { ExpressiveFilterPill("Плейлисты (${playlists.size})", category == 1, primaryColor) { category = 1 } }
            item { ExpressiveFilterPill("Исполнители", category == 2, primaryColor) { category = 2 } }
            item { ExpressiveFilterPill("Альбомы", category == 3, primaryColor) { category = 3 } }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = category, label = "LibCategory") { cat ->
            when (cat) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track ->
                        TrackListItem(track, cardShape, expressiveShape, primaryColor) { onTrackClick(track) }
                    }
                }
                1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(playlists) { pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(Color.White.copy(0.06f)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = remember(pl.coverUri) {
                                pl.coverUri?.let { uriStr ->
                                    try {
                                        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
                                    } catch (_: Exception) { null }
                                }
                            }
                            Box(modifier = Modifier.size(52.dp).clip(expressiveShape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Filled.QueueMusic, null, tint = primaryColor)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(pl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Пользовательский плейлист", color = Color.White.copy(0.6f), fontSize = 12.sp)
                            }
                        }
                    }
                }
                2 -> {
                    val artists = remember(tracks) { tracks.groupBy { it.artist } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(artists.keys.toList()) { artist ->
                            val count = artists[artist]?.size ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(cardShape).background(Color.White.copy(0.06f))
                                    .clickable { artists[artist]?.firstOrNull()?.let(onTrackClick) }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(artist, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count тр.", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                else -> {
                    val albums = remember(tracks) { tracks.groupBy { it.album } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(albums.keys.toList()) { album ->
                            val count = albums[album]?.size ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(cardShape).background(Color.White.copy(0.06f))
                                    .clickable { albums[album]?.firstOrNull()?.let(onTrackClick) }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(album, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count тр.", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
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
    onBarClick: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF191923).copy(0.94f))
            .border(1.dp, Color.White.copy(0.14f), cardShape)
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (track != null) "${track.artist} • ${metadata.formatName} • ${formatMillis(currentPosMs)}" else "Нажми для воспроизведения",
                    color = Color.White.copy(0.65f),
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

// Полноэкранный плеер по макету фото №5 в стиле MD3 Expressive
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
    onDismiss: () -> Unit,
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
    var isFavorite by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(enabled = true, onClick = {})
    ) {
        // Размытое атмосферное свечение от обложки/темы на весь экран
        if (metadata.coverArt != null) {
            Image(
                bitmap = metadata.coverArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(75.dp),
                alpha = 0.42f
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(primaryColor.copy(0.32f), secondaryColor.copy(0.22f), bgColor)))
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Ручка закрытия сверху как в Apple Music / MD3 Sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.4f))
                )
            }

            // Большая обложка с Expressive-формой (Сквиркл / Звезда / Сердце / Клевер)
            val coverScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.90f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "CoverBounce"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding((1f - coverScale).dp * 120)
                    .clip(expressiveShape)
                    .background(Color.White.copy(0.08f))
                    .border(1.dp, Color.White.copy(0.18f), expressiveShape),
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
                    Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(110.dp))
                }
            }

            // Название и артист слева, Избранное и Меню справа (как на фото №5)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = Color.White.copy(0.72f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(
                            onClick = { isFavorite = !isFavorite },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f))
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                tint = if (isFavorite) primaryColor else Color.White
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f))
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Прогресс-бар с точными секундами слева (0:05) и остатком справа (-4:48)
                val safeDur = track.durationMs.coerceAtLeast(1L)
                val remainingMs = (safeDur - currentPosMs).coerceAtLeast(0L)

                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, safeDur.toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..safeDur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = Color.White.copy(0.24f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMillis(currentPosMs), color = Color.White.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${metadata.formatName} • ${metadata.bitrateKbps}", color = primaryColor.copy(0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("-${formatMillis(remainingMs)}", color = Color.White.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Крупные кнопки управления воспроизведением (MD3 Expressive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(expressiveShape)
                        .background(primaryColor)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }

            // Ползунок громкости как на фото №5
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.VolumeDown, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(20.dp))
                Slider(
                    value = currentVol,
                    onValueChange = {
                        currentVol = it
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    },
                    valueRange = 0f..maxVol.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(0.85f),
                        inactiveTrackColor = Color.White.copy(0.22f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.VolumeUp, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(20.dp))
            }

            // Нижний ряд быстрых иконок (Текст / Аудиовыход / Очередь)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Filled.Notes, null, tint = Color.White.copy(0.65f)) }
                IconButton(onClick = {}) { Icon(Icons.Filled.SpeakerGroup, null, tint = primaryColor) }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.QueueMusic, null, tint = Color.White.copy(0.65f)) }
            }
        }
    }
}

@Composable
fun SettingsAndThemeEditor(
    themeConfig: MMPThemeConfig,
    cardShape: Shape,
    expressiveShape: Shape,
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
        "Sunset Coral" to 0xFFFF5E5B
    )
    val primaryColor = Color(themeConfig.primaryHex)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Кастомизация MD3 Expressive", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        item {
            Text("Акцентный цвет", color = Color.White.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(presets) { (name, hex) ->
                    val selected = themeConfig.primaryHex == hex
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(expressiveShape)
                            .background(Color(hex))
                            .border(if (selected) 3.dp else 0.dp, Color.White, expressiveShape)
                            .clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) }
                    )
                }
            }
        }
        item {
            Text("Форма элементов (MD3 Shapes)", color = Color.White.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(ShapeStyle.entries) { style ->
                    ExpressiveFilterPill(
                        text = style.label,
                        selected = themeConfig.shapeStyle == style,
                        primaryColor = primaryColor
                    ) {
                        onThemeChange(themeConfig.copy(shapeStyle = style))
                    }
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color.White.copy(0.06f))
                    .border(1.dp, Color.White.copy(0.12f), cardShape)
                    .padding(16.dp)
            ) {
                Column {
                    Text("Работа в фоне и уведомления", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Чтобы система не закрывала плеер в фоне без ручного закрепления:", color = Color.White.copy(0.65f), fontSize = 13.sp)
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
                    Text("Импорт темы", color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

// Компактная Док-панель: иконка сверху, текст снизу, ничего не вылезает
@Composable
fun FrostedDockBar(
    selectedTab: Int,
    cardShape: Shape,
    primaryColor: Color,
    onTabSelected: (Int) -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF14141D).copy(0.92f))
            .border(1.dp, Color.White.copy(0.14f), cardShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockVerticalItem(
            icon = Icons.Filled.Home,
            label = "Главная",
            selected = selectedTab == 0,
            primaryColor = primaryColor,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(0) }
        )
        DockVerticalItem(
            icon = Icons.Filled.LibraryMusic,
            label = "Медиатека",
            selected = selectedTab == 1,
            primaryColor = primaryColor,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
        DockVerticalItem(
            icon = Icons.Filled.AddCircleOutline,
            label = "Создать",
            selected = false,
            primaryColor = primaryColor,
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
                tint = if (selected) primaryColor else Color.White.copy(0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) primaryColor else Color.White.copy(0.75f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExpressiveFilterPill(text: String, selected: Boolean, primaryColor: Color, onClick: () -> Unit) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(if (selected) primaryColor else Color.White.copy(0.08f))
            .border(1.dp, if (selected) primaryColor else Color.White.copy(0.16f), pillShape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun TrackListItem(track: Track, cardShape: Shape, expressiveShape: Shape, primaryColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White.copy(0.06f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${track.artist} • ${formatMillis(track.durationMs)}", color = Color.White.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(34.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(20.dp))
        }
    }
}

// Шаг 1 создания: плавно выезжающее меню с кнопкой "Плейлист"
@Composable
fun CreateMenuSheet(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
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
                .background(Color(0xFF181824))
                .border(1.dp, Color.White.copy(0.15f), cardShape)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Что создаём?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
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
                    Text("Плейлист", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Выбрать свою обложку и название", color = Color.White.copy(0.65f), fontSize = 12.sp)
                }
            }
        }
    }
}

// Шаг 2 создания: окно с выбором обложки и названием плейлиста
@Composable
fun CreatePlaylistDetailsDialog(
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    selectedCoverUri: String?,
    onPickCoverClick: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coverBitmap = remember(selectedCoverUri) {
        selectedCoverUri?.let {
            try {
                context.contentResolver.openInputStream(Uri.parse(it))?.use { s -> BitmapFactory.decodeStream(s) }
            } catch (_: Exception) { null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = Color(0xFF181824),
        title = { Text("Новый плейлист", color = Color.White, fontWeight = FontWeight.Black) },
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
                            Text("Обложка", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Название плейлиста", color = Color.White.copy(0.45f)) },
                    singleLine = true,
                    shape = cardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(0.25f)
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
            TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White.copy(0.7f)) }
        }
    )
}
