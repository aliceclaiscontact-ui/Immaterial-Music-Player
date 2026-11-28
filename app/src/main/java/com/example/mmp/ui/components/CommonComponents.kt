package com.example.mmp.ui.components

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
import androidx.compose.ui.unit.TextUnit
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MotionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { width -> width / 10 }
            ).togetherWith(
                fadeOut(animationSpec = tween(150, easing = LinearOutSlowInEasing)) +
                    slideOutHorizontally(
                        animationSpec = tween(210, easing = FastOutSlowInEasing)
                    ) { width -> -width / 10 }
            )
        },
        contentAlignment = Alignment.CenterStart,
        label = "motion-blur-text"
    ) { displayedText ->
        val blurRadius by transition.animateFloat(
            transitionSpec = { tween(300, easing = FastOutSlowInEasing) },
            label = "motion-blur-radius"
        ) { state ->
            if (state == EnterExitState.Visible) 0f else 2.4f
        }

        Text(
            text = displayedText,
            modifier = modifier.blur(
                radius = blurRadius.dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            ),
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

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
fun TrackCoverThumb(track: Track, shape: Shape, primaryColor: Color, sizeDp: Int = 48) {
    val context = LocalContext.current
    var bitmap by remember(track.id, track.customCoverUri) { mutableStateOf(CoverCache.get(track.id)?.takeIf { !it.isRecycled }) }
    LaunchedEffect(track.id, track.customCoverUri) {
        bitmap = loadTrackThumbnail(context, track)
    }
    Box(modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(primaryColor.copy(0.18f)), contentAlignment = Alignment.Center) {
        val validBmp = bitmap?.takeIf { !it.isRecycled }
        if (validBmp != null) Image(validBmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = primaryColor, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

@Composable
fun PlaylistGridCover(tracks: List<Track>, primaryColor: Color, sizeDp: Int = 54, fillBounds: Boolean = false) {
    val context = LocalContext.current
    val previewTracks = remember(tracks) { tracks.take(4) }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null, null, null) }
    LaunchedEffect(previewTracks) { previewTracks.forEachIndexed { index, track -> bitmaps[index] = loadTrackThumbnail(context, track) } }

    Box(modifier = (if (fillBounds) Modifier.fillMaxSize() else Modifier.size(sizeDp.dp)).clip(RoundedCornerShape(8.dp)).background(primaryColor.copy(0.18f))) {
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
fun PlaylistThumb(playlist: PlaylistItem, allTracks: List<Track>, expressiveShape: Shape, primaryColor: Color, sizeDp: Int = 48, fillBounds: Boolean = false) {
    val context = LocalContext.current
    var coverBmp by remember(playlist.coverUri) { mutableStateOf<Bitmap?>(null) }
    val resolvedTracks = remember(playlist, allTracks) { resolvePlaylistTracks(playlist, allTracks) }

    LaunchedEffect(playlist.coverUri) {
        if (playlist.coverUri != null) {
            coverBmp = loadBitmapFromUriOrHttp(context, playlist.coverUri, 256)
        }
    }

    Box(modifier = (if (fillBounds) Modifier.fillMaxSize() else Modifier.size(sizeDp.dp)).clip(RoundedCornerShape(16.dp)).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
        val valid = coverBmp?.takeIf { !it.isRecycled }
        if (valid != null) {
            Image(valid.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            PlaylistGridCover(resolvedTracks, primaryColor, sizeDp, fillBounds = fillBounds)
        }
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
    isInPlaylist: Boolean = false,
    onAddToPlaylist: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) primaryColor.copy(0.22f) else surfaceColor)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) primaryColor else textColor.copy(0.08f), cardShape)
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
                Icon(if (isInPlaylist) Icons.Filled.Check else Icons.Filled.Add, null, tint = if (isInPlaylist) Color(0xFF42C878) else primaryColor, modifier = Modifier.size(22.dp))
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
    BackHandler { onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(surfaceColor).clickable(onClick = {})
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .heightIn(min = 300.dp, max = 650.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.24f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Добавить в плейлист", color = textColor, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (tracks.size == 1) tracks.first().title else "Выбрано треков: ${tracks.size}",
                        color = textColor.copy(0.62f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть", tint = textColor) }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(primaryColor.copy(0.16f))
                    .clickable { onCreateNewPlaylistClick() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(primaryColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Создать плейлист", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Новая коллекция для выбранных треков", color = textColor.copy(0.62f), fontSize = 11.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = primaryColor)
            }
            Spacer(Modifier.height(18.dp))
            Text("Ваши плейлисты", color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LibraryMusic, null, tint = primaryColor.copy(0.8f), modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Плейлистов пока нет", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Создай первый плейлист выше", color = textColor.copy(0.58f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(playlists, key = { it.name }) { playlist ->
                        val count = resolvePlaylistTracks(playlist, allTracks).size
                        val containsAll = tracks.isNotEmpty() && tracks.all { track ->
                            playlist.trackIds.contains(track.id) ||
                                (track.filePath.isNotBlank() && playlist.trackFilePaths.contains(track.filePath))
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                .background(if (containsAll) primaryColor.copy(0.13f) else textColor.copy(0.045f))
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistThumb(playlist, allTracks, RoundedCornerShape(14.dp), primaryColor, 54)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count треков", color = textColor.copy(0.58f), fontSize = 11.sp)
                            }
                            Box(Modifier.size(38.dp).clip(CircleShape).background(if (containsAll) primaryColor else textColor.copy(0.06f)), contentAlignment = Alignment.Center) {
                                Icon(if (containsAll) Icons.Filled.Check else Icons.Filled.Add, null, tint = if (containsAll) Color.Black else primaryColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackPickerSheet(
    playlist: PlaylistItem, allTracks: List<Track>, currentTracks: List<Track>,
    cardShape: Shape, expressiveShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color,
    onDismiss: () -> Unit, onToggleTrack: (Track) -> Unit
) {
    var query by remember(playlist.name) { mutableStateOf("") }
    val selectedIds = remember(currentTracks) { currentTracks.map { it.id }.toSet() }
    val filtered = remember(allTracks, query) {
        if (query.isBlank()) allTracks
        else allTracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(surfaceColor).clickable(onClick = {})
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .heightIn(min = 380.dp, max = 720.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.24f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Добавить треки", color = textColor, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("В плейлист «${playlist.name}»", color = textColor.copy(0.62f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть", tint = textColor) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Найти трек или исполнителя") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = primaryColor) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = textColor.copy(0.16f),
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    cursorColor = primaryColor
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Медиатека", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${selectedIds.size} добавлено", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered, key = { trackStableKey(it) }) { track ->
                    val added = selectedIds.contains(track.id)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (added) primaryColor.copy(0.13f) else textColor.copy(0.035f))
                            .clickable { onToggleTrack(track) }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackCoverThumb(track, RoundedCornerShape(12.dp), primaryColor, 46)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} • ${formatMillis(track.durationMs)}", color = textColor.copy(0.58f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (added) primaryColor else textColor.copy(0.06f)), contentAlignment = Alignment.Center) {
                            Icon(if (added) Icons.Filled.Check else Icons.Filled.Add, null, tint = if (added) Color.Black else primaryColor, modifier = Modifier.size(20.dp))
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).clickable(onClick = {}).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
fun WavySlider(
    trackKey: Any = 0L,
    durationMs: Long = 1L,
    value: Float,
    onSeekCommit: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color,
    trackColor: Color = waveColor.copy(alpha = 0.25f),
    thumbColor: Color = waveColor
) {
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
    val waveEnabled = remember { prefs.getBoolean("anim_wave_enabled", true) }

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
        animationSpec = tween(durationMillis = 260),
        label = "waveAmp"
    )

    var isScrubbing by remember(trackKey) { mutableStateOf(false) }
    var scrubFraction by remember(trackKey) { mutableFloatStateOf(0f) }
    var holdUntilMs by remember(trackKey) { mutableLongStateOf(0L) }
    var sliderWidthPx by remember { mutableFloatStateOf(1f) }

    val nowMs = SystemClock.elapsedRealtime()
    val activeFraction = when {
        isScrubbing -> scrubFraction
        nowMs < holdUntilMs -> scrubFraction
        else -> value.coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat()
                if (w > 1f) sliderWidthPx = w
            }
            .pointerInput(trackKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = sliderWidthPx.coerceAtLeast(1f)
                    val startFrac = (down.position.x / w).coerceIn(0f, 1f)
                    scrubFraction = startFrac
                    isScrubbing = true
                    down.consume()

                    var latestFrac = startFrac
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                        val x = change.position.x.coerceIn(0f, w)
                        latestFrac = (x / w).coerceIn(0f, 1f)
                        scrubFraction = latestFrac
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (change.positionChange() != Offset.Zero) change.consume()
                            break
                        }
                    }

                    scrubFraction = latestFrac
                    holdUntilMs = SystemClock.elapsedRealtime() + 1200L
                    isScrubbing = false
                    onSeekCommit(latestFrac)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
            val width = size.width
            val midY = size.height / 2f
            val thumbX = (width * activeFraction).coerceIn(0f, width)
            val gap = 6.dp.toPx()

            if (thumbX + gap < width) {
                drawLine(
                    color = trackColor,
                    start = Offset((thumbX + gap).coerceAtMost(width), midY),
                    end = Offset(width, midY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (thumbX - gap > 0f) {
                val endX = (thumbX - gap).coerceAtLeast(0f)
                val maxAmpPx = 4.0.dp.toPx() * amplitudeFraction
                if (maxAmpPx < 0.2f) {
                    drawLine(
                        color = waveColor,
                        start = Offset(0f, midY),
                        end = Offset(endX, midY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val wavePath = Path()
                    val waveLength = 42.dp.toPx().coerceAtLeast(1f)
                    var x = 0f
                    val step = 4f
                    var first = true
                    while (x <= endX) {
                        val rel = x / waveLength
                        val y = midY + sin((rel * 2.0 * Math.PI + phase.toDouble())).toFloat() * maxAmpPx
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

            val thumbHalfH = if (isScrubbing) 12.dp.toPx() else 9.dp.toPx()
            drawLine(
                color = thumbColor,
                start = Offset(thumbX, midY - thumbHalfH),
                end = Offset(thumbX, midY + thumbHalfH),
                strokeWidth = if (isScrubbing) 5.dp.toPx() else 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        if (isScrubbing) {
            val safeDurMs = durationMs.coerceAtLeast(0L)
            val previewMs = (activeFraction * safeDurMs).toLong()
            val bubbleW = 58.dp
            val bubbleWPx = with(density) { bubbleW.toPx() }
            val maxLeftPx = (sliderWidthPx - bubbleWPx).coerceAtLeast(0f)
            val leftPx = (sliderWidthPx * activeFraction - bubbleWPx / 2f).coerceIn(0f, maxLeftPx)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { leftPx.toDp() }, y = (-34).dp)
                    .width(bubbleW)
                    .clip(RoundedCornerShape(50))
                    .background(waveColor)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatMillis(previewMs),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}
