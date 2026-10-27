package com.example.cricketscoring

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform