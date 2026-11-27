package com.example.model

data class OnlineStream(
    val id: String,
    val title: String,
    val url: String,
    val category: String,
    val thumbnailUrl: String,
    val isCustom: Boolean = false
)

val defaultStreams = emptyList<OnlineStream>()
