package com.example.cricketscoring.persist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences

object AndroidStorage {
    var preferences: SharedPreferences? = null
    var context: Context? = null
}

actual fun platformStore(): KeyValueStore = object : KeyValueStore {
    override fun getString(key: String): String? = AndroidStorage.preferences?.getString(key, null)

    override fun putString(key: String, value: String) {
        AndroidStorage.preferences?.edit()?.putString(key, value)?.apply()
    }
}

actual fun copyToClipboard(text: String): Boolean {
    val context = AndroidStorage.context ?: return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("scorecard", text))
    return true
}
