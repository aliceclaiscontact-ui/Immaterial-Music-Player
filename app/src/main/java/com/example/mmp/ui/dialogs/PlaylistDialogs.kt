package com.example.mmp.ui.dialogs

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
                item { OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, label = { Text("Текст песни") }, minLines = 3, maxLines = 6, shape = cardShape, modifier = Modifier.fillMaxWidth()) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueManagementSheet(
    queue: List<Track>, currentTrack: Track?, cardShape: Shape, primaryColor: Color, textColor: Color, surfaceColor: Color,
    onDismiss: () -> Unit, onSelectTrack: (Track) -> Unit, onMoveTrack: (Int, Int) -> Unit, onRemoveTrack: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 76.dp.toPx() }
    val latestQueue by rememberUpdatedState(queue)
    LaunchedEffect(draggingId, autoScrollDirection) {
        while (draggingId != null && autoScrollDirection != 0) {
            listState.scrollBy(autoScrollDirection * 18f)
            kotlinx.coroutines.delay(16)
        }
    }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.58f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding().clip(cardShape).background(surfaceColor).border(1.dp, textColor.copy(0.12f), cardShape).clickable(onClick = {}).padding(16.dp).heightIn(min = 320.dp, max = 690.dp)) {
            Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(textColor.copy(0.28f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Очередь", color = textColor, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("${queue.size} треков • удерживай и перетаскивай", color = textColor.copy(0.58f), fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = textColor) }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(queue, key = { _, t -> t.id }) { index, trk ->
                    val current = currentTrack?.id == trk.id
                    val dragging = draggingId == trk.id
                    Row(Modifier.animateItemPlacement().fillMaxWidth().zIndex(if (dragging) 1f else 0f).graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        scaleX = if (dragging) 1.02f else 1f
                        scaleY = if (dragging) 1.02f else 1f
                        shadowElevation = if (dragging) 12.dp.toPx() else 0f
                    }.clip(RoundedCornerShape(16.dp)).background(if (current) primaryColor.copy(0.16f) else textColor.copy(0.045f)).clickable { onSelectTrack(trk) }.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrackCoverThumb(trk, RoundedCornerShape(8.dp), primaryColor, 44)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trk.title, color = if (current) primaryColor else textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${trk.artist} • ${formatMillis(trk.durationMs)}", color = textColor.copy(0.58f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(
                            onClick = { if (!current) onRemoveTrack(index) },
                            enabled = !current,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, null, tint = if (current) textColor.copy(0.22f) else textColor.copy(0.55f), modifier = Modifier.size(18.dp))
                        }
                        Box(Modifier.size(36.dp).pointerInput(trk.id) {
                            detectDragGestures(
                                onDragStart = { draggingId = trk.id; dragOffset = 0f; autoScrollDirection = 0 },
                                onDragEnd = { draggingId = null; dragOffset = 0f; autoScrollDirection = 0 },
                                onDragCancel = { draggingId = null; dragOffset = 0f; autoScrollDirection = 0 },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta.y
                                    val info = listState.layoutInfo
                                    val dragged = info.visibleItemsInfo.firstOrNull { it.key == trk.id } ?: return@detectDragGestures
                                    val currentIndex = latestQueue.indexOfFirst { it.id == trk.id }
                                    val center = dragged.offset + dragged.size / 2 + dragOffset
                                    val target = info.visibleItemsInfo.firstOrNull { center >= it.offset && center <= it.offset + it.size }
                                    if (target != null && currentIndex >= 0 && target.index != currentIndex) {
                                        onMoveTrack(currentIndex, target.index)
                                        dragOffset = 0f
                                    }
                                    autoScrollDirection = when {
                                        center < info.viewportStartOffset + edge -> -1
                                        center > info.viewportEndOffset - edge -> 1
                                        else -> 0
                                    }
                                }
                            )
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.DragIndicator, "Перетащить", tint = primaryColor, modifier = Modifier.size(21.dp)) }
                    }
                }
            }
        }
    }
}
