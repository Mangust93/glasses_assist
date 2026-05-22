package com.cyanbridge.app.domain.model

import java.time.Instant

enum class VoiceInteractionStatus {
    RECORDING,
    UPLOADING,
    PROCESSING,
    DONE,
    ERROR
}

data class VoiceInteraction(
    val id: String,
    val createdAt: Instant,
    val transcript: String?,
    val answer: String?,
    val audioLocalPath: String?,
    val status: VoiceInteractionStatus,
    val error: String?
)
