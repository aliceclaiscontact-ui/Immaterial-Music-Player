package com.example.mmp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.mmp.domain.model.ShapeStyle

object Material3ExpressivePalette {
    const val Accent: Long = 0xFF6750A4L
    const val Secondary: Long = 0xFF625B71L
    const val Background: Long = 0xFF141218L
    const val Surface: Long = 0xFF211F26L
    const val LightBackground: Long = 0xFFF7F4FAL
    const val LightSurface: Long = 0xFFF0ECF4L
    const val DarkText: Long = 0xFFE6E1E9L
}

object MMPShapeRadii {
    val small = 12.dp
    val medium = 22.dp
    val large = 30.dp
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
    val destinations = listOf(
        Icons.Filled.Home to "Главная",
        Icons.Filled.LibraryMusic to "Медиатека",
        Icons.Filled.CloudQueue to "Онлайн",
        Icons.Filled.Settings to "Настройки"
    )
    Surface(
        color = surfaceColor,
        contentColor = textColor,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        tonalElevation = if (isLightMode) 3.dp else 1.dp,
        shadowElevation = 5.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = textColor,
            tonalElevation = 0.dp
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    icon = {
                        androidx.compose.material3.Icon(
                            destination.first,
                            contentDescription = destination.second
                        )
                    },
                    label = { Text(destination.second) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryColor,
                        selectedTextColor = primaryColor,
                        indicatorColor = primaryColor.copy(alpha = 0.18f),
                        unselectedIconColor = textColor.copy(alpha = 0.72f),
                        unselectedTextColor = textColor.copy(alpha = 0.72f)
                    )
                )
            }
        }
    }
}
