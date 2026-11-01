package com.example.cricketscoring.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.model.CricketStyle

val PitchBlack = Color(0xFF0B1220)
val PitchCard = Color(0xFF152033)
val PitchRaised = Color(0xFF1E2D45)
val PitchGlass = Color(0xCC1A2740)
val Lawn = Color(0xFF3DDC97)
val ActionCoral = Color(0xFFFF7A45)
val ActionCoralDeep = Color(0xFFFF5A2E)
val Gold = Color(0xFFF5C451)
val WicketRed = Color(0xFFFF5C6A)
val Ink = Color(0xFFF4F7FB)
val Muted = Color(0xFFA9B7CC)
val BoxAmber = Color(0xFFFFB020)
val OpenTeal = Color(0xFF2EC4B6)
val OverarmGreen = Color(0xFF8DFF72)
val SoftWhite = Color(0xFFF7F8FC)

val RadiusSm = 14.dp
val RadiusMd = 20.dp
val RadiusLg = 28.dp
val RadiusXl = 32.dp
val ElevationLow = 2.dp
val ElevationMid = 6.dp
val ElevationHigh = 12.dp

val ActionBrush = Brush.horizontalGradient(listOf(ActionCoral, ActionCoralDeep))
val HeroBrush = Brush.linearGradient(listOf(Color(0xFF21405F), Color(0xFF152033), Color(0xFF0F1A2C)))
val AccentBrushTeal = Brush.horizontalGradient(listOf(OpenTeal, Color(0xFF1B8F9E)))
val AccentBrushAmber = Brush.horizontalGradient(listOf(BoxAmber, ActionCoral))

fun CricketStyle.accent(): Color = when (this) {
    CricketStyle.UNDERARM_BOX -> BoxAmber
    CricketStyle.UNDERARM_OPEN -> OpenTeal
    CricketStyle.OVERARM -> OverarmGreen
}

fun CricketStyle.heroBrush(): Brush = when (this) {
    CricketStyle.UNDERARM_BOX -> AccentBrushAmber
    CricketStyle.UNDERARM_OPEN -> AccentBrushTeal
    CricketStyle.OVERARM -> Brush.horizontalGradient(listOf(OverarmGreen, OpenTeal))
}

private val PitchColors = darkColorScheme(
    primary = ActionCoral,
    onPrimary = SoftWhite,
    primaryContainer = Color(0xFF3A2418),
    onPrimaryContainer = Color(0xFFFFE0D2),
    secondary = Lawn,
    onSecondary = Color(0xFF052117),
    secondaryContainer = Color(0xFF143528),
    onSecondaryContainer = Color(0xFFD9FFE9),
    tertiary = OpenTeal,
    background = PitchBlack,
    onBackground = Ink,
    surface = PitchCard,
    onSurface = Ink,
    surfaceVariant = PitchRaised,
    onSurfaceVariant = Muted,
    error = WicketRed,
    onError = Color(0xFF2A060A),
    outline = Color(0xFF334866),
)

private val PitchType = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

private val PitchShapes = Shapes(
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
)

@Composable
fun PitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PitchColors, typography = PitchType, shapes = PitchShapes, content = content)
}
