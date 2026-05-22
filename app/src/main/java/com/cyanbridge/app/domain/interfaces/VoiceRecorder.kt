package com.cyanbridge.app.domain.interfaces

import kotlinx.coroutines.flow.Flow
import java.io.File

sealed class RecorderState {
    object Idle : RecorderState()
    object Recording : RecorderState()
    data class Error(val message: String) : RecorderState()
}

interface VoiceRecorder {
    val state: Flow<RecorderState>
    val amplitudeFlow: Flow<Int>

    suspend fun startRecording(): Result<Unit>
    suspend fun stopRecording(): Result<File>
    fun release()
}
