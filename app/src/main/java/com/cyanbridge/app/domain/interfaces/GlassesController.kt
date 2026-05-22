package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.GlassesStatus
import kotlinx.coroutines.flow.Flow

interface GlassesController {
    val status: Flow<GlassesStatus>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(address: String)
    suspend fun disconnect()

    suspend fun takePhoto()
    suspend fun startRecording()
    suspend fun stopRecording()
    suspend fun getBatteryLevel(): Int?

    fun release()
}
