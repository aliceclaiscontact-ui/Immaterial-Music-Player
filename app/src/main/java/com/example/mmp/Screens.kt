package com.example.mmp

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    currentShape: Shape,
    themeConfig: MMPThemeConfig,
    onRequestPermission: () -> Unit,
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
        if (total > 0 && top != null) "Артист ${top.key} играл ${((top.value.toFloat() / total) * 100).toInt()}% твоей музыки!"
        else "Артист SOPHIE играл 57% твоей музыки!"
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MMP", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
            Box(
                modifier = Modifier.clip(currentShape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor.copy(0.5f), currentShape).padding(14.dp, 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${listenedMinutes}м ${listenedSeconds}с в музыке", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveFilterPill("История прослушивания", activeSubTab == 0, currentShape, primaryColor) { activeSubTab = 0 }
            ExpressiveFilterPill("Настройки", activeSubTab == 1, currentShape, primaryColor) { activeSubTab = 1 }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = activeSubTab, label = "SubTab") { subTab ->
            if (subTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    if (!hasPermission) {
                        item {
                            Button(onClick = onRequestPermission, shape = currentShape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                                Text("Разрешить доступ к аудиофайлам", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(currentShape)
                                .background(Brush.linearGradient(listOf(primaryColor.copy(0.35f), Color(themeConfig.secondaryHex).copy(0.25f))))
                                .border(1.dp, Color.White.copy(0.18f), currentShape).padding(20.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primaryColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("СВЕДЕНИЯ О ПРОСЛУШИВАНИИ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(insightText, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Треков в памяти телефона: $allTracksCount", fontSize = 13.sp, color = Color.White.copy(0.75f))
                            }
                        }
                    }
                    item { Text("Последние 5 треков", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    items(recentTracks.take(5), key = { it.id }) { TrackListItem(it, currentShape, primaryColor) { onTrackClick(it) } }
                }
            } else {
                SettingsAndThemeEditor(themeConfig, currentShape, onThemeChange, onExportTheme, onImportTheme)
            }
        }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    playlists: List<String>,
    hasPermission: Boolean,
    currentShape: Shape,
    primaryColor: Color,
    onRequestPermission: () -> Unit,
    onRescanClick: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    var category by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Моя медиатека", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            IconButton(onClick = onRescanClick, modifier = Modifier.clip(CircleShape).background(Color.White.copy(0.08f))) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveFilterPill("Треки (${tracks.size})", category == 0, CircleShape, primaryColor) { category = 0 }
            ExpressiveFilterPill("Исполнители", category == 1, CircleShape, primaryColor) { category = 1 }
            ExpressiveFilterPill("Альбомы", category == 2, CircleShape, primaryColor) { category = 2 }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { TrackListItem(it, currentShape, primaryColor) { onTrackClick(it) } }
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: Track?,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    shape: Shape,
    primaryColor: Color,
    onBarClick: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1A1A24).copy(0.92f))
            .border(1.dp, Color.White.copy(0.14f), shape).clickable { onBarClick() }.padding(14.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(46.dp).clip(shape).background(primaryColor.copy(0.25f)), contentAlignment = Alignment.Center) {
                if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = primaryColor)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(track?.title ?: "Выбери трек", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(if (track != null) "${track.artist} • ${metadata.formatName} •${formatMillis(currentPosMs)}" else "Локальная музыка", color = Color.White.copy(0.65f), fontSize = 11.sp, maxLines = 1)
            }
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.clip(shape).background(primaryColor).size(42.dp)) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black)
        }
    }
}

@Composable
fun FullPlayerDialog(
    track: Track,
    metadata: DetailedMetadata,
    isPlaying: Boolean,
    currentPosMs: Long,
    shape: Shape,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = Color(0xFF14141D),
        title = { Text(track.title, color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(200.dp).clip(shape).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
                    if (metadata.coverArt != null) Image(metadata.coverArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.Album, null, tint = primaryColor, modifier = Modifier.size(80.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(track.artist, color = Color.White.copy(0.7f), fontSize = 14.sp)
                Slider(
                    value = currentPosMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..track.durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp).clip(shape).background(primaryColor)) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Свернуть", color = primaryColor) } }
    )
}

@Composable
fun SettingsAndThemeEditor(themeConfig: MMPThemeConfig, currentShape: Shape, onThemeChange: (MMPThemeConfig) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val presets = listOf("Neon" to 0xFFFF66C4, "Acid" to 0xFFA6FF00, "Violet" to 0xFF9D4EDD, "Cyan" to 0xFF00F5D4)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Кастомизация темы", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presets.forEach { (name, hex) ->
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(hex)).clickable { onThemeChange(themeConfig.copy(themeName = name, primaryHex = hex)) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeStyle.entries.forEach { style ->
                    ExpressiveFilterPill(style.label, themeConfig.shapeStyle == style, currentShape, Color(themeConfig.primaryHex)) { onThemeChange(themeConfig.copy(shapeStyle = style)) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onExport, shape = currentShape, colors = ButtonDefaults.buttonColors(containerColor = Color(themeConfig.primaryHex))) {
                    Text("Экспорт .mmpui", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onImport, shape = currentShape) { Text("Импорт", color = Color.White) }
            }
        }
    }
}

@Composable
fun FrostedDockBar(selectedTab: Int, shape: Shape, primaryColor: Color, onTabSelected: (Int) -> Unit, onCreateClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0xFF14141D).copy(0.85f)).border(1.dp, Color.White.copy(0.14f), shape).padding(12.dp, 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(Icons.Filled.Home, "Главная", selectedTab == 0, primaryColor, shape) { onTabSelected(0) }
        DockItem(Icons.Filled.LibraryMusic, "Медиатека", selectedTab == 1, primaryColor, shape) { onTabSelected(1) }
        Box(modifier = Modifier.clip(shape).background(primaryColor.copy(0.2f)).border(1.dp, primaryColor, shape).clickable { onCreateClick() }.padding(14.dp, 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = primaryColor)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Создать", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DockItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, primaryColor: Color, shape: Shape, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(shape).background(if (selected) primaryColor.copy(0.2f) else Color.Transparent).clickable { onClick() }.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) primaryColor else Color.White.copy(0.6f))
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun ExpressiveFilterPill(text: String, selected: Boolean, shape: Shape, primaryColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(shape).background(if (selected) primaryColor else Color.White.copy(0.08f)).border(1.dp, if (selected) primaryColor else Color.White.copy(0.15f), shape).clickable { onClick() }.padding(16.dp, 8.dp)
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun TrackListItem(track: Track, shape: Shape, primaryColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(0.05f)).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("${track.artist} •${formatMillis(track.durationMs)}", color = Color.White.copy(0.6f), fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, null, tint = primaryColor)
    }
}

@Composable
fun CreatePlaylistDialog(shape: Shape, primaryColor: Color, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = Color(0xFF181822),
        title = { Text("Новый плейлист", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, shape = shape) },
        confirmButton = { Button(onClick = { onCreate(name) }, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("ОК", color = Color.Black) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White) } }
    )
}
