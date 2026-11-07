package com.example.cricketscoring.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.cricketscoring.persist.CodecRepository
import com.example.cricketscoring.persist.STORE_KEY
import com.example.cricketscoring.persist.platformStore
import com.example.cricketscoring.ui.CricketStore

class AppContainer(
    val store: CricketStore,
)

@Composable
fun rememberAppContainer(): AppContainer = remember {
    val storage = platformStore()
    AppContainer(
        store = CricketStore(
            CodecRepository(
                read = { storage.getString(STORE_KEY) },
                write = { storage.putString(STORE_KEY, it) },
            ),
        ),
    )
}
