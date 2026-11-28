package com.example.mmp.ui.theme

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mmp.domain.model.ShapeStyle

object ImmaU1Palette {
    const val Accent: Long = 0xFF79D6C3L
    const val Secondary: Long = 0xFF4EAA9AL
    const val Background: Long = 0xFF15191DL
    const val Surface: Long = 0xFF222A2DL
    const val LightBackground: Long = 0xFFF4F6F5L
    const val LightSurface: Long = 0xFFE6ECEAL
    const val DarkText: Long = 0xFFF1F5F4L
}

object MMPShapeRadii {
    val small = 8.dp
    val medium = 12.dp
    val large = 18.dp
}

fun ShapeStyle.toCardShape(): Shape = RoundedCornerShape(MMPShapeRadii.large)

fun ShapeStyle.toExpressiveShape(): Shape = RoundedCornerShape(MMPShapeRadii.medium)

@Composable
fun FrostedDockBar(
    selectedTab: Int,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    isLightMode: Boolean,
    onTabSelected: (Int) -> Unit
) {
    val panelShape = RoundedCornerShape(MMPShapeRadii.medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(surfaceColor)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(Icons.Filled.Home, "Главная", selectedTab == 0, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(0) }
        DockItem(Icons.Filled.LibraryMusic, "Медиатека", selectedTab == 1, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(1) }
        DockItem(Icons.Filled.CloudQueue, "Онлайн", selectedTab == 2, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(2) }
        DockItem(Icons.Filled.Settings, "Настройки", selectedTab == 3, primaryColor, textColor, Modifier.weight(1f)) { onTabSelected(3) }
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
            .background(if (selected) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) primaryColor else textColor.copy(alpha = 0.76f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) primaryColor else textColor.copy(alpha = 0.78f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}
