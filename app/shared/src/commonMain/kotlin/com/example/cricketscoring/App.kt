package com.example.cricketscoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cricketscoring.di.rememberAppContainer
import com.example.cricketscoring.navigation.AppNavHost
import com.example.cricketscoring.persist.copyToClipboard
import com.example.cricketscoring.ui.AppToastType
import com.example.cricketscoring.ui.PitchTheme

@Composable
fun App() {
    val container = rememberAppContainer()
    PitchTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppNavHost(container)
            container.store.shareText?.let { text ->
                AlertDialog(
                    onDismissRequest = { container.store.shareText = null },
                    title = { Text("Scorecard") },
                    text = { SelectionContainer { Text(text) } },
                    confirmButton = {
                        TextButton(onClick = {
                            val copied = copyToClipboard(text)
                            container.store.showToast(
                                if (copied) "Scorecard copied." else "Select the scorecard text to copy it.",
                                if (copied) AppToastType.Success else AppToastType.Info,
                            )
                            container.store.shareText = null
                        }) { Text("Copy") }
                    },
                    dismissButton = {
                        TextButton(onClick = { container.store.shareText = null }) { Text("Close") }
                    },
                )
            }
        }
    }
}
