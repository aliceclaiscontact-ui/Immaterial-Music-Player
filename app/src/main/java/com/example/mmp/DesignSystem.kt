package com.example.mmp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ImmaU1Palette {
    const val Accent: Long = 0xFFD9A1FAL
    const val Secondary: Long = 0xFFAD86E8L
    const val Background: Long = 0xFF2A1040L
    const val Surface: Long = 0xFF3B2452L
    const val LightBackground: Long = 0xFFF8F4FCL
    const val LightSurface: Long = 0xFFECE4F3L
    const val DarkText: Long = 0xFFF8F0FFL
}

object MMPShapeRadii {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
}

private class RoundedPolygonComposeShape(
    private val polygon: RoundedPolygon
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val bounds = polygon.calculateBounds()
        val polygonWidth = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
        val polygonHeight = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
        val matrix = Matrix()
        matrix.scale(size.width / polygonWidth, size.height / polygonHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

private fun createLobedPolygon(lobes: Int, innerRadius: Float): RoundedPolygon {
    val pointCount = 96
    val vertices = FloatArray(pointCount * 2)
    for (index in 0 until pointCount) {
        val angle = (2.0 * PI * index / pointCount) - PI / 2.0
        val wave = (cos(lobes * angle) + 1.0) / 2.0
        val radius = innerRadius + (1.0 - innerRadius) * wave
        vertices[index * 2] = (radius * cos(angle)).toFloat()
        vertices[index * 2 + 1] = (radius * sin(angle)).toFloat()
    }
    return RoundedPolygon(vertices = vertices, rounding = CornerRounding(radius = 0.018f, smoothing = 0.82f))
}

private val cloverPolygon by lazy { createLobedPolygon(lobes = 4, innerRadius = 0.48f) }
private val flowerPolygon by lazy { createLobedPolygon(lobes = 6, innerRadius = 0.76f) }
private val starPolygon by lazy {
    RoundedPolygon.star(
        numVerticesPerRadius = 5,
        radius = 1f,
        innerRadius = 0.52f,
        rounding = CornerRounding(radius = 0.075f, smoothing = 0.72f)
    )
}

fun ShapeStyle.toCardShape(): Shape = RoundedCornerShape(MMPShapeRadii.large)

fun ShapeStyle.toExpressiveShape(): Shape = when (this) {
    ShapeStyle.SQUIRCLE -> RoundedCornerShape(MMPShapeRadii.medium)
    ShapeStyle.COOKIE -> RoundedPolygonComposeShape(cloverPolygon)
    ShapeStyle.FLOWER -> RoundedPolygonComposeShape(flowerPolygon)
    ShapeStyle.STAR -> RoundedPolygonComposeShape(starPolygon)
}

@Composable
fun FrostedDockBar(
    selectedTab: Int,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    isLightMode: Boolean,
    onTabSelected: (Int) -> Unit
) {
    val panelShape = RoundedCornerShape(MMPShapeRadii.large)
    val panelBrush = Brush.verticalGradient(
        if (isLightMode) listOf(
            Color.White.copy(alpha = 0.78f),
            Color(0xFFF3EAFB).copy(alpha = 0.66f),
            surfaceColor.copy(alpha = 0.62f)
        ) else listOf(
            Color(0xFF76558E).copy(alpha = 0.42f),
            Color(0xFF49305F).copy(alpha = 0.50f),
            Color(0xFF241331).copy(alpha = 0.62f)
        )
    )
    val edgeBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isLightMode) 0.78f else 0.28f),
            primaryColor.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.06f)
        )
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, panelShape, clip = false)
            .clip(panelShape)
            .background(panelBrush)
            .border(1.dp, edgeBrush, panelShape)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(Icons.Outlined.Home, "Главная", selectedTab == 0, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(0) }
        DockItem(Icons.Outlined.LibraryMusic, "Медиатека", selectedTab == 1, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(1) }
        DockItem(Icons.Outlined.CloudQueue, "Онлайн", selectedTab == 2, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(2) }
        DockItem(Icons.Outlined.Settings, "Настройки", selectedTab == 3, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(3) }
    }
}

@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    primaryColor: Color,
    textColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(MMPShapeRadii.medium))
            .background(if (selected) primaryColor.copy(alpha = 0.17f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) primaryColor else textColor.copy(alpha = 0.70f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) primaryColor else textColor.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1)
    }
}
