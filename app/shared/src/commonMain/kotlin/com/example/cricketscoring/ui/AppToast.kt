package com.example.cricketscoring.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

enum class AppToastType {
    Success,
    Warning,
    Info,
    Error,
}

object AppToastDefaults {
    const val DurationMs = 2_500L
    const val ExitAnimationMs = 300L
    val HostBottomClearance = 88.dp
    val HorizontalPadding = 16.dp
    val BottomPadding = 16.dp
}

@Immutable
private data class AppToastColors(
    val background: Color,
    val border: Color,
    val icon: Color,
    val text: Color,
)

private fun AppToastType.toastColors(): AppToastColors = when (this) {
    AppToastType.Success -> AppToastColors(
        background = Lawn.copy(alpha = 0.16f),
        border = Lawn.copy(alpha = 0.65f),
        icon = Lawn,
        text = Ink,
    )
    AppToastType.Warning -> AppToastColors(
        background = Gold.copy(alpha = 0.16f),
        border = Gold.copy(alpha = 0.65f),
        icon = Gold,
        text = Ink,
    )
    AppToastType.Info -> AppToastColors(
        background = OpenTeal.copy(alpha = 0.16f),
        border = OpenTeal.copy(alpha = 0.65f),
        icon = OpenTeal,
        text = Ink,
    )
    AppToastType.Error -> AppToastColors(
        background = WicketRed.copy(alpha = 0.16f),
        border = WicketRed.copy(alpha = 0.65f),
        icon = WicketRed,
        text = Ink,
    )
}

@Composable
fun AppToast(
    message: String,
    type: AppToastType,
    modifier: Modifier = Modifier,
) {
    val colors = type.toastColors()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colors.background, shape = shape)
            .border(width = 1.dp, color = colors.border, shape = shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppToastIcon(type = type, tint = colors.icon, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = message,
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
    }
}

@Composable
private fun AppToastIcon(
    type: AppToastType,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (type) {
            AppToastType.Success -> SuccessToastIcon(tint)
            AppToastType.Warning -> WarningToastIcon(tint)
            AppToastType.Info -> InfoToastIcon(tint)
            AppToastType.Error -> ErrorToastIcon(tint)
        }
    }
}

@Composable
private fun SuccessToastIcon(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height * 0.28f)
        }
        drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
private fun ErrorToastIcon(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        val inset = size.width * 0.22f
        drawLine(tint, Offset(inset, inset), Offset(size.width - inset, size.height - inset), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(size.width - inset, inset), Offset(inset, size.height - inset), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun InfoToastIcon(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawCircle(color = tint, style = Stroke(width = strokeWidth), radius = size.minDimension / 2f - strokeWidth)
        drawCircle(color = tint, radius = strokeWidth * 0.9f, center = Offset(size.width / 2f, size.height * 0.32f))
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, size.height * 0.46f),
            end = Offset(size.width / 2f, size.height * 0.72f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WarningToastIcon(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width / 2f, size.height * 0.12f)
            lineTo(size.width * 0.88f, size.height * 0.86f)
            lineTo(size.width * 0.12f, size.height * 0.86f)
            close()
        }
        drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawCircle(color = tint, radius = strokeWidth * 0.9f, center = Offset(size.width / 2f, size.height * 0.68f))
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, size.height * 0.36f),
            end = Offset(size.width / 2f, size.height * 0.56f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * WSLF-style toast overlay. Mount once inside the app root [Box].
 * Sized only to the toast chip so it does not steal Layout Inspector / pointer hits.
 */
@Composable
fun BoxScope.AppToastHost(
    store: CricketStore,
    bottomInset: Dp = AppToastDefaults.HostBottomClearance,
    modifier: Modifier = Modifier,
) {
    var visibleMessage by remember { mutableStateOf<String?>(null) }
    var visibleType by remember { mutableStateOf(AppToastType.Info) }
    var toastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(store.message, store.messageType) {
        val next = store.message ?: return@LaunchedEffect
        visibleMessage = next
        visibleType = store.messageType
        toastVisible = true
        delay(AppToastDefaults.DurationMs)
        if (store.message == next) {
            toastVisible = false
            delay(AppToastDefaults.ExitAnimationMs)
            if (store.message == next) {
                store.dismissMessage()
            }
        }
    }

    LaunchedEffect(store.message) {
        if (store.message == null && toastVisible) {
            toastVisible = false
            delay(AppToastDefaults.ExitAnimationMs)
            visibleMessage = null
        }
    }

    // Skip composing an overlay node when idle — a fillMaxSize host blocks Layout Inspector.
    if (visibleMessage == null && !toastVisible) return

    AnimatedVisibility(
        visible = toastVisible && visibleMessage != null,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = AppToastDefaults.HorizontalPadding)
            .padding(bottom = bottomInset + AppToastDefaults.BottomPadding)
            .zIndex(40f),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        visibleMessage?.let { message ->
            AppToast(message = message, type = visibleType)
        }
    }
}
