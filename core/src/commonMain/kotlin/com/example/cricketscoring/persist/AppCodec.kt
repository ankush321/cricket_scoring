package com.example.cricketscoring.persist

import com.example.cricketscoring.model.AppData
import com.example.cricketscoring.model.Match
import kotlinx.serialization.json.Json

object AppJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodeMatch(match: Match): String = json.encodeToString(Match.serializer(), match)
    fun decodeMatch(text: String): Match = json.decodeFromString(Match.serializer(), text)
    fun encodeApp(data: AppData): String = json.encodeToString(AppData.serializer(), data)
    fun decodeApp(text: String): AppData = json.decodeFromString(AppData.serializer(), text)
}

interface AppRepository {
    fun load(): AppData
    fun save(data: AppData)
}

class MemoryRepository(initial: AppData = AppData()) : AppRepository {
    private var current = initial
    override fun load(): AppData = current
    override fun save(data: AppData) {
        current = data
    }
}

class CodecRepository(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : AppRepository {
    override fun load(): AppData {
        val raw = read() ?: return AppData()
        return runCatching { AppJson.decodeApp(raw) }.getOrElse { AppData() }
    }

    override fun save(data: AppData) {
        write(AppJson.encodeApp(data))
    }
}
