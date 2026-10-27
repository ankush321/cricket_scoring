package com.example.cricketscoring.persist

import platform.Foundation.NSUserDefaults
import platform.UIKit.UIPasteboard

actual fun platformStore(): KeyValueStore = object : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}

actual fun copyToClipboard(text: String): Boolean {
    UIPasteboard.generalPasteboard.string = text
    return true
}
