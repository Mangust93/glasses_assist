package com.cyanbridge.app.domain.model

data class VoiceResponse(
    val interactionId: String,
    val transcript: String,
    val answer: String,
    val audioUrl: String?,
    val noteId: String?,
    val status: String,
    val error: String?
)
