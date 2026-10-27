package com.example.cricketscoring.persist

import web.storage.localStorage

actual fun platformStore(): KeyValueStore = object : KeyValueStore {
    override fun getString(key: String): String? = localStorage.getItem(key)

    override fun putString(key: String, value: String) {
        localStorage.setItem(key, value)
    }
}

actual fun copyToClipboard(text: String): Boolean = runCatching {
    js("navigator.clipboard.writeText(text)")
    true
}.getOrDefault(false)
