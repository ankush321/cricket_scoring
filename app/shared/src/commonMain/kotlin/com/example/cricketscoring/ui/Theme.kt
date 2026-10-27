package com.example.cricketscoring.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.model.CricketStyle

val PitchBlack = Color(0xFF07140F)
val PitchCard = Color(0xFF10241C)
val PitchRaised = Color(0xFF17352A)
val Lawn = Color(0xFF3DDC97)
val Gold = Color(0xFFF5C451)
val WicketRed = Color(0xFFFF5C6A)
val Ink = Color(0xFFF4F7F2)
val Muted = Color(0xFFB7CBBF)
val BoxAmber = Color(0xFFFFB020)
val OpenTeal = Color(0xFF2EC4B6)
val OverarmGreen = Color(0xFF8DFF72)

fun CricketStyle.accent(): Color = when (this) {
    CricketStyle.UNDERARM_BOX -> BoxAmber
    CricketStyle.UNDERARM_OPEN -> OpenTeal
    CricketStyle.OVERARM -> OverarmGreen
}

private val PitchColors = darkColorScheme(
    primary = Lawn,
    onPrimary = Color(0xFF052117),
    primaryContainer = Color(0xFF143528),
    onPrimaryContainer = Color(0xFFD9FFE9),
    secondary = Gold,
    onSecondary = Color(0xFF2A2108),
    secondaryContainer = Color(0xFF3A3214),
    onSecondaryContainer = Color(0xFFFFE7A3),
    background = PitchBlack,
    onBackground = Ink,
    surface = PitchCard,
    onSurface = Ink,
    surfaceVariant = PitchRaised,
    onSurfaceVariant = Muted,
    error = WicketRed,
    onError = Color(0xFF2A060A),
    outline = Color(0xFF2C5644),
)

private val PitchType = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

private val PitchShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun PitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PitchColors, typography = PitchType, shapes = PitchShapes, content = content)
}
