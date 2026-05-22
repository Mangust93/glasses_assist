package com.cyanbridge.app.domain.model

data class HermesHealth(
    val status: String,
    val version: String?,
    val isOnline: Boolean
)
