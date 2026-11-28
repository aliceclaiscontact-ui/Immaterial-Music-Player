package com.example.mmp.ui.home

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
    onImportTheme: () -> Unit,
    settingsOnly: Boolean = false
) {
    val context = LocalContext.current
    val activeSubTab = if (settingsOnly) 1 else 0
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.Start) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (settingsOnly) "Настройки" else "Главная",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = activeSubTab,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
            label = ""
        ) { subTab ->
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
                                        Text("Моя волна", color = textColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        Text("Микс из твоих треков и ${activeStreamingProvider.title}", color = textColor.copy(0.65f), fontSize = 12.sp)
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
                            Text("Статистика артистов", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
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
                                    text = if (selectedDateFilter == "ALL") "Главные артисты" else "Артисты за $selectedDateFilter",
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
