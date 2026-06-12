package com.vibecoding.zerobookmultiplatform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform