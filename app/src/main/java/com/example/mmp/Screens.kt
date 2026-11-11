package com.example.mmp

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun YouTubeMusicLogoBadge(sizeDp: Int = 44) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFFFF0033), radius = r)
        drawCircle(
            color = Color.White,
            radius = r * 0.62f,
            style = Stroke(width = size.minDimension * 0.06f)
        )
        val path = Path().apply {
            val left = center.x - r * 0.22f
            val right = center.x + r * 0.34f
            val top = center.y - r * 0.28f
            val bottom = center.y + r * 0.28f
            moveTo(left, top)
            lineTo(right, center.y)
            lineTo(left, bottom)
            close()
        }
        drawPath(path = path, color = Color.White)
    }
}

@Composable
fun SoundCloudLogoBadge(sizeDp: Int = 44) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFFFF5500), radius = r)
        val barWidth = size.minDimension * 0.055f
        val baseLine = center.y + r * 0.26f
        val heights = listOf(0.22f, 0.36f, 0.48f, 0.42f, 0.56f)
        val startX = center.x - r * 0.46f
        heights.forEachIndexed { idx, hRatio ->
            val x = startX + idx * (barWidth * 1.65f)
            drawLine(
                color = Color.White,
                start = Offset(x, baseLine),
                end = Offset(x, baseLine - r * hRatio),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color.White,
            radius = r * 0.22f,
            center = Offset(center.x + r * 0.26f, baseLine - r * 0.22f)
        )
    }
}

