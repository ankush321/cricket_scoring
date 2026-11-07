package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Shared content chrome for feature screens.
 *
 * Apply page padding / background here — not on [App] or Scaffold —
 * so toolbar and bottom nav stay edge-to-edge.
 */
@Composable
fun CommonBox(
    modifier: Modifier = Modifier,
    background: Color = PitchBlack,
    horizontalPadding: Dp = AppScreenDefaults.Horizontal,
    verticalPadding: Dp = AppScreenDefaults.VerticalLoose,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun CommonColumn(
    modifier: Modifier = Modifier,
    background: Color = PitchBlack,
    horizontalPadding: Dp = AppScreenDefaults.Horizontal,
    verticalPadding: Dp = AppScreenDefaults.VerticalLoose,
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppScreenDefaults.SectionGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    CommonBox(
        modifier = modifier,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** @deprecated Prefer [CommonBox]. */
@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    background: Color = PitchBlack,
    withStatusBars: Boolean = false,
    horizontalPadding: Dp = AppScreenDefaults.Horizontal,
    verticalPadding: Dp = AppScreenDefaults.VerticalLoose,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    CommonBox(
        modifier = modifier,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** @deprecated Prefer [CommonColumn]. */
@Composable
fun AppScreenColumn(
    modifier: Modifier = Modifier,
    background: Color = PitchBlack,
    withStatusBars: Boolean = false,
    horizontalPadding: Dp = AppScreenDefaults.Horizontal,
    verticalPadding: Dp = AppScreenDefaults.VerticalLoose,
    scrollable: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppScreenDefaults.SectionGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    CommonColumn(
        modifier = modifier,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        scrollable = scrollable,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
