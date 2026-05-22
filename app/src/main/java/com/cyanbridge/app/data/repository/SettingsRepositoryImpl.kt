package com.cyanbridge.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.model.GlassesMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        val HERMES_BASE_URL = stringPreferencesKey("hermes_base_url")
        val GLASSES_MODE = stringPreferencesKey("glasses_mode")
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val LAST_CONNECTED_DEVICE_ADDRESS = stringPreferencesKey("last_connected_device_address")
    }

    override val hermesBaseUrl: Flow<String> = dataStore.data.map {
        it[Keys.HERMES_BASE_URL] ?: "http://192.168.1.100:8000"
    }

    override val glassesMode: Flow<GlassesMode> = dataStore.data.map {
        val raw = it[Keys.GLASSES_MODE] ?: GlassesMode.FAKE.name
        runCatching { GlassesMode.valueOf(raw) }.getOrDefault(GlassesMode.FAKE)
    }

    override val sttLanguage: Flow<String> = dataStore.data.map {
        it[Keys.STT_LANGUAGE] ?: "ru"
    }

    override val ttsVoice: Flow<String> = dataStore.data.map {
        it[Keys.TTS_VOICE] ?: "ru-RU-SvetlanaNeural"
    }

    override val isDebugMode: Flow<Boolean> = dataStore.data.map {
        it[Keys.DEBUG_MODE] ?: false
    }

    override val lastConnectedDeviceAddress: Flow<String?> = dataStore.data.map {
        it[Keys.LAST_CONNECTED_DEVICE_ADDRESS]
    }

    override suspend fun setHermesBaseUrl(url: String) {
        dataStore.edit { it[Keys.HERMES_BASE_URL] = url.trimEnd('/') }
    }

    override suspend fun setGlassesMode(mode: GlassesMode) {
        dataStore.edit { it[Keys.GLASSES_MODE] = mode.name }
    }

    override suspend fun setSttLanguage(language: String) {
        dataStore.edit { it[Keys.STT_LANGUAGE] = language }
    }

    override suspend fun setTtsVoice(voice: String) {
        dataStore.edit { it[Keys.TTS_VOICE] = voice }
    }

    override suspend fun setDebugMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }

    override suspend fun setLastConnectedDeviceAddress(address: String?) {
        dataStore.edit {
            if (address != null) it[Keys.LAST_CONNECTED_DEVICE_ADDRESS] = address
            else it.remove(Keys.LAST_CONNECTED_DEVICE_ADDRESS)
        }
    }
}
