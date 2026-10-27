package com.example.cricketscoring.persist

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

const val STORE_KEY = "cricket.scoring.v1"

expect fun platformStore(): KeyValueStore

expect fun copyToClipboard(text: String): Boolean
