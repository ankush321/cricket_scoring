package com.example.cricketscoring.persist

import kotlin.js.ExperimentalWasmJsInterop

private val memory = mutableMapOf<String, String>()

@OptIn(ExperimentalWasmJsInterop::class)
private fun storageGet(key: String): String? = js("localStorage.getItem(key)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun storageSet(key: String, value: String) {
    js("localStorage.setItem(key, value)")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun storageCopy(text: String) {
    js("navigator.clipboard.writeText(text)")
}

actual fun platformStore(): KeyValueStore = object : KeyValueStore {
    override fun getString(key: String): String? = runCatching { storageGet(key) }.getOrElse { memory[key] }

    override fun putString(key: String, value: String) {
        memory[key] = value
        runCatching { storageSet(key, value) }
    }
}

actual fun copyToClipboard(text: String): Boolean = runCatching {
    storageCopy(text)
    true
}.getOrDefault(false)
