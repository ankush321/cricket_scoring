package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle

@Composable
fun HomeScreen(store: CricketStore) {
    val matches = store.data.matches
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading(
            "Cricket Scoring",
            "Choose a style, open fixtures, then start or resume a match.",
        )
        CricketStyle.entries.forEach { style ->
            val count = matches.count { it.style == style }
            SurfaceCard(
                elevation = ElevationHigh,
                accent = style.accent(),
                onClick = { store.openStyle(style, fromHome = true) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StyleMark(style)
                    Column(Modifier.weight(1f)) {
                        Text(styleTitle(style), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(styleSummary(style), color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(style.accent().copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (count == 0) "No fixtures yet" else "$count fixtures",
                                color = style.accent(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("View fixtures", { store.openStyle(style, fromHome = true) }, Modifier.fillMaxWidth())
            }
        }
    }
}
