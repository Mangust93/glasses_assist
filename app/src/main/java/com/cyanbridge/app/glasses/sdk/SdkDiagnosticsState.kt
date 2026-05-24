package com.cyanbridge.app.glasses.sdk

data class SdkDiagnosticsState(
    val aarPresent: Boolean = false,
    val sdkInitialized: Boolean = false,
    val connected: Boolean = false,
    val ready: Boolean = false,
    val battery: Int? = null,
    val isCharging: Boolean? = null,
    val firmwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val wifiFirmwareVersion: String? = null,
    val imageCount: Int? = null,
    val videoCount: Int? = null,
    val recordCount: Int? = null,
    val p2pIp: String? = null,
    val thumbnailsReceived: Int? = null,
    val lastEvent: String? = null,
    val lastError: String? = null
)
