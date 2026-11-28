package com.example.mmp.ui.player

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
import com.example.mmp.domain.model.RepeatMode
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
        Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(primaryColor.copy(0.22f)), contentAlignment = Alignment.Center) {
            val validArt = metadata.coverArt?.takeIf { !it.isRecycled }
            if (validArt != null) Image(validArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MotionText(text = track?.title ?: "Выбери трек", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    playbackSource: String,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    queueIndex: Int,
    queueSize: Int,
    playbackSpeed: Float,
    playbackPitch: Float,
    onSpeedPitchChange: (Float, Float) -> Unit,
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

    var showFullLyricsOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSpeedPitchPopup by remember { mutableStateOf(false) }
    var showAbLooperBar by remember { mutableStateOf(loopPointAMs != null || loopPointBMs != null) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(850L)
            doubleTapFeedback = null
        }
    }

    BackHandler {
        when {
            showSpeedPitchPopup -> showSpeedPitchPopup = false
            showFullLyricsOverlay -> showFullLyricsOverlay = false
            else -> onDismiss()
        }
    }

    val validArt = metadata.coverArt?.takeIf { !it.isRecycled }
    val latestCurrentPosMs by rememberUpdatedState(currentPosMs)
    val latestTrackDurationMs by rememberUpdatedState(track.durationMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)

    Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable(enabled = true, onClick = {})) {
        ArtworkAtmosphere(
            bitmap = validArt,
            accentColor = primaryColor,
            backgroundColor = bgColor,
            modifier = Modifier.fillMaxSize(),
            overlayAlpha = 0.78f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Свернуть", tint = textColor, modifier = Modifier.size(28.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    MotionText(
                        text = playbackSource.ifBlank { "Моя Медиатека" },
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (queueSize > 0 && queueIndex >= 0) {
                        MotionText(
                            text = "Трек ${queueIndex + 1} из $queueSize",
                            color = textColor.copy(0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (sleepTimerRemainingSec > 0 || stopAfterCurrentTrack) {
                        val timerText = if (stopAfterCurrentTrack) "Таймер: до конца трека" else "Сон: ${sleepTimerRemainingSec / 60}м ${sleepTimerRemainingSec % 60}с"
                        Text(timerText, color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "Меню", tint = textColor, modifier = Modifier.size(24.dp))
                    }
                    val isOnlineStream = track.folderName == "SoundCloud" || track.folderName == "YouTube Music" || track.uri.scheme == "http" || track.uri.scheme == "https"
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Повтор фрагмента", color = textColor, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.Loop, null, tint = primaryColor) },
                            onClick = { showMoreMenu = false; showAbLooperBar = !showAbLooperBar }
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться карточкой трека", color = textColor, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.Share, null, tint = primaryColor) },
                            onClick = {
                                showMoreMenu = false
                                scope.launch { shareTrackCardImage(context, track, metadata, primaryColor.toArgb()) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Таймер сна", color = textColor, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.Bedtime, null, tint = primaryColor) },
                            onClick = { showMoreMenu = false; onOpenSleepTimerClick() }
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
                                onClick = { showMoreMenu = false; onDownloadCurrentScTrack() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Изменить сведения о треке", color = textColor, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = primaryColor) },
                                onClick = { showMoreMenu = false; onEditTagsClick() }
                            )
                        }
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
                    .padding((1f - coverScale).dp * 70)
                    .clip(RoundedCornerShape(24.dp))
                    .background(surfaceColor)
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
                    .pointerInput(track.id) {
                        detectTapGestures(
                            onTap = { showFullLyricsOverlay = true },
                            onDoubleTap = { offset ->
                                val relX = offset.x / coverWidthPx
                                when {
                                    relX < 0.35f -> {
                                        latestOnSeek((latestCurrentPosMs - 10000L).coerceAtLeast(0L))
                                        doubleTapFeedback = "-10 сек"
                                    }
                                    relX > 0.65f -> {
                                        latestOnSeek((latestCurrentPosMs + 10000L).coerceAtMost(latestTrackDurationMs.coerceAtLeast(1L)))
                                        doubleTapFeedback = "+10 сек"
                                    }
                                    else -> {
                                        latestOnPlayPause()
                                        doubleTapFeedback = if (latestIsPlaying) "Пауза" else "Играет"
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = textColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            track.artist,
                            color = textColor.copy(0.62f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onAddToPlaylistClick,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(primaryColor.copy(0.14f))
                    ) {
                        Icon(Icons.Filled.Add, "Добавить в плейлист", tint = primaryColor, modifier = Modifier.size(26.dp))
                    }
                }

                AnimatedVisibility(visible = showAbLooperBar || loopPointAMs != null || loopPointBMs != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(primaryColor.copy(0.16f))
                            .border(1.dp, primaryColor.copy(0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onSetLoopPointA(currentPosMs) }) {
                            Text(
                                if (loopPointAMs != null) "A: ${formatMillis(loopPointAMs)}" else "Точка A",
                                color = if (loopPointAMs != null) primaryColor else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { onSetLoopPointB(currentPosMs) }) {
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
                            Icon(Icons.Filled.Close, "Сброс", tint = textColor.copy(0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val safeDur = track.durationMs.coerceAtLeast(1L)
                WavySlider(
                    trackKey = track.id,
                    durationMs = safeDur,
                    value = (currentPosMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                    onSeekCommit = { frac -> onSeek((frac * safeDur).toLong()) },
                    isPlaying = isPlaying,
                    waveColor = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.25f),
                    thumbColor = primaryColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMillis(currentPosMs), color = textColor.copy(0.55f), fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                    Text(formatMillis(safeDur), color = textColor.copy(0.55f), fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                }
            }

            val playCornerDp by animateDpAsState(targetValue = if (isPlaying) 24.dp else 44.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "playMorph")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (isShuffleEnabled) primaryColor else textColor.copy(0.65f), modifier = Modifier.size(24.dp))
                }

                IconButton(
                    onClick = onPrev,
                    modifier = Modifier.size(62.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                ) {
                    Icon(Icons.Filled.SkipPrevious, "Назад", tint = primaryColor, modifier = Modifier.size(30.dp))
                }

                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(playCornerDp))
                        .background(primaryColor)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Play/Pause",
                        tint = if (primaryColor.luminance() > 0.179f) Color.Black else Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(62.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                ) {
                    Icon(Icons.Filled.SkipNext, "Вперёд", tint = primaryColor, modifier = Modifier.size(30.dp))
                }

                val repeatTint = when (repeatMode) {
                    RepeatMode.OFF -> textColor.copy(0.65f)
                    RepeatMode.ALL -> primaryColor
                    RepeatMode.ONE -> primaryColor
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedText = if (abs(playbackSpeed - 1.0f) < 0.05f && abs(playbackPitch - 1.0f) < 0.05f) "1x" else String.format(Locale.US, "%.1fx", playbackSpeed)
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .widthIn(min = 68.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, textColor.copy(0.28f), RoundedCornerShape(50))
                        .clickable { showSpeedPitchPopup = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(speedText, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .width(64.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(if (showFullLyricsOverlay) primaryColor else surfaceColor)
                            .clickable { showFullLyricsOverlay = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.FormatQuote,
                            "Текст песни",
                            tint = if (showFullLyricsOverlay) Color.White else textColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .width(64.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                            .background(surfaceColor)
                            .clickable { onOpenQueueClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            "Очередь",
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSpeedPitchPopup,
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            SpeedPitchSheet(
                speed = playbackSpeed,
                pitch = playbackPitch,
                cardShape = cardShape,
                primaryColor = primaryColor,
                textColor = textColor,
                surfaceColor = surfaceColor,
                onDismiss = { showSpeedPitchPopup = false },
                onSpeedPitchChange = onSpeedPitchChange
            )
        }

        AnimatedVisibility(
            visible = showFullLyricsOverlay,
            enter = fadeIn(tween(260)) + slideInVertically(
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
                loopPointAMs = loopPointAMs,
                loopPointBMs = loopPointBMs,
                onSetLoopRange = { a, b ->
                    onSetLoopPointA(a)
                    onSetLoopPointB(b)
                },
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                bgColor = bgColor,
                surfaceColor = surfaceColor,
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
fun SpeedPitchSheet(
    speed: Float,
    pitch: Float,
    cardShape: Shape,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onSpeedPitchChange: (Float, Float) -> Unit
) {
    fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f

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
                .border(1.dp, textColor.copy(0.14f), cardShape)
                .clickable(onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Скорость и Питч", color = textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                TextButton(onClick = { onSpeedPitchChange(1.0f, 1.0f) }) {
                    Text("Сбросить", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            }

            // Пресеты (перенесены сюда из троеточия)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExpressiveFilterPill("Обычный", abs(speed - 1.0f) < 0.05f && abs(pitch - 1.0f) < 0.05f, primaryColor, textColor) {
                    onSpeedPitchChange(1.0f, 1.0f)
                }
                ExpressiveFilterPill("Slowed", abs(speed - 0.8f) < 0.05f && abs(pitch - 0.8f) < 0.05f, primaryColor, textColor) {
                    onSpeedPitchChange(0.8f, 0.8f)
                }
                ExpressiveFilterPill("Nightcore", abs(speed - 1.2f) < 0.05f && abs(pitch - 1.2f) < 0.05f, primaryColor, textColor) {
                    onSpeedPitchChange(1.2f, 1.2f)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Скорость", color = textColor.copy(0.8f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%.1fx", speed), color = primaryColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onSpeedPitchChange(round1((speed - 0.1f).coerceIn(0.5f, 3.0f)), pitch) },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                    ) {
                        Icon(Icons.Filled.Remove, "-0.1", tint = primaryColor)
                    }
                    Slider(
                        value = speed.coerceIn(0.5f, 3.0f),
                        onValueChange = { onSpeedPitchChange(round1(it), pitch) },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onSpeedPitchChange(round1((speed + 0.1f).coerceIn(0.5f, 3.0f)), pitch) },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                    ) {
                        Icon(Icons.Filled.Add, "+0.1", tint = primaryColor)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Питч", color = textColor.copy(0.8f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%.1fst", pitch), color = primaryColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onSpeedPitchChange(speed, round1((pitch - 0.1f).coerceIn(0.1f, 5.0f))) },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                    ) {
                        Icon(Icons.Filled.Remove, "-0.1", tint = primaryColor)
                    }
                    Slider(
                        value = pitch.coerceIn(0.1f, 5.0f),
                        onValueChange = { onSpeedPitchChange(speed, round1(it)) },
                        valueRange = 0.1f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onSpeedPitchChange(speed, round1((pitch + 0.1f).coerceIn(0.1f, 5.0f))) },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(primaryColor.copy(0.18f))
                    ) {
                        Icon(Icons.Filled.Add, "+0.1", tint = primaryColor)
                    }
                }
            }
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
    loopPointAMs: Long?,
    loopPointBMs: Long?,
    onSetLoopRange: (Long?, Long?) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    surfaceColor: Color,
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
    var showLyricsMenu by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(true) }
    var isLyricLoopSelectionMode by remember { mutableStateOf(false) }
    var loopStartLineIdx by remember { mutableStateOf<Int?>(null) }
    var loopEndLineIdx by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(track.id) {
        isLyricsExpanded = true
    }

    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && lyricsList.isNotEmpty() && !isLyricLoopSelectionMode) {
            try {
                val targetIdx = (activeLyricIndex - 1).coerceAtLeast(0)
                lyricListState.animateScrollToItem(index = targetIdx, scrollOffset = 0)
            } catch (_: Throwable) {}
        }
    }

    val validArt = metadata.coverArt?.takeIf { !it.isRecycled }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(enabled = true, onClick = {})
    ) {
        val panelWidth by animateDpAsState(
            targetValue = if (isLyricsExpanded) maxWidth else (maxWidth - 32.dp).coerceAtLeast(280.dp),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "lyricsPanelWidth"
        )
        val panelHeight by animateDpAsState(
            targetValue = if (isLyricsExpanded) maxHeight else 250.dp.coerceAtMost(maxHeight - 48.dp),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "lyricsPanelHeight"
        )
        val panelCorner by animateDpAsState(
            targetValue = if (isLyricsExpanded) 0.dp else 28.dp,
            animationSpec = tween(520),
            label = "lyricsPanelCorner"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(panelWidth)
                .height(panelHeight)
                .clip(RoundedCornerShape(panelCorner))
                .background(bgColor)
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        ) {
            if (!isLyricsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isLyricsExpanded = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ТЕКСТ ПЕСНИ", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            MotionText(track.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Close, "Закрыть", tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Text(
                        text = when {
                            isSearchingOnlineLyrics -> "Загружаем текст…"
                            lyricsList.isEmpty() -> "Текст песни пока не загружен"
                            activeLyricIndex >= 0 -> lyricsList[activeLyricIndex].text
                            else -> lyricsList.first().text
                        },
                        color = Color.White,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(track.artist, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("Открывается…", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    ArtworkAtmosphere(
                        bitmap = validArt,
                        accentColor = primaryColor,
                        backgroundColor = bgColor,
                        modifier = Modifier.fillMaxSize(),
                        overlayAlpha = 0.74f
                    )
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f))
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Закрыть", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Text("Текст песни", color = Color.White.copy(0.92f), fontWeight = FontWeight.Black, fontSize = 16.sp)

                Box {
                    IconButton(
                        onClick = { showLyricsMenu = true },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f))
                    ) {
                        Icon(Icons.Filled.MoreVert, "Меню текста", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(
                        expanded = showLyricsMenu,
                        onDismissRequest = { showLyricsMenu = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Лупер по тексту", color = Color.White, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.Loop, null, tint = primaryColor) },
                            onClick = {
                                showLyricsMenu = false
                                isLyricLoopSelectionMode = true
                                loopStartLineIdx = null
                                loopEndLineIdx = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Обновить текст", color = Color.White, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, null, tint = primaryColor) },
                            onClick = {
                                showLyricsMenu = false
                                onSearchLyricsByQuery(null)
                            }
                        )
                    }
                }
            }

            // Баннер выбора строк для A-B лупера по тексту
            AnimatedVisibility(visible = isLyricLoopSelectionMode || (loopPointAMs != null && loopPointBMs != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(primaryColor.copy(0.24f))
                        .border(1.dp, primaryColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hintText = when {
                        loopPointAMs != null && loopPointBMs != null -> "Цикл: ${formatMillis(loopPointAMs)} – ${formatMillis(loopPointBMs)}"
                        loopStartLineIdx == null -> "Нажми на начальную строку фрагмента"
                        else -> "Теперь нажми на конечную строку"
                    }
                    Text(hintText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            isLyricLoopSelectionMode = false
                            loopStartLineIdx = null
                            loopEndLineIdx = null
                            onSetLoopRange(null, null)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Сбросить", color = primaryColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (isSearchingOnlineLyrics) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Загружаем текст...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            Text("Найти текст", color = Color.Black, fontWeight = FontWeight.Bold)
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

                            val isInSelectedLoop = (loopStartLineIdx != null && loopEndLineIdx != null && idx in loopStartLineIdx!!..loopEndLineIdx!!) ||
                                (loopStartLineIdx == idx)

                            val rawAlpha = when {
                                isInSelectedLoop -> 1f
                                isCurrent -> 1f
                                idx == activeLyricIndex - 1 -> 0.6f
                                else -> (0.45f - distance * 0.08f).coerceIn(0.2f, 0.45f)
                            }

                            val blurRadius = if (blurLyricsEnabled && !isCurrent && !isLyricLoopSelectionMode) {
                                (distance * 1.0f).coerceAtMost(3.0f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isInSelectedLoop) primaryColor.copy(0.28f) else Color.Transparent)
                                    .clickable {
                                        if (isLyricLoopSelectionMode) {
                                            val startIdx = loopStartLineIdx
                                            if (startIdx == null) {
                                                loopStartLineIdx = idx
                                                loopEndLineIdx = null
                                            } else {
                                                val fromIdx = minOf(startIdx, idx)
                                                val toIdx = maxOf(startIdx, idx)
                                                loopStartLineIdx = fromIdx
                                                loopEndLineIdx = toIdx
                                                val startTime = lyricsList[fromIdx].timeMs
                                                val endTime = if (toIdx + 1 < lyricsList.size) {
                                                    lyricsList[toIdx + 1].timeMs
                                                } else {
                                                    track.durationMs.coerceAtLeast(startTime + 2000L)
                                                }
                                                onSetLoopRange(startTime, endTime)
                                                onSeek(startTime)
                                                isLyricLoopSelectionMode = false
                                            }
                                        } else {
                                            onSeek(line.timeMs)
                                        }
                                    }
                                    .let { mod ->
                                        if (blurRadius > 0.2f) mod.blur(blurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                        else mod
                                    }
                                    .padding(vertical = 6.dp, horizontal = 10.dp)
                            ) {
                                if (isCurrent && line.words.isNotEmpty() && !isLyricLoopSelectionMode) {
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
                                        fontSize = 22.sp,
                                        lineHeight = 30.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = line.text,
                                        color = if (isInSelectedLoop) primaryColor else Color.White.copy(alpha = rawAlpha),
                                        fontSize = 22.sp,
                                        fontWeight = if (isCurrent || isInSelectedLoop) FontWeight.Black else FontWeight.Bold,
                                        lineHeight = 30.sp,
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222A2D).copy(0.92f))
                    .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(primaryColor.copy(0.25f)),
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
                            MotionText(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} • ${formatMillis(currentPosMs)}", color = Color.White.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(primaryColor)
                        ) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(22.dp))
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
                Text("Проведи по дуге, чтобы выбрать время", color = textColor.copy(0.65f), fontSize = 12.sp, textAlign = TextAlign.Center)
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
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(textColor.copy(0.06f)).clickable { stopEndTrack = !stopEndTrack }.padding(horizontal = 14.dp, vertical = 8.dp),
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
