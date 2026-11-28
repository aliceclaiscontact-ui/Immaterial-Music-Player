package com.example.mmp.ui.settings

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
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val itemShape = RoundedCornerShape(16.dp)
    val itemSurface = if (textColor.luminance() > 0.5f) Color(0xFF222A2D) else Color(0xFFE6ECEA)
    if (compact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(itemShape)
                .background(itemSurface)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(16.dp))
                    .background(primaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, color = textColor, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = textColor.copy(0.62f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(19.dp))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().clip(itemShape)
                .background(itemSurface)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(primaryColor), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.Start) {
                Text(title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = textColor.copy(0.62f), fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Start)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
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

    val presets = listOf(
        "Mint" to 0xFF79D6C3,
        "Ocean" to 0xFF62B8D8,
        "Sky" to 0xFF83AEEB,
        "Teal" to 0xFF4EAA9A,
        "Coral" to 0xFFFF9278,
        "Rose" to 0xFFE88FA6
    )

    var showCustomColorPicker by remember { mutableStateOf(false) }
    var customRed by remember { mutableFloatStateOf(((themeConfig.primaryHex shr 16) and 0xFF).toFloat()) }
    var customGreen by remember { mutableFloatStateOf(((themeConfig.primaryHex shr 8) and 0xFF).toFloat()) }
    var customBlue by remember { mutableFloatStateOf((themeConfig.primaryHex and 0xFF).toFloat()) }

    val foldersMap = remember(allRawTracks) { allRawTracks.groupBy { it.folderPath } }

    AnimatedContent(
        targetState = activeSettingsMenu,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
        label = ""
    ) { menu ->
        when (menu) {
            "MAIN" -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(start = 0.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    item { Text("ПОДКЛЮЧЕНИЯ", color = textColor.copy(0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
                    item {
                        SettingsCategoryItem(Icons.Filled.CloudQueue, "Стриминг", "Активно: ${selectedStreamState.title}", textColor, primaryColor, { activeSettingsMenu = "STREAMING" }, compact = true)
                    }
                    item { Text("ВНЕШНИЙ ВИД", color = textColor.copy(0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
                    item {
                        SettingsCategoryItem(Icons.Filled.ColorLens, "Интерфейс и темы", "Цвета, акцент и формы", textColor, primaryColor, { activeSettingsMenu = "UI" }, compact = true)
                    }
                    item { Text("ВОСПРОИЗВЕДЕНИЕ", color = textColor.copy(0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
                    item {
                        SettingsCategoryItem(Icons.Filled.GraphicEq, "Звук и эквалайзер", "Затухание, эквалайзер и бас", textColor, primaryColor, { activeSettingsMenu = "SOUND" }, compact = true)
                    }
                    item {
                        SettingsCategoryItem(Icons.Filled.Animation, "Анимации", "Волна прогресса и фокус текста", textColor, primaryColor, { activeSettingsMenu = "ANIMATIONS" }, compact = true)
                    }
                    item { Text("МЕДИАТЕКА", color = textColor.copy(0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
                    item {
                        SettingsCategoryItem(Icons.Filled.FolderSpecial, "Медиатека и папки", "Музыкальные папки и фильтр аудио", textColor, primaryColor, { activeSettingsMenu = "LIBRARY" }, compact = true)
                    }
                    if (!isBatteryIgnored) {
                        item {
                            SettingsCategoryItem(Icons.Filled.BatteryChargingFull, "Работа в фоне", "Стабильная работа без ограничений", textColor, primaryColor, { onRequestBatteryIgnore() }, compact = true)
                        }
                    }
                }
            }
            "STREAMING" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
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
                                    Text("Каталог песен и ремиксов без подписки", color = textColor.copy(0.6f), fontSize = 12.sp)
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
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
                            Text("Исключает голосовые сообщения, рингтоны и короткие сэмплы", color = textColor.copy(0.6f), fontSize = 12.sp)
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }) {
                                Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Интерфейс и темы", color = textColor, fontWeight = FontWeight.Black, fontSize = 21.sp)
                                Text("Настрой внешний вид Immaterial", color = textColor.copy(0.58f), fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(surfaceColor)
                                .border(1.dp, textColor.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Цветовая схема", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Режим оформления и акцентный цвет", color = textColor.copy(0.58f), fontSize = 12.sp)
                            }
                            Text("Режим оформления", color = textColor.copy(0.82f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val modes = listOf(
                                Triple("Тёмная", ThemeMode.DARK, 0xFF15191D),
                                Triple("Светлая", ThemeMode.LIGHT, 0xFFF4F6F5),
                                Triple("Adaptive", ThemeMode.ADAPTIVE, 0xFF15191D),
                                Triple("Adaptive светлая", ThemeMode.ADAPTIVE_LIGHT, 0xFFF4F6F5)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                modes.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { (label, mode, bg) ->
                                            val selected = themeConfig.themeMode == mode
                                            Row(
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                                    .background(if (selected) primaryColor.copy(0.16f) else textColor.copy(0.035f))
                                                    .border(if (selected) 1.5.dp else 1.dp, if (selected) primaryColor else textColor.copy(0.08f), RoundedCornerShape(16.dp))
                                                    .clickable { onThemeChange(themeConfig.copy(themeMode = mode, bgHex = bg)) }
                                                    .padding(horizontal = 11.dp, vertical = 13.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(Modifier.size(15.dp).clip(CircleShape).background(if (mode == ThemeMode.LIGHT || mode == ThemeMode.ADAPTIVE_LIGHT) Color(0xFFE6ECEA) else Color(0xFF222A2D)).border(1.dp, textColor.copy(0.2f), CircleShape))
                                                Spacer(Modifier.width(8.dp))
                                                Text(label, color = if (selected) primaryColor else textColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                                if (selected) {
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(Icons.Filled.CheckCircle, null, tint = primaryColor, modifier = Modifier.size(17.dp))
                                                }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("Акцентный цвет", color = textColor.copy(0.82f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(presets) { (name, hex) ->
                                    val selected = themeConfig.primaryHex == hex
                                    Box(
                                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                                            .background(Color(hex))
                                            .border(if (selected) 3.dp else 1.dp, if (selected) textColor else textColor.copy(0.18f), RoundedCornerShape(16.dp))
                                            .clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                                    }
                                }
                                item {
                                    Box(
                                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                                            .background(textColor.copy(0.06f)).border(1.dp, textColor.copy(0.12f), RoundedCornerShape(16.dp))
                                            .clickable { showCustomColorPicker = !showCustomColorPicker },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Filled.Colorize, null, tint = primaryColor, modifier = Modifier.size(20.dp)) }
                                }
                            }
                            AnimatedVisibility(visible = showCustomColorPicker) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(textColor.copy(0.045f)).padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    val previewColor = Color(customRed.toInt(), customGreen.toInt(), customBlue.toInt())
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Свой цвет", color = textColor, fontWeight = FontWeight.Bold)
                                        Box(Modifier.size(30.dp).clip(CircleShape).background(previewColor).border(1.dp, textColor.copy(0.2f), CircleShape))
                                    }
                                    Text("Красный", color = textColor.copy(0.65f), fontSize = 11.sp)
                                    Slider(value = customRed, onValueChange = { customRed = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor))
                                    Text("Зелёный", color = textColor.copy(0.65f), fontSize = 11.sp)
                                    Slider(value = customGreen, onValueChange = { customGreen = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor))
                                    Text("Синий", color = textColor.copy(0.65f), fontSize = 11.sp)
                                    Slider(value = customBlue, onValueChange = { customBlue = it }, valueRange = 40f..255f, colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor))
                                    Button(
                                        onClick = {
                                            val hex = (0xFFL shl 24) or (customRed.toLong() shl 16) or (customGreen.toLong() shl 8) or customBlue.toLong()
                                            onThemeChange(themeConfig.copy(themeName = "Custom RGB", primaryHex = hex))
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Применить цвет", color = Color.Black, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(surfaceColor)
                                .border(1.dp, textColor.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Форма элементов", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Форма кнопок и поверхностей. Обложки всегда остаются квадратными.", color = textColor.copy(0.58f), fontSize = 12.sp)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ShapeStyle.entries) { style ->
                                    val selected = themeConfig.shapeStyle == style
                                    val previewShape = style.toExpressiveShape()
                                    Column(
                                        modifier = Modifier.width(84.dp).clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) primaryColor.copy(0.14f) else textColor.copy(0.035f))
                                            .border(if (selected) 1.5.dp else 1.dp, if (selected) primaryColor else textColor.copy(0.08f), RoundedCornerShape(16.dp))
                                            .clickable { onThemeChange(themeConfig.copy(shapeStyle = style)) }
                                            .padding(vertical = 9.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(Modifier.size(40.dp).clip(previewShape).background(if (selected) primaryColor else primaryColor.copy(0.20f)), contentAlignment = Alignment.Center) {
                                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                                        }
                                        Text(style.label, color = if (selected) primaryColor else textColor.copy(0.72f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Filled.FileUpload, null, tint = primaryColor)
                                Spacer(Modifier.width(7.dp))
                                Text("Экспорт темы", color = textColor, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Filled.FileDownload, null, tint = primaryColor)
                                Spacer(Modifier.width(7.dp))
                                Text("Импорт темы", color = textColor, fontWeight = FontWeight.SemiBold)
                            }
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

                val bands = listOf(
                    Triple("60 Гц", band60) { v: Float -> saveEqBands(v, band230, band910, band3600, band14000) },
                    Triple("230 Гц", band230) { v: Float -> saveEqBands(band60, v, band910, band3600, band14000) },
                    Triple("910 Гц", band910) { v: Float -> saveEqBands(band60, band230, v, band3600, band14000) },
                    Triple("3.6 кГц", band3600) { v: Float -> saveEqBands(band60, band230, band910, v, band14000) },
                    Triple("14 кГц", band14000) { v: Float -> saveEqBands(band60, band230, band910, band3600, v) }
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            IconButton(onClick = { activeSettingsMenu = "MAIN" }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.ArrowBackIosNew, null, tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            Text("Звук и Эквалайзер", color = textColor, fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Start)
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Плавное затухание", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Переход громкости между треками", color = textColor.copy(0.6f), fontSize = 11.sp)
                                }
                                Text(if (crossfadeSec.toInt() == 0) "Выкл" else "${crossfadeSec.toInt()} сек", color = primaryColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
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
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(cardShape).background(surfaceColor).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("5-полосный эквалайзер", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Обработка аудиопотока в реальном времени", color = textColor.copy(0.6f), fontSize = 11.sp)
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
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                item { ExpressiveFilterPill("Flat", false, primaryColor, textColor) { saveEqBands(0f, 0f, 0f, 0f, 0f) } }
                                item { ExpressiveFilterPill("Bass Boost", false, primaryColor, textColor) { saveEqBands(8f, 5f, 0f, 1f, 2f) } }
                                item { ExpressiveFilterPill("Hyperpop", false, primaryColor, textColor) { saveEqBands(7f, 3f, -2f, 5f, 8f) } }
                                item { ExpressiveFilterPill("Vocal", false, primaryColor, textColor) { saveEqBands(-2f, 1f, 6f, 5f, 1f) } }
                            }
                            bands.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    pair.forEach { (label, value, onChange) ->
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(label, color = textColor.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                Text("${if (value >= 0) "+" else ""}${value.toInt()} дБ", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(
                                                value = value,
                                                onValueChange = onChange,
                                                valueRange = -12f..12f,
                                                enabled = eqEnabled,
                                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                            )
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Усиление баса", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${bassBoostLevel.toInt()}%", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            }
            "ANIMATIONS" -> {
                BackHandler { activeSettingsMenu = "MAIN" }
                val ctx = LocalContext.current
                val prefs = remember { ctx.getSharedPreferences("mmp_prefs_v3", Context.MODE_PRIVATE) }
                var waveAnimEnabled by remember { mutableStateOf(prefs.getBoolean("anim_wave_enabled", true)) }
                var blurLyricsEnabled by remember { mutableStateOf(prefs.getBoolean("anim_lyrics_blur", false)) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
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
                                    Text("Волна прогресс-бара", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                                    Text("Плавная смена строк песни", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Motion blur при переходе к следующей строке текста", color = textColor.copy(0.6f), fontSize = 12.sp)
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
