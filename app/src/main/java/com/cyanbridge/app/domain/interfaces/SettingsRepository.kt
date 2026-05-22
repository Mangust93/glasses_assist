package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.GlassesMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val hermesBaseUrl: Flow<String>
    val glassesMode: Flow<GlassesMode>
    val sttLanguage: Flow<String>
    val ttsVoice: Flow<String>
    val isDebugMode: Flow<Boolean>
    val lastConnectedDeviceAddress: Flow<String?>

    suspend fun setHermesBaseUrl(url: String)
    suspend fun setGlassesMode(mode: GlassesMode)
    suspend fun setSttLanguage(language: String)
    suspend fun setTtsVoice(voice: String)
    suspend fun setDebugMode(enabled: Boolean)
    suspend fun setLastConnectedDeviceAddress(address: String?)
}
