package com.cyanbridge.app.domain.model

import java.time.Instant

data class Note(
    val id: String,
    val createdAt: Instant,
    val title: String,
    val text: String,
    val summary: String?,
    val tags: List<String>
)