@Composable
fun AimpStyleOnboardingOverlay(
    selectedProvider: StreamingProvider,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    bgColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onSelectProvider: (StreamingProvider) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(surfaceColor)
                .border(1.dp, textColor.copy(0.14f), cardShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .width(if (step == idx) 28.dp else 8.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (step == idx) primaryColor else textColor.copy(0.22f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                0 -> {
                    Box(
                        modifier = Modifier.size(76.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(42.dp))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text("Добро пожаловать в Imma3", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Модульный аудиоплеер с живой физикой Material Design 3 Expressive, эквалайзером, офлайн-текстами и стримингом без ограничений.",
                        color = textColor.copy(0.72f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
                1 -> {
                    Box(
                        modifier = Modifier.size(76.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.TouchApp, null, tint = primaryColor, modifier = Modifier.size(42.dp))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text("Жесты и управление", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("• Двойной тап слева/справа обложки: перемотка ±10 сек", color = textColor.copy(0.85f), fontSize = 13.sp)
                        Text("• Двойной тап по центру обложки: Пауза / Плей", color = textColor.copy(0.85f), fontSize = 13.sp)
                        Text("• Тап по обложке: синхронный текст (работает и без интернета)", color = textColor.copy(0.85f), fontSize = 13.sp)
                        Text("• В меню «⋮» доступен A-B Лупер и генератор карточки трека", color = textColor.copy(0.85f), fontSize = 13.sp)
                    }
                }
                else -> {
                    Text("Выбери источник стриминга", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ты всегда сможешь переключить платформу в Настройках", color = textColor.copy(0.65f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(if (selectedProvider == StreamingProvider.YOUTUBE_MUSIC) primaryColor.copy(0.2f) else textColor.copy(0.06f))
                            .border(if (selectedProvider == StreamingProvider.YOUTUBE_MUSIC) 1.5.dp else 1.dp, if (selectedProvider == StreamingProvider.YOUTUBE_MUSIC) primaryColor else textColor.copy(0.14f), cardShape)
                            .clickable { onSelectProvider(StreamingProvider.YOUTUBE_MUSIC) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        YouTubeMusicLogoBadge(42)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("YouTube Music", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Полный каталог песен и ремиксов без подписки Go+", color = textColor.copy(0.65f), fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = selectedProvider == StreamingProvider.YOUTUBE_MUSIC,
                            onClick = { onSelectProvider(StreamingProvider.YOUTUBE_MUSIC) },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(if (selectedProvider == StreamingProvider.SOUNDCLOUD) primaryColor.copy(0.2f) else textColor.copy(0.06f))
                            .border(if (selectedProvider == StreamingProvider.SOUNDCLOUD) 1.5.dp else 1.dp, if (selectedProvider == StreamingProvider.SOUNDCLOUD) primaryColor else textColor.copy(0.14f), cardShape)
                            .clickable { onSelectProvider(StreamingProvider.SOUNDCLOUD) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SoundCloudLogoBadge(42)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SoundCloud", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Плейлисты, андеграунд и скачивание альбомов", color = textColor.copy(0.65f), fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = selectedProvider == StreamingProvider.SOUNDCLOUD,
                            onClick = { onSelectProvider(StreamingProvider.SOUNDCLOUD) },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    TextButton(onClick = { step -= 1 }) {
                        Text("Назад", color = textColor.copy(0.75f), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Button(
                    onClick = {
                        if (step < 2) step += 1 else onFinishOnboarding()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(if (step < 2) "Далее" else "Начать слушать", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun TrackCoverThumb(track: Track, shape: Shape, primaryColor: Color, sizeDp: Int = 48) {
    val context = LocalContext.current
    var bitmap by remember(track.id, track.customCoverUri) { mutableStateOf(CoverCache.get(track.id)?.takeIf { !it.isRecycled }) }
    LaunchedEffect(track.id, track.customCoverUri) {
        bitmap = loadTrackThumbnail(context, track)
    }
    Box(modifier = Modifier.size(sizeDp.dp).clip(shape).background(primaryColor.copy(0.18f)), contentAlignment = Alignment.Center) {
        val validBmp = bitmap?.takeIf { !it.isRecycled }
        if (validBmp != null) Image(validBmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = primaryColor, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

@Composable
fun PlaylistGridCover(tracks: List<Track>, primaryColor: Color, sizeDp: Int = 54) {
    val context = LocalContext.current
    val previewTracks = remember(tracks) { tracks.take(4) }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null, null, null) }
    LaunchedEffect(previewTracks) { previewTracks.forEachIndexed { index, track -> bitmaps[index] = loadTrackThumbnail(context, track) } }

    Box(modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)).background(primaryColor.copy(0.18f))) {
        if (previewTracks.isEmpty()) {
            Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.align(Alignment.Center))
        } else if (previewTracks.size < 4) {
            val b0 = bitmaps[0]?.takeIf { !it.isRecycled }
            if (b0 != null) Image(b0.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) { bitmaps[0]?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                    Box(modifier = Modifier.weight(1f)) { bitmaps[1]?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                }
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) { bitmaps[2]?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                    Box(modifier = Modifier.weight(1f)) { bitmaps[3]?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                }
            }
        }
    }
}

@Composable
fun PlaylistThumb(playlist: PlaylistItem, allTracks: List<Track>, expressiveShape: Shape, primaryColor: Color, sizeDp: Int = 48) {
    val context = LocalContext.current
    var coverBmp by remember(playlist.coverUri) { mutableStateOf<Bitmap?>(null) }
    val resolvedTracks = remember(playlist, allTracks) { resolvePlaylistTracks(playlist, allTracks) }

    LaunchedEffect(playlist.coverUri) {
        if (playlist.coverUri != null) {
            coverBmp = loadBitmapFromUriOrHttp(context, playlist.coverUri, 256)
        }
    }

    Box(modifier = Modifier.size(sizeDp.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
        val valid = coverBmp?.takeIf { !it.isRecycled }
        if (valid != null) {
            Image(valid.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            PlaylistGridCover(resolvedTracks, primaryColor, sizeDp)
        }
    }
}

@Composable
fun HomeScreen(
    allRawTracks: List<Track>,
    deviceTracks: List<Track>,
    trackPlaysAllTime: Map<Long, Int>,
    artistSecondsAllTime: Map<String, Int>,
    dailyStats: Map<String, DailyStatRecord>,
    listenedMinutesTotal: Int,
    firstLaunchDate: String,
    hasPermission: Boolean,
    isBatteryIgnored: Boolean,
    minDurationSec: Int,
    excludedFolders: Set<String>,
    activeStreamingProvider: StreamingProvider,
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
    onStreamingProviderSelected: (StreamingProvider) -> Unit,
    onRestartAppClick: () -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddTrackToPlaylistClick: (Track) -> Unit,
    onStartMyWaveClick: () -> Unit,
    onExportTheme: () -> Unit,
    onImportTheme: () -> Unit
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableIntStateOf(0) }
    var selectedDateFilter by remember { mutableStateOf("ALL") }

    val activeArtistSeconds = run {
        if (selectedDateFilter == "ALL") artistSecondsAllTime
        else dailyStats[selectedDateFilter]?.artistSeconds ?: emptyMap()
    }
    val totalArtistSec = activeArtistSeconds.values.sum()
    val topArtistEntry = activeArtistSeconds.maxByOrNull { it.value }
    val topArtistPercent = if (totalArtistSec > 0 && topArtistEntry != null) ((topArtistEntry.value.toFloat() / totalArtistSec) * 100).toInt().coerceIn(1, 100) else 0
    val topArtistsOver25 = remember(activeArtistSeconds) { getTopArtistsOver25(activeArtistSeconds) }

    val top5TracksWithPlays = run {
        val trackMap = deviceTracks.associateBy { it.id }
        trackPlaysAllTime.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .mapNotNull { entry -> trackMap[entry.key]?.let { it to entry.value } }
            .take(5)
    }

    fun openCalendarPicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDateFilter = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ExpressiveFilterPill("История прослушивания", activeSubTab == 0, primaryColor, textColor) { activeSubTab = 0 }
            IconButton(
                onClick = { activeSubTab = if (activeSubTab == 1) 0 else 1 },
                modifier = Modifier.size(44.dp).clip(CircleShape).background(if (activeSubTab == 1) primaryColor else surfaceColor).border(1.dp, if (activeSubTab == 1) primaryColor else textColor.copy(0.12f), CircleShape)
            ) {
                Icon(Icons.Filled.Settings, "Настройки", tint = if (activeSubTab == 1) Color.Black else textColor)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = activeSubTab, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) }, label = "") { subTab ->
            if (subTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    if (!hasPermission) {
                        item {
                            Button(onClick = onRequestPermission, shape = cardShape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text("Разрешить доступ к музыке", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).clickable { onStartMyWaveClick() },
                            color = primaryColor.copy(alpha = 0.16f),
                            shape = cardShape,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(expressiveShape).background(primaryColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(26.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Моя волна (50/50)", color = textColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        Text("50% твоих треков + 50% ${activeStreamingProvider.title}", color = textColor.copy(0.65f), fontSize = 12.sp)
                                    }
                                }
                                Icon(Icons.Filled.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).padding(16.dp)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.GraphicEq, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("СВЕДЕНИЯ О ПРОСЛУШИВАНИИ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (topArtistEntry != null && totalArtistSec > 0) {
                                            Text(
                                                buildAnnotatedString {
                                                    append("Артист ")
                                                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Black)) { append(topArtistEntry.key) }
                                                    append(" играл $topArtistPercent% твоей музыки!")
                                                },
                                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor, lineHeight = 24.sp
                                            )
                                        } else {
                                            Text("Слушай любимые треки, чтобы увидеть главного артиста!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(modifier = Modifier.size(48.dp).clip(expressiveShape).background(primaryColor.copy(0.22f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Audiotrack, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(primaryColor.copy(0.15f)).border(1.dp, primaryColor.copy(0.35f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Timer, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("$listenedMinutesTotal минут музыки за этот год", color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column {
                            Text("Статистика артистов (> 25%)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ExpressiveFilterPill("За этот год", selectedDateFilter == "ALL", primaryColor, textColor) { selectedDateFilter = "ALL" }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selectedDateFilter != "ALL") primaryColor else textColor.copy(0.08f))
                                        .border(1.dp, if (selectedDateFilter != "ALL") primaryColor else textColor.copy(0.16f), RoundedCornerShape(50))
                                        .clickable { openCalendarPicker() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.CalendarMonth, "Дата", tint = if (selectedDateFilter != "ALL") Color.Black else primaryColor, modifier = Modifier.size(17.dp))
                                        Text(if (selectedDateFilter == "ALL") "Выбрать дату" else selectedDateFilter, color = if (selectedDateFilter != "ALL") Color.Black else textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (topArtistsOver25.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (selectedDateFilter == "ALL") "Главные артисты (>25%)" else "Артисты за $selectedDateFilter (>25%)",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor
                                )
                                topArtistsOver25.forEach { (artist, pct) ->
                                    val sec = activeArtistSeconds[artist] ?: 0
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(artist, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$pct% (${sec / 60}м ${sec % 60}с)", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else if (totalArtistSec > 0) {
                        item {
                            Text("Ни один артист ещё не набрал более 25% от общего времени прослушивания.", color = textColor.copy(0.55f), fontSize = 13.sp)
                        }
                    }

                    item { Text("Топ-5 треков", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor) }
                    if (top5TracksWithPlays.isEmpty()) {
                        item {
                            Text("Здесь появятся треки, которые ты прослушала 2 и более раз.", color = textColor.copy(0.55f), fontSize = 13.sp)
                        }
                    } else {
                        items(top5TracksWithPlays, key = { it.first.id }) { (track, playCount) ->
                            TrackListItem(
                                track = track,
                                cardShape = cardShape,
                                expressiveShape = expressiveShape,
                                primaryColor = primaryColor,
                                textColor = textColor,
                                surfaceColor = surfaceColor,
                                badgeText = "$playCount просл.",
                                showCoverThumb = true,
                                isSelected = false,
                                isSelectionMode = false,
                                onAddToPlaylist = { onAddTrackToPlaylistClick(track) },
                                onLongClick = {},
                                onClick = { onTrackClick(track) }
                            )
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
                    activeStreamingProvider = activeStreamingProvider,
                    cardShape = cardShape,
                    expressiveShape = expressiveShape,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    onMinDurationChange = onMinDurationChange,
                    onToggleFolderExclusion = onToggleFolderExclusion,
                    onStreamingProviderSelected = onStreamingProviderSelected,
                    onRestartAppClick = onRestartAppClick,
                    onThemeChange = onThemeChange,
                    onRequestBatteryIgnore = onRequestBatteryIgnore,
                    onExport = onExportTheme,
                    onImport = onImportTheme
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}

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
                    placeholder = { Text("Поиск песен в YouTube Music...", color = textColor.copy(0.5f), fontSize = 13.sp) },
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
                    Text("Введи название трека или артиста (без ограничений подписки)", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
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
                                Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
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
                    Text("Нажми ❤️ рядом с треком в YouTube Music, чтобы сохранить его сюда", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(likedTracks, key = { it.id }) { savedTrack ->
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
                        Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
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
                    placeholder = { Text(if (searchType == 0) "Поиск треков SoundCloud..." else "Поиск плейлистов...", color = textColor.copy(0.5f), fontSize = 13.sp) },
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
                                    Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isLiked) primaryColor else textColor.copy(0.6f), modifier = Modifier.size(20.dp))
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
                    Text("Нажми ❤️ рядом с треком в SoundCloud, чтобы сохранить его сюда", color = textColor.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(likedScTracks, key = { it.id }) { savedTrack ->
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
    onAddTrackToPlaylistClick: (Track) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var heroBitmap by remember(title, customCoverUri) { mutableStateOf<Bitmap?>(null) }
    val isContextPlaying = isPlaying && currentTrack != null && tracks.any { it.id == currentTrack.id }

    LaunchedEffect(tracks, customCoverUri) {
        if (customCoverUri != null) {
            heroBitmap = loadBitmapFromUriOrHttp(context, customCoverUri, 512)
        } else {
            for (trk in tracks) { val bmp = loadTrackThumbnail(context, trk); if (bmp != null) { heroBitmap = bmp; break } }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(CircleShape).background(surfaceColor)) {
                        Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                    if (onEditPlaylistClick != null) {
                        IconButton(onClick = onEditPlaylistClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(surfaceColor)) {
                            Icon(Icons.Filled.Edit, "Изменить", tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.size(210.dp).clip(expressiveShape).background(surfaceColor).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    val validHero = heroBitmap?.takeIf { !it.isRecycled }
                    if (validHero != null) Image(validHero.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else if (customCoverUri == null && tracks.isNotEmpty()) PlaylistGridCover(tracks, primaryColor, 210)
                    else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(90.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(title, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = textColor.copy(0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onToggleShuffle()
                            if (!isContextPlaying && tracks.isNotEmpty()) onPlayContext(tracks.shuffled(), tracks.random())
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isShuffleEnabled) primaryColor.copy(0.22f) else surfaceColor)
                    ) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (isShuffleEnabled) primaryColor else textColor.copy(0.75f), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(primaryColor).clickable {
                            if (isContextPlaying) onTogglePlay()
                            else if (tracks.isNotEmpty()) onPlayContext(tracks, tracks.first())
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isContextPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("В этом списке пока нет треков.", color = textColor.copy(0.6f)) } }
        } else {
            items(tracks, key = { it.id }) { track ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TrackListItem(track, RoundedCornerShape(16.dp), expressiveShape, primaryColor, textColor, surfaceColor, null, true, false, false, { onAddTrackToPlaylistClick(track) }, {}) { onPlayContext(tracks, track) }
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
                Box(modifier = Modifier.fillMaxWidth().height(410.dp)) {
                    val validHero = heroBitmap?.takeIf { !it.isRecycled }
                    if (validHero != null) {
                        Image(bitmap = validHero.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(28.dp), alpha = 0.7f)
                        Image(bitmap = validHero.asImageBitmap(), contentDescription = artistName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(surfaceColor), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, null, tint = textColor.copy(0.3f), modifier = Modifier.size(110.dp))
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(colors = listOf(Color.Black.copy(0.25f), Color.Transparent, bgColor.copy(0.88f), bgColor))
                        )
                    )

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(0.45f))) {
                            Icon(Icons.Filled.ArrowBackIosNew, "Назад", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(artistName, color = textColor, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onToggleShuffle(); if (artistTracks.isNotEmpty()) onPlayContext(artistTracks.shuffled(), artistTracks.random()) }) {
                                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (isShuffleEnabled) primaryColor else textColor, modifier = Modifier.size(28.dp))
                            }
                            Box(
                                modifier = Modifier.size(58.dp).clip(CircleShape).background(primaryColor).clickable {
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
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Песни", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
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

            items(filteredTracks, key = { it.id }) { track ->
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
    onSetOpenedPlaylist: (PlaylistItem?) -> Unit,
    onSetOpenedArtist: (String?) -> Unit,
    onSetOpenedAlbum: (String?) -> Unit,
    onEditPlaylist: (PlaylistItem) -> Unit,
    onCreateNewPlaylistClick: () -> Unit,
    onRescanClick: () -> Unit
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
        AppleMusicArtistScreen(openedArtist, artistTracks, cardShape, expressiveShape, primaryColor, textColor, surfaceColor, bgColor, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedArtist(null) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick, onAddMultipleTracksToPlaylist)
        return
    }

    if (openedPlaylist != null) {
        val currentPl = playlists.find { it.name == openedPlaylist.name } ?: openedPlaylist
        val plTracks = remember(currentPl, tracks) { resolvePlaylistTracks(currentPl, tracks) }
        CleanHeaderLayout(currentPl.name, if (currentPl.description.isNotBlank()) currentPl.description else "Плейлист • ${plTracks.size} треков", plTracks, currentPl.coverUri, primaryColor, textColor, surfaceColor, bgColor, expressiveShape, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedPlaylist(null) }, { onEditPlaylist(currentPl) }, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick)
        return
    }

    if (openedAlbum != null) {
        val albumTracks = run { tracks.filter { it.album == openedAlbum } }
        val artistName = albumTracks.firstOrNull()?.artist ?: "Неизвестный исполнитель"
        CleanHeaderLayout(openedAlbum, "Альбом • $artistName", albumTracks, null, primaryColor, textColor, surfaceColor, bgColor, expressiveShape, currentTrack, isPlaying, isShuffleEnabled, { onSetOpenedAlbum(null) }, null, onPlayContext, onTogglePlay, onToggleShuffle, onAddTrackToPlaylistClick)
        return
    }

    val sortedTracks = remember(tracks, sortMode, searchQuery) {
        val filtered = if (searchQuery.isBlank()) tracks else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            TrackSortMode.DATE_ADDED -> filtered
            TrackSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            TrackSortMode.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            TrackSortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
    }

    val filteredArtists = remember(tracks, searchQuery) {
        val map = mutableMapOf<String, MutableList<Track>>()
        for (trk in tracks) {
            for (art in splitArtists(trk.artist)) {
                map.getOrPut(art) { mutableListOf() }.add(trk)
            }
        }
        if (searchQuery.isBlank()) map else map.filter { it.key.contains(searchQuery, ignoreCase = true) }
    }

    val filteredAlbums = remember(tracks, searchQuery) {
        val allAlb = tracks.groupBy { it.album }
        if (searchQuery.isBlank()) allAlb else allAlb.filter { it.key.contains(searchQuery, ignoreCase = true) }
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(
                    onClick = { category = 0 },
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (category == 0) primaryColor else surfaceColor)
                ) {
                    Icon(Icons.Filled.MusicNote, "Треки", tint = if (category == 0) Color.Black else textColor)
                }
                IconButton(
                    onClick = { category = 1 },
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (category == 1) primaryColor else surfaceColor)
                ) {
                    Icon(Icons.Filled.QueueMusic, "Плейлисты", tint = if (category == 1) Color.Black else textColor)
                }
                IconButton(
                    onClick = { category = 2 },
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (category == 2) primaryColor else surfaceColor)
                ) {
                    Icon(Icons.Filled.Person, "Авторы", tint = if (category == 2) Color.Black else textColor)
                }
                IconButton(
                    onClick = { category = 3 },
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (category == 3) primaryColor else surfaceColor)
                ) {
                    Icon(Icons.Filled.Album, "Альбомы", tint = if (category == 3) Color.Black else textColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = category, label = "") { cat ->
                when (cat) {
                    0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(sortedTracks, key = { it.id }) { track ->
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
                    1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(primaryColor.copy(0.18f))
                                    .border(1.5.dp, primaryColor, cardShape)
                                    .clickable { onCreateNewPlaylistClick() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(48.dp).clip(expressiveShape).background(primaryColor), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, "Создать", tint = Color.Black)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Создать плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Добавить свою обложку и описание", color = textColor.copy(0.65f), fontSize = 12.sp)
                                }
                            }
                        }

                        if (filteredPlaylists.isEmpty() && searchQuery.isNotBlank()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("Ничего не найдено", color = textColor.copy(0.6f))
                                }
                            }
                        }

                        items(filteredPlaylists) { pl ->
                            val plCount = resolvePlaylistTracks(pl, tracks).size
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedPlaylist(pl) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlaylistThumb(pl, tracks, expressiveShape, primaryColor, 54)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Треков: $plCount", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                                IconButton(onClick = { onEditPlaylist(pl) }) {
                                    Icon(Icons.Filled.Edit, "Изменить", tint = primaryColor, modifier = Modifier.size(20.dp))
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(0.5f))
                            }
                        }
                    }
                    2 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredArtists.keys.toList()) { artist ->
                            val artistTrackList = filteredArtists[artist] ?: emptyList()
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedArtist(artist) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (artistTrackList.isNotEmpty()) { TrackCoverThumb(artistTrackList.first(), CircleShape, primaryColor, 48); Spacer(modifier = Modifier.width(14.dp)) }
                                Column(modifier = Modifier.weight(1f)) { Text(artist, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${artistTrackList.size} треков", color = textColor.copy(0.6f), fontSize = 12.sp) }
                                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
                            }
                        }
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredAlbums.keys.toList()) { album ->
                            val albumTrackList = filteredAlbums[album] ?: emptyList()
                            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onSetOpenedAlbum(album) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (albumTrackList.isNotEmpty()) { TrackCoverThumb(albumTrackList.first(), expressiveShape, primaryColor, 50); Spacer(modifier = Modifier.width(14.dp)) }
                                Column(modifier = Modifier.weight(1f)) { Text(album, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${albumTrackList.firstOrNull()?.artist ?: ""} • ${albumTrackList.size} тр.", color = textColor.copy(0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor.copy(0.96f))
            .border(1.dp, textColor.copy(0.12f), cardShape)
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
        Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor.copy(0.22f)), contentAlignment = Alignment.Center) {
            val validArt = metadata.coverArt?.takeIf { !it.isRecycled }
            if (validArt != null) Image(validArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerOverlay(
    track: Track,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    sleepTimerRemainingSec: Int,
    stopAfterCurrentTrack: Boolean,
    isSearchingOnlineLyrics: Boolean,
    loopPointAMs: Long?,
    loopPointBMs: Long?,
    onSetLoopPointA: (Long?) -> Unit,
    onSetLoopPointB: (Long?) -> Unit,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onOpenSleepTimerClick: () -> Unit,
    onOpenAudioFxClick: () -> Unit = {},
    onDownloadCurrentScTrack: () -> Unit = {},
    onSearchOnlineLyricsClick: (String?) -> Unit,
    onOpenQueueClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVol by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    var showFullLyricsOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAbLooperBar by remember { mutableStateOf(loopPointAMs != null || loopPointBMs != null) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(850L)
            doubleTapFeedback = null
        }
    }

    BackHandler {
        if (showFullLyricsOverlay) showFullLyricsOverlay = false else onDismiss()
    }

    val validArt = metadata.coverArt?.takeIf { !it.isRecycled }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable(enabled = true, onClick = {})) {
        if (validArt != null) {
            Image(validArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(75.dp), alpha = 0.38f)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(primaryColor.copy(0.28f), secondaryColor.copy(0.18f), bgColor))))
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(textColor.copy(0.35f)))
                }
                if (sleepTimerRemainingSec > 0 || stopAfterCurrentTrack) {
                    val timerText = if (stopAfterCurrentTrack) "Таймер: до конца трека" else "Таймер сна: ${sleepTimerRemainingSec / 60}м ${sleepTimerRemainingSec % 60}с"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(primaryColor.copy(0.2f))
                            .clickable { onOpenSleepTimerClick() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(timerText, color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val targetScale = if (isPlaying) 1f else 0.94f
            val coverScale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(250), label = "coverScale")
            var coverWidthPx by remember { mutableFloatStateOf(1f) }
            var swipeDownY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding((1f - coverScale).dp * 80)
                    .clip(expressiveShape)
                    .background(textColor.copy(0.08f))
                    .border(1.dp, textColor.copy(0.18f), expressiveShape)
                    .onGloballyPositioned { coverWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(track.id) {
                        detectDragGestures(
                            onDragStart = { swipeDownY = 0f },
                            onDrag = { change, drag ->
                                change.consume()
                                swipeDownY += drag.y
                            },
                            onDragEnd = {
                                if (swipeDownY > 110f) onDismiss()
                            }
                        )
                    }
                    .pointerInput(track.id, currentPosMs) {
                        detectTapGestures(
                            onTap = { showFullLyricsOverlay = true },
                            onDoubleTap = { offset ->
                                val relX = offset.x / coverWidthPx
                                when {
                                    relX < 0.35f -> {
                                        onSeek((currentPosMs - 10000L).coerceAtLeast(0L))
                                        doubleTapFeedback = "-10 сек"
                                    }
                                    relX > 0.65f -> {
                                        onSeek((currentPosMs + 10000L).coerceAtMost(track.durationMs.coerceAtLeast(1L)))
                                        doubleTapFeedback = "+10 сек"
                                    }
                                    else -> {
                                        onPlayPause()
                                        doubleTapFeedback = if (isPlaying) "Пауза" else "Играет"
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (validArt != null) Image(validArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(105.dp))

                androidx.compose.animation.AnimatedVisibility(
                    visible = doubleTapFeedback != null,
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(0.68f))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(doubleTapFeedback ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
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
                    Spacer(modifier = Modifier.width(10.dp))
                    val isOnlineStream = track.folderName == "SoundCloud" || track.folderName == "YouTube Music" || track.uri.scheme == "http" || track.uri.scheme == "https"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(0.12f))
                                .clickable { onAddToPlaylistClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, "В плейлист", tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(textColor.copy(0.12f))
                                    .clickable { showMoreMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MoreVert, "Ещё", tint = textColor, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(surfaceColor)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Повтор фрагмента (A-B Лупер)", color = textColor, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Filled.Loop, null, tint = primaryColor) },
                                    onClick = {
                                        showMoreMenu = false
                                        showAbLooperBar = !showAbLooperBar
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Поделиться карточкой трека", color = textColor, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Filled.Share, null, tint = primaryColor) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            shareTrackCardImage(context, track, metadata, primaryColor.toArgb())
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Настройка звука (Speed / Pitch / FX)", color = textColor, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Filled.GraphicEq, null, tint = primaryColor) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenAudioFxClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Таймер сна (до 120 мин)", color = textColor, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Filled.Bedtime, null, tint = primaryColor) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenSleepTimerClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Синхронный текст", color = textColor, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Filled.CloudDownload, null, tint = primaryColor) },
                                    onClick = {
                                        showMoreMenu = false
                                        onSearchOnlineLyricsClick(null)
                                        showFullLyricsOverlay = true
                                    }
                                )
                                if (isOnlineStream) {
                                    DropdownMenuItem(
                                        text = { Text("Скачать трек в память", color = textColor, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Filled.Download, null, tint = primaryColor) },
                                        onClick = {
                                            showMoreMenu = false
                                            onDownloadCurrentScTrack()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Изменить сведения о треке", color = textColor, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = primaryColor) },
                                        onClick = {
                                            showMoreMenu = false
                                            onEditTagsClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showAbLooperBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(primaryColor.copy(0.16f))
                            .border(1.dp, primaryColor.copy(0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onSetLoopPointA(currentPosMs) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (loopPointAMs != null) "A: ${formatMillis(loopPointAMs)}" else "Точка A",
                                color = if (loopPointAMs != null) primaryColor else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = { onSetLoopPointB(currentPosMs) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (loopPointBMs != null) "B: ${formatMillis(loopPointBMs)}" else "Точка B",
                                color = if (loopPointBMs != null) primaryColor else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                onSetLoopPointA(null)
                                onSetLoopPointB(null)
                                showAbLooperBar = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Сброс цикла", tint = textColor.copy(0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val safeDur = track.durationMs.coerceAtLeast(1L)
                WavySlider(
                    value = (currentPosMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                    onSeekCommit = { frac -> onSeek((frac * safeDur).toLong()) },
                    isPlaying = isPlaying,
                    waveColor = primaryColor,
                    trackColor = textColor.copy(alpha = 0.22f),
                    thumbColor = textColor,
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(if (isShuffleEnabled) primaryColor.copy(0.22f) else Color.Transparent)
                ) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (isShuffleEnabled) primaryColor else textColor.copy(0.55f), modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.FastRewind, null, tint = textColor, modifier = Modifier.size(40.dp))
                }

                Box(
                    modifier = Modifier.size(76.dp).clip(expressiveShape).background(primaryColor).clickable {
                        onPlayPause()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }

                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = textColor, modifier = Modifier.size(40.dp))
                }

                val repeatAccent = when (repeatMode) {
                    RepeatMode.OFF -> textColor.copy(0.55f)
                    RepeatMode.ALL -> primaryColor
                    RepeatMode.ONE -> Color(0xFF00F5D4)
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
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
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onOpenQueueClick,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(textColor.copy(0.1f))
                ) {
                    Icon(Icons.Filled.PlaylistPlay, "Очередь", tint = primaryColor, modifier = Modifier.size(24.dp))
                }

                Icon(Icons.Filled.VolumeDown, null, tint = textColor.copy(0.6f), modifier = Modifier.size(18.dp))
                Slider(
                    value = currentVol,
                    onValueChange = { currentVol = it; audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) },
                    valueRange = 0f..maxVol.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = textColor, activeTrackColor = textColor.copy(0.85f), inactiveTrackColor = textColor.copy(0.22f)),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.VolumeUp, null, tint = textColor.copy(0.6f), modifier = Modifier.size(18.dp))

                IconButton(
                    onClick = { showFullLyricsOverlay = true },
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(if (showFullLyricsOverlay) primaryColor else textColor.copy(0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Текст песни",
                        tint = if (showFullLyricsOverlay) Color.Black else primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showFullLyricsOverlay,
            enter = fadeIn(tween(280)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250))
        ) {
            FullScreenLyricsOverlayWithMiniPlayer(
                track = track,
                metadata = metadata,
                isPlaying = isPlaying,
                currentPosMs = currentPosMs,
                isSearchingOnlineLyrics = isSearchingOnlineLyrics,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                bgColor = bgColor,
                onClose = { showFullLyricsOverlay = false },
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrev = onPrev,
                onSeek = onSeek,
                onSearchLyricsByQuery = { query -> onSearchOnlineLyricsClick(query) }
            )
        }
    }
}

@Composable
fun FullScreenLyricsOverlayWithMiniPlayer(
    track: Track,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    isSearchingOnlineLyrics: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onSearchLyricsByQuery: (String?) -> Unit
) {
    val lyricsList = metadata.lyrics
    val syncedPosMs = currentPosMs + 220L
    val activeLyricIndex = remember(syncedPosMs, lyricsList) {
        if (lyricsList.isEmpty()) -1
        else lyricsList.indexOfLast { it.timeMs <= syncedPosMs }.coerceAtLeast(0)
    }
    val lyricListState = rememberLazyListState()
    var showManualSearchField by remember { mutableStateOf(false) }
    var manualQuery by remember(track.id) { mutableStateOf("${track.artist} ${track.title}") }

    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && lyricsList.isNotEmpty()) {
            try {
                val targetIdx = (activeLyricIndex - 1).coerceAtLeast(0)
                lyricListState.animateScrollToItem(index = targetIdx, scrollOffset = 0)
            } catch (_: Throwable) {}
        }
    }

    val validArt = metadata.coverArt?.takeIf { !it.isRecycled }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable(enabled = true, onClick = {})) {
        if (validArt != null) {
            Image(
                bitmap = validArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(65.dp),
                alpha = 0.42f
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(primaryColor.copy(0.32f), secondaryColor.copy(0.2f), bgColor))))
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Синхронный текст", color = Color.White.copy(0.9f), fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f))
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Закрыть", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            AnimatedVisibility(visible = showManualSearchField) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = { manualQuery = it },
                        placeholder = { Text("Артист и название трека...") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSearchLyricsByQuery(manualQuery) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Найти", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (isSearchingOnlineLyrics) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Загружаем синхронный текст...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (lyricsList.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Filled.QueueMusic, null, tint = primaryColor, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Текст для «${track.title}» пока не загружен", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onSearchLyricsByQuery(null) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Загрузить синхронный текст", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(
                        state = lyricListState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 18.dp, bottom = 260.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyricsList, key = { idx, item -> "${idx}_${item.timeMs}" }) { idx, line ->
                            val isCurrent = idx == activeLyricIndex
                            val distance = abs(idx - activeLyricIndex)
                            val ctx = LocalContext.current
                            val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
                            val blurLyricsEnabled = remember { prefs.getBoolean("anim_lyrics_blur", false) }

                            val rawAlpha = when {
                                isCurrent -> 1f
                                idx == activeLyricIndex - 1 -> 0.6f
                                else -> (0.45f - distance * 0.08f).coerceIn(0.2f, 0.45f)
                            }

                            val blurRadius = if (blurLyricsEnabled && !isCurrent) {
                                (distance * 1.0f).coerceAtMost(3.0f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeek(line.timeMs) }
                                    .let { mod ->
                                        if (blurRadius > 0.2f) mod.blur(blurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                        else mod
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp)
                            ) {
                                if (isCurrent && line.words.isNotEmpty()) {
                                    val wordAnnotated = buildAnnotatedString {
                                        for (w in line.words) {
                                            val isWordActive = syncedPosMs >= w.timeMs
                                            withStyle(
                                                SpanStyle(
                                                    color = if (isWordActive) primaryColor else Color.White.copy(alpha = 0.5f),
                                                    fontWeight = if (isWordActive) FontWeight.Black else FontWeight.Bold
                                                )
                                            ) {
                                                append(w.text)
                                            }
                                        }
                                    }
                                    Text(
                                        text = wordAnnotated,
                                        fontSize = 20.sp,
                                        lineHeight = 28.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = line.text,
                                        color = Color.White.copy(alpha = rawAlpha),
                                        fontSize = 20.sp,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                        lineHeight = 28.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val safeDur = track.durationMs.coerceAtLeast(1L)
            val progressFraction = (currentPosMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1A1A26).copy(0.92f))
                    .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(primaryColor.copy(0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (validArt != null) {
                                Image(validArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} • ${formatMillis(currentPosMs)}", color = Color.White.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.FastRewind, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(primaryColor)
                        ) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = primaryColor,
                    trackColor = Color.White.copy(0.18f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
fun CurvedSleepTimerDialog(
    currentMinutes: Int,
    stopAfterCurrentTrack: Boolean,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onApplyTimer: (Int, Boolean) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes.coerceIn(0, 120)) }
    var stopEndTrack by remember { mutableStateOf(stopAfterCurrentTrack) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = { Text("Таймер сна", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Проведи по дуге, чтобы выбрать время (максимум 120 минут)", color = textColor.copy(0.65f), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = change.position.x - cx
                                    val dy = change.position.y - cy
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0f) angle += 360f
                                    val rel = (angle - 135f + 360f) % 360f
                                    val clamped = if (rel > 315f) 0f else rel.coerceIn(0f, 270f)
                                    selectedMinutes = ((clamped / 270f) * 120f).roundToInt().coerceIn(0, 120)
                                }
                            }
                    ) {
                        val strokePx = 18.dp.toPx()
                        val arcDiameter = size.minDimension - strokePx * 2
                        val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
                        val sweep = (selectedMinutes / 120f) * 270f

                        drawArc(
                            color = textColor.copy(alpha = 0.14f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcDiameter, arcDiameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )

                        drawArc(
                            color = primaryColor,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcDiameter, arcDiameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )

                        val thumbRad = Math.toRadians((135f + sweep).toDouble())
                        val radius = arcDiameter / 2f
                        val thumbX = size.width / 2f + (radius * cos(thumbRad)).toFloat()
                        val thumbY = size.height / 2f + (radius * sin(thumbRad)).toFloat()

                        drawCircle(color = Color.White, radius = 12.dp.toPx(), center = Offset(thumbX, thumbY))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Bedtime, null, tint = primaryColor, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (selectedMinutes == 0) "Выкл" else "$selectedMinutes мин", color = textColor, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0, 15, 30, 60, 120).forEach { preset ->
                        ExpressiveFilterPill(if (preset == 0) "Выкл" else "${preset}м", selectedMinutes == preset, primaryColor, textColor) { selectedMinutes = preset }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(textColor.copy(0.06f)).clickable { stopEndTrack = !stopEndTrack }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Остановить после конца трека", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = stopEndTrack, onCheckedChange = { stopEndTrack = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApplyTimer(selectedMinutes, stopEndTrack) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                Text("Запустить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) } }
    )
}

@Composable
fun SettingsCategoryItem(icon: ImageVector, title: String, subtitle: String, textColor: Color, primaryColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    activeStreamingProvider: StreamingProvider,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onMinDurationChange: (Int) -> Unit,
    onToggleFolderExclusion: (String) -> Unit,
    onStreamingProviderSelected: (StreamingProvider) -> Unit,
    onRestartAppClick: () -> Unit,
    onThemeChange: (MMPThemeConfig) -> Unit,
    onRequestBatteryIgnore: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var activeSettingsMenu by remember { mutableStateOf("MAIN") }
    var pendingRestartNeeded by remember { mutableStateOf(false) }
    var selectedStreamState by remember(activeStreamingProvider) { mutableStateOf(activeStreamingProvider) }

    val presets = listOf("Neon Pink" to 0xFFFF66C4, "Acid Lime" to 0xFFA6FF00, "Electric Violet" to 0xFF9D4EDD, "Cyber Cyan" to 0xFF00F5D4, "Sunset Coral" to 0xFFFF5E5B, "Pure Gold" to 0xFFFFBE0B)

    var showCustomColorPicker by remember { mutableStateOf(false) }
    var customRed by remember { mutableFloatStateOf(((themeConfig.primaryHex shr 16) and 0xFF).toFloat()) }
    var customGreen by remember { mutableFloatStateOf(((themeConfig.primaryHex shr 8) and 0xFF).toFloat()) }
    var customBlue by remember { mutableFloatStateOf((themeConfig.primaryHex and 0xFF).toFloat()) }

    val foldersMap = remember(allRawTracks) { allRawTracks.groupBy { it.folderPath } }

    AnimatedContent(targetState = activeSettingsMenu, label = "") { menu ->
        when (menu) {
            "MAIN" -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Настройки", color = textColor, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                "v1.2.0",
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } }
                    item {
                        SettingsCategoryItem(Icons.Filled.CloudQueue, "Стриминг из интернета", "Активно: ${selectedStreamState.title} (YouTube Music / SoundCloud)", textColor, primaryColor) {
                            activeSettingsMenu = "STREAMING"
                        }
                    }
                    item {
                        SettingsCategoryItem(Icons.Filled.ColorLens, "Интерфейс и Темы", "Цвета, формы MD3 Expressive, светлая/тёмная/Adaptive тема", textColor, primaryColor) {
                            activeSettingsMenu = "UI"
                        }
                    }
                    item {
                        SettingsCategoryItem(Icons.Filled.GraphicEq, "Звук", "5-полосный эквалайзер, пресеты, усиление баса и Crossfade", textColor, primaryColor) {
                            activeSettingsMenu = "SOUND"
                        }
                    }
                    item {
                        SettingsCategoryItem(Icons.Filled.Animation, "Анимации", "Плавность интерфейса, волна плеера и оптимизация текста", textColor, primaryColor) {
                            activeSettingsMenu = "ANIMATIONS"
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
            "STREAMING" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Стриминг из интернета", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Text(
                            "Выбери активную систему стриминга. Одновременно может работать только один сервис.",
                            color = textColor.copy(0.65f),
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(surfaceColor)
                                .border(if (selectedStreamState == StreamingProvider.YOUTUBE_MUSIC) 1.5.dp else 1.dp, if (selectedStreamState == StreamingProvider.YOUTUBE_MUSIC) primaryColor else textColor.copy(0.12f), cardShape)
                                .clickable {
                                    selectedStreamState = StreamingProvider.YOUTUBE_MUSIC
                                    onStreamingProviderSelected(StreamingProvider.YOUTUBE_MUSIC)
                                    pendingRestartNeeded = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                YouTubeMusicLogoBadge(46)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("YouTube Music", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Без ограничений подписки Go+", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = selectedStreamState == StreamingProvider.YOUTUBE_MUSIC,
                                onCheckedChange = { checked ->
                                    val next = if (checked) StreamingProvider.YOUTUBE_MUSIC else StreamingProvider.SOUNDCLOUD
                                    selectedStreamState = next
                                    onStreamingProviderSelected(next)
                                    pendingRestartNeeded = true
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(surfaceColor)
                                .border(if (selectedStreamState == StreamingProvider.SOUNDCLOUD) 1.5.dp else 1.dp, if (selectedStreamState == StreamingProvider.SOUNDCLOUD) primaryColor else textColor.copy(0.12f), cardShape)
                                .clickable {
                                    selectedStreamState = StreamingProvider.SOUNDCLOUD
                                    onStreamingProviderSelected(StreamingProvider.SOUNDCLOUD)
                                    pendingRestartNeeded = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                SoundCloudLogoBadge(46)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("SoundCloud", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Поиск треков и скачивание плейлистов", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = selectedStreamState == StreamingProvider.SOUNDCLOUD,
                                onCheckedChange = { checked ->
                                    val next = if (checked) StreamingProvider.SOUNDCLOUD else StreamingProvider.YOUTUBE_MUSIC
                                    selectedStreamState = next
                                    onStreamingProviderSelected(next)
                                    pendingRestartNeeded = true
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                            )
                        }
                    }
                    if (pendingRestartNeeded) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(primaryColor.copy(0.18f))
                                    .border(1.dp, primaryColor, cardShape)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Чтобы изменить систему стриминга, нужно перезапустить приложение.",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = onRestartAppClick,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Refresh, null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Перезапустить сейчас", color = Color.Black, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
            "LIBRARY" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Медиатека и Папки", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp)) {
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
                        Text("Отключи папки, которые не нужно показывать в плеере:", color = textColor.copy(0.6f), fontSize = 12.sp)
                    }
                    items(foldersMap.keys.toList()) { folderPath ->
                        val list = foldersMap[folderPath] ?: emptyList()
                        val folderName = list.firstOrNull()?.folderName ?: folderPath
                        val isEnabled = !excludedFolders.contains(folderPath)
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).clickable { onToggleFolderExclusion(folderPath) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folderName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$folderPath • ${list.size} аудио", color = textColor.copy(0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(checked = isEnabled, onCheckedChange = { onToggleFolderExclusion(folderPath) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
            }
            "UI" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Интерфейс и Темы", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Text("Режим оформления", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ExpressiveFilterPill("Тёмная", themeConfig.themeMode == ThemeMode.DARK, primaryColor, textColor) {
                                        onThemeChange(themeConfig.copy(themeMode = ThemeMode.DARK, bgHex = 0xFF0D0D12))
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ExpressiveFilterPill("Светлая", themeConfig.themeMode == ThemeMode.LIGHT, primaryColor, textColor) {
                                        onThemeChange(themeConfig.copy(themeMode = ThemeMode.LIGHT, bgHex = 0xFFF7F5FA))
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ExpressiveFilterPill("Adaptive", themeConfig.themeMode == ThemeMode.ADAPTIVE, primaryColor, textColor) {
                                        onThemeChange(themeConfig.copy(themeMode = ThemeMode.ADAPTIVE, bgHex = 0xFF0D0D12))
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ExpressiveFilterPill("Adaptive Светлая", themeConfig.themeMode == ThemeMode.ADAPTIVE_LIGHT, primaryColor, textColor) {
                                        onThemeChange(themeConfig.copy(themeMode = ThemeMode.ADAPTIVE_LIGHT, bgHex = 0xFFF7F5FA))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Акцентный цвет", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            presets.forEach { (name, hex) ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(if (themeConfig.primaryHex == hex) 2.5.dp else 0.dp, textColor, CircleShape)
                                        .clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(surfaceColor)
                                    .border(1.dp, textColor.copy(0.2f), CircleShape)
                                    .clickable { showCustomColorPicker = !showCustomColorPicker },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Colorize, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                            }
                        }
                        AnimatedVisibility(visible = showCustomColorPicker) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(cardShape).background(surfaceColor).padding(16.dp)) {
                                val previewColor = Color(customRed.toInt(), customGreen.toInt(), customBlue.toInt())
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Палитра RGB", color = textColor, fontWeight = FontWeight.Bold)
                                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(previewColor))
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
                        Text("Форма обложек и кнопок (MD3 Expressive)", color = textColor.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(ShapeStyle.entries) { style ->
                                val isSelected = themeConfig.shapeStyle == style
                                val previewShape = style.toExpressiveShape()
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(previewShape)
                                        .background(if (isSelected) primaryColor else surfaceColor)
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) textColor else textColor.copy(0.25f), previewShape)
                                        .clickable { onThemeChange(themeConfig.copy(shapeStyle = style)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Black))
                                    }
                                }
                            }
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
            "SOUND" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                val ctx = LocalContext.current
                val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
                var eqEnabled by remember { mutableStateOf(prefs.getBoolean("eq_enabled", false)) }
                var crossfadeSec by remember { mutableFloatStateOf(prefs.getInt("crossfade_sec", 0).toFloat()) }
                var bassBoostLevel by remember { mutableFloatStateOf(prefs.getInt("bass_boost_level", 0).toFloat()) }
                var band60 by remember { mutableFloatStateOf(prefs.getFloat("eq_b0", 0f)) }
                var band230 by remember { mutableFloatStateOf(prefs.getFloat("eq_b1", 0f)) }
                var band910 by remember { mutableFloatStateOf(prefs.getFloat("eq_b2", 0f)) }
                var band3600 by remember { mutableFloatStateOf(prefs.getFloat("eq_b3", 0f)) }
                var band14000 by remember { mutableFloatStateOf(prefs.getFloat("eq_b4", 0f)) }

                fun saveEqBands(b0: Float, b1: Float, b2: Float, b3: Float, b4: Float) {
                    band60 = b0; band230 = b1; band910 = b2; band3600 = b3; band14000 = b4
                    prefs.edit()
                        .putFloat("eq_b0", b0).putFloat("eq_b1", b1).putFloat("eq_b2", b2)
                        .putFloat("eq_b3", b3).putFloat("eq_b4", b4)
                        .putLong("eq_updated_ts", System.currentTimeMillis())
                        .apply()
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Звук и Эквалайзер", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Плавное затухание (Crossfade)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Плавный переход громкости при смене треков", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                                Text(if (crossfadeSec.toInt() == 0) "Выкл" else "${crossfadeSec.toInt()} сек", color = primaryColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            Slider(
                                value = crossfadeSec,
                                onValueChange = {
                                    crossfadeSec = it
                                    prefs.edit().putInt("crossfade_sec", it.toInt()).apply()
                                },
                                valueRange = 0f..12f,
                                steps = 11,
                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                            )
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Системный 5-полосный эквалайзер", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Обработка аудиопотока в реальном времени", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = eqEnabled,
                                    onCheckedChange = {
                                        eqEnabled = it
                                        prefs.edit().putBoolean("eq_enabled", it).putLong("eq_updated_ts", System.currentTimeMillis()).apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { ExpressiveFilterPill("Flat", false, primaryColor, textColor) { saveEqBands(0f, 0f, 0f, 0f, 0f) } }
                                item { ExpressiveFilterPill("Bass Boost", false, primaryColor, textColor) { saveEqBands(8f, 5f, 0f, 1f, 2f) } }
                                item { ExpressiveFilterPill("Hyperpop / Club", false, primaryColor, textColor) { saveEqBands(7f, 3f, -2f, 5f, 8f) } }
                                item { ExpressiveFilterPill("Vocal", false, primaryColor, textColor) { saveEqBands(-2f, 1f, 6f, 5f, 1f) } }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            val bands = listOf(
                                Triple("60 Гц (Суб-бас)", band60) { v: Float -> saveEqBands(v, band230, band910, band3600, band14000) },
                                Triple("230 Гц (Низкие)", band230) { v: Float -> saveEqBands(band60, v, band910, band3600, band14000) },
                                Triple("910 Гц (Средние)", band910) { v: Float -> saveEqBands(band60, band230, v, band3600, band14000) },
                                Triple("3.6 кГц (Верхние СЧ)", band3600) { v: Float -> saveEqBands(band60, band230, band910, v, band14000) },
                                Triple("14 кГц (Воздух / ВЧ)", band14000) { v: Float -> saveEqBands(band60, band230, band910, band3600, v) }
                            )
                            bands.forEach { (label, value, onChange) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = textColor.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${if (value >= 0) "+" else ""}${value.toInt()} дБ", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = value,
                                    onValueChange = onChange,
                                    valueRange = -12f..12f,
                                    enabled = eqEnabled,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Усиление баса (Bass Boost)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${bassBoostLevel.toInt()}%", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Slider(
                                value = bassBoostLevel,
                                onValueChange = {
                                    bassBoostLevel = it
                                    prefs.edit().putInt("bass_boost_level", it.toInt()).putLong("eq_updated_ts", System.currentTimeMillis()).apply()
                                },
                                valueRange = 0f..100f,
                                enabled = eqEnabled,
                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                            )
                        }
                    }
                }
            }
            "ANIMATIONS" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                val ctx = LocalContext.current
                val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
                var waveAnimEnabled by remember { mutableStateOf(prefs.getBoolean("anim_wave_enabled", true)) }
                var blurLyricsEnabled by remember { mutableStateOf(prefs.getBoolean("anim_lyrics_blur", false)) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) { Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp)) }
                            Text("Анимации", color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Волна прогресс-бара (WavySlider)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Живая волна при воспроизведении и прямая линия на паузе", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = waveAnimEnabled,
                                    onCheckedChange = {
                                        waveAnimEnabled = it
                                        prefs.edit().putBoolean("anim_wave_enabled", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Эффект размытия (Blur) неактивных строк", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Шейдер размытия для GPU", color = textColor.copy(0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = blurLyricsEnabled,
                                    onCheckedChange = {
                                        blurLyricsEnabled = it
                                        prefs.edit().putBoolean("anim_lyrics_blur", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditPlaylistDialog(
    playlist: PlaylistItem,
    cardShape: Shape,
    expressiveShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    selectedCoverUri: String?,
    onPickCoverClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(playlist.name) }
    var desc by remember { mutableStateOf(playlist.description) }
    val context = LocalContext.current
    val effectiveCover = selectedCoverUri ?: playlist.coverUri
    val coverBitmap = run {
        if (effectiveCover != null && !effectiveCover.startsWith("http")) {
            try { context.contentResolver.openInputStream(Uri.parse(effectiveCover))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = { Text("Настройки плейлиста", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(100.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor, expressiveShape).clickable { onPickCoverClick() }, contentAlignment = Alignment.Center) {
                    if (coverBitmap != null) Image(coverBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.AddPhotoAlternate, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, shape = cardShape, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, maxLines = 3, shape = cardShape, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), desc.trim(), effectiveCover) }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(50)) {
                Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) } }
    )
}

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
        if (effectiveCoverUri != null && !effectiveCoverUri.startsWith("http")) {
            try { context.contentResolver.openInputStream(Uri.parse(effectiveCoverUri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        } else CoverCache.get(track.id)?.takeIf { !it.isRecycled }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = { Text("Изменить сведения о треке", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp) },
        text = {
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item {
                    Box(modifier = Modifier.size(96.dp).clip(expressiveShape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor, expressiveShape).clickable { onPickCoverClick() }, contentAlignment = Alignment.Center) {
                        if (coverBitmap != null) Image(coverBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = primaryColor); Text("Обложка", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название трека") }, singleLine = true, shape = cardShape, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Исполнитель") }, singleLine = true, shape = cardShape, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Альбом") }, singleLine = true, shape = cardShape, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, label = { Text("Текст песни или .lrc ([00:12.00]Строка)") }, minLines = 3, maxLines = 6, shape = cardShape, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), artist.trim(), album.trim(), effectiveCoverUri, lyrics.ifBlank { null }) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) } }
    )
}

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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).clickable(enabled = false, onClick = {}).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Очередь воспроизведения (${queue.size})", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = textColor) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(queue) { index, trk ->
                    val isCurrent = currentTrack?.id == trk.id
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isCurrent) primaryColor.copy(0.2f) else textColor.copy(0.05f)).clickable { onSelectTrack(trk) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trk.title, color = if (isCurrent) primaryColor else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(trk.artist, color = textColor.copy(0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row {
                            if (index > 0) IconButton(onClick = { onMoveTrack(index, index - 1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = textColor.copy(0.75f)) }
                            if (index < queue.size - 1) IconButton(onClick = { onMoveTrack(index, index + 1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = textColor.copy(0.75f)) }
                            IconButton(onClick = { onRemoveTrack(index) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = textColor.copy(0.6f), modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrostedDockBar(
    selectedTab: Int,
    activeStreamingProvider: StreamingProvider,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onTabSelected: (Int) -> Unit,
    onCreateClick: () -> Unit
) {
    val streamLabel = if (activeStreamingProvider == StreamingProvider.YOUTUBE_MUSIC) "YT Music" else "SoundCloud"
    val streamIcon = if (activeStreamingProvider == StreamingProvider.YOUTUBE_MUSIC) Icons.Filled.PlayCircleOutline else Icons.Filled.CloudQueue

    Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor.copy(0.95f)).border(1.dp, textColor.copy(0.12f), cardShape).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        DockVerticalItem(Icons.Filled.Home, "Главная", selectedTab == 0, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(0) }
        DockVerticalItem(Icons.Filled.LibraryMusic, "Медиатека", selectedTab == 1, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(1) }
        DockVerticalItem(streamIcon, streamLabel, selectedTab == 2, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(2) }
        DockVerticalItem(Icons.Filled.AddCircleOutline, "Создать", false, primaryColor, textColor, Modifier.weight(1f), onCreateClick)
    }
}

@Composable
fun DockVerticalItem(icon: ImageVector, label: String, selected: Boolean, primaryColor: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (selected) primaryColor.copy(0.24f) else Color.Transparent), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) primaryColor else textColor.copy(0.7f), modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = if (selected) primaryColor else textColor.copy(0.75f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExpressiveFilterPill(text: String, selected: Boolean, primaryColor: Color, textColor: Color = Color.White, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) primaryColor else textColor.copy(0.08f)).border(1.dp, if (selected) primaryColor else textColor.copy(0.16f), RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = if (selected) Color.Black else textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onAddToPlaylist: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) primaryColor.copy(0.22f) else surfaceColor)
            .border(if (isSelected) 1.5.dp else 0.dp, primaryColor, cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = primaryColor, checkmarkColor = Color.Black)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (showCoverThumb) {
            TrackCoverThumb(track, expressiveShape, primaryColor, 46)
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(if (badgeText != null) "$badgeText • ${track.artist} • ${formatMillis(track.durationMs)}" else "${track.artist} • ${formatMillis(track.durationMs)}", color = if (badgeText != null) primaryColor else textColor.copy(0.6f), fontWeight = if (badgeText != null) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (!isSelectionMode && onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp).clip(CircleShape)) {
                Icon(Icons.Filled.Add, null, tint = primaryColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun AddToPlaylistSheet(
    tracks: List<Track>,
    allTracks: List<Track>,
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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).clickable(enabled = false, onClick = {}).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text("В какой плейлист добавить?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(if (tracks.size == 1) tracks.first().title else "Выбрано треков: ${tracks.size}", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(primaryColor.copy(0.18f))
                    .border(1.dp, primaryColor, cardShape)
                    .clickable { onCreateNewPlaylistClick() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(42.dp).clip(expressiveShape).background(primaryColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text("Создать новый плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text("Плейлистов пока нет", color = textColor.copy(0.65f), fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 260.dp)) {
                    items(playlists) { pl ->
                        val plCount = resolvePlaylistTracks(pl, allTracks).size
                        Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(textColor.copy(0.06f)).clickable { onSelectPlaylist(pl) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlaylistThumb(pl, allTracks, expressiveShape, primaryColor, 46)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(pl.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Треков: $plCount", color = textColor.copy(0.6f), fontSize = 11.sp)
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
fun CreateMenuSheet(cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, onDismiss: () -> Unit, onSelectPlaylist: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).clickable(enabled = false, onClick = {}).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Что создаём?", color = textColor, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(cardShape).background(primaryColor.copy(0.18f)).border(1.dp, primaryColor, cardShape).clickable { onSelectPlaylist() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(46.dp).clip(expressiveShape).background(primaryColor), contentAlignment = Alignment.Center) { Icon(Icons.Filled.LibraryAdd, null, tint = Color.Black) }
                Spacer(modifier = Modifier.width(16.dp))
                Column { Text("Плейлист", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Выбрать свою обложку, имя и описание", color = textColor.copy(0.65f), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun CreatePlaylistDetailsDialog(cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color, selectedCoverUri: String?, onPickCoverClick: () -> Unit, onDismiss: () -> Unit, onCreate: (String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
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
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Название плейлиста", color = textColor.copy(0.45f)) }, singleLine = true, shape = cardShape)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, placeholder = { Text("Описание (необязательно)", color = textColor.copy(0.45f)) }, maxLines = 3, shape = cardShape)
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, desc, selectedCoverUri) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Создать", color = Color.Black, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = textColor.copy(0.7f)) } }
    )
}

@Composable
fun AudioFxDialog(
    playbackSpeed: Float,
    playbackPitch: Float,
    bassBoostStrength: Int,
    reverbPreset: Int,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onApplyFx: (Float, Float, Int, Int) -> Unit
) {
    var speed by remember { mutableFloatStateOf(playbackSpeed) }
    var pitch by remember { mutableFloatStateOf(playbackPitch) }
    var bass by remember { mutableIntStateOf(bassBoostStrength) }
    var reverb by remember { mutableIntStateOf(reverbPreset) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape,
        containerColor = surfaceColor,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.GraphicEq, null, tint = primaryColor)
                Text("Тюнинг звука (ExoPlayer FX)", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveFilterPill("Обычный", speed == 1f && pitch == 1f, primaryColor, textColor) {
                        speed = 1.0f; pitch = 1.0f; onApplyFx(1.0f, 1.0f, bass, reverb)
                    }
                    ExpressiveFilterPill("Slowed", speed == 0.85f && pitch == 0.85f, primaryColor, textColor) {
                        speed = 0.85f; pitch = 0.85f; onApplyFx(0.85f, 0.85f, bass, reverb)
                    }
                    ExpressiveFilterPill("Nightcore", speed == 1.2f && pitch == 1.2f, primaryColor, textColor) {
                        speed = 1.2f; pitch = 1.2f; onApplyFx(1.2f, 1.2f, bass, reverb)
                    }
                }

                Column {
                    Text("Скорость (Speed): ${String.format("%.2f", speed)}x", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = speed,
                        onValueChange = { speed = it; onApplyFx(speed, pitch, bass, reverb) },
                        valueRange = 0.5f..1.75f,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                    )
                }

                Column {
                    Text("Тон / Питч (Pitch): ${String.format("%.2f", pitch)}x", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it; onApplyFx(speed, pitch, bass, reverb) },
                        valueRange = 0.5f..1.75f,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                    )
                }

                Column {
                    Text("Усиление баса (Bass Boost): ${(bass / 10)}%", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = bass.toFloat(),
                        onValueChange = { bass = it.toInt(); onApplyFx(speed, pitch, bass, reverb) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun WavySlider(
    value: Float,
    onSeekCommit: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color,
    trackColor: Color = waveColor.copy(alpha = 0.25f),
    thumbColor: Color = waveColor
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
    val waveEnabled = prefs.getBoolean("anim_wave_enabled", true)

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, waveEnabled) {
        if (isPlaying && waveEnabled) {
            var lastTime = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                phase = (phase + dt * 3.3f) % (2f * Math.PI.toFloat())
            }
        }
    }

    val amplitudeFraction by animateFloatAsState(
        targetValue = if (isPlaying && waveEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "waveAmp"
    )

    var isInteracting by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastCommitTimeMs by remember { mutableLongStateOf(0L) }
    var sliderWidthPx by remember { mutableFloatStateOf(1f) }

    val now = SystemClock.elapsedRealtime()
    val holdCommit = !isInteracting && (now - lastCommitTimeMs < 600L)
    val displayProgress = when {
        isInteracting || holdCommit -> dragProgress
        else -> value.coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { coordinates ->
                sliderWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(sliderWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isInteracting = true
                    val initialFrac = (down.position.x / sliderWidthPx).coerceIn(0f, 1f)
                    dragProgress = initialFrac
                    down.consume()

                    var currX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChange() != Offset.Zero) {
                            currX = change.position.x
                            val frac = (currX / sliderWidthPx).coerceIn(0f, 1f)
                            dragProgress = frac
                            change.consume()
                        }
                    }
                    val finalFrac = (currX / sliderWidthPx).coerceIn(0f, 1f)
                    dragProgress = finalFrac
                    lastCommitTimeMs = SystemClock.elapsedRealtime()
                    isInteracting = false
                    onSeekCommit(finalFrac)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val width = size.width
            val midY = size.height / 2f
            val thumbX = (width * displayProgress).coerceIn(0f, width)

            if (thumbX < width) {
                drawLine(
                    color = trackColor,
                    start = Offset(thumbX, midY),
                    end = Offset(width, midY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (thumbX > 0f) {
                val maxAmpPx = 4.2.dp.toPx() * amplitudeFraction
                if (maxAmpPx < 0.2f) {
                    drawLine(
                        color = waveColor,
                        start = Offset(0f, midY),
                        end = Offset(thumbX, midY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val wavePath = androidx.compose.ui.graphics.Path()
                    val waveLength = 46.dp.toPx().coerceAtLeast(1f)
                    var x = 0f
                    val step = 3.5f
                    var first = true
                    while (x <= thumbX) {
                        val relativeX = x / waveLength
                        val y = midY + kotlin.math.sin((relativeX * 2.0 * Math.PI + phase.toDouble())).toFloat() * maxAmpPx
                        if (first) {
                            wavePath.moveTo(x, y)
                            first = false
                        } else {
                            wavePath.lineTo(x, y)
                        }
                        x += step
                    }
                    drawPath(
                        path = wavePath,
                        color = waveColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            drawCircle(
                color = thumbColor,
                radius = if (isInteracting) 8.5.dp.toPx() else 6.5.dp.toPx(),
                center = Offset(thumbX, midY)
            )
        }
    }
}
