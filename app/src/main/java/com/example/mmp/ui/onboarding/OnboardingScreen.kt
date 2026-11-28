package com.example.mmp.ui.onboarding

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
                        "Аудиоплеер с живой анимацией, эквалайзером, офлайн-текстами и стримингом без ограничений.",
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
                        Text("• Тап по обложке: полноэкранный синхронный текст", color = textColor.copy(0.85f), fontSize = 13.sp)
                        Text("• Кнопка 1x: быстрая настройка скорости, питча и пресетов", color = textColor.copy(0.85f), fontSize = 13.sp)
                    }
                }
                else -> {
                    Text("Выбери источник стриминга", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Платформу всегда можно сменить в Настройках", color = textColor.copy(0.65f), fontSize = 12.sp, textAlign = TextAlign.Center)
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
                            Text("Полный каталог песен и ремиксов без подписки", color = textColor.copy(0.65f), fontSize = 12.sp)
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
