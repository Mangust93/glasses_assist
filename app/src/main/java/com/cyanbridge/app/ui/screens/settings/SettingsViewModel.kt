package com.cyanbridge.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.cyanbridge.app.domain.interfaces.NotesRepository
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.interfaces.VoiceInteractionRepository
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.network.DynamicBaseUrlInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class HealthCheckState { IDLE, CHECKING, OK, ERROR }

data class SettingsUiState(
    val healthCheckState: HealthCheckState = HealthCheckState.IDLE,
    val healthMessage: String? = null,
    val isClearingHistory: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hermesApiClient: HermesApiClient,
    private val voiceInteractionRepository: VoiceInteractionRepository,
    private val notesRepository: NotesRepository,
    private val dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor
) : ViewModel() {

    val hermesBaseUrl: StateFlow<String> = settingsRepository.hermesBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "http://192.168.1.100:8000")

    val glassesMode: StateFlow<GlassesMode> = settingsRepository.glassesMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlassesMode.FAKE)

    val sttLanguage: StateFlow<String> = settingsRepository.sttLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ru")

    val ttsVoice: StateFlow<String> = settingsRepository.ttsVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ru-RU-SvetlanaNeural")

    val isDebugMode: StateFlow<Boolean> = settingsRepository.isDebugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setHermesBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setHermesBaseUrl(url)
            dynamicBaseUrlInterceptor.baseUrl = url.trimEnd('/')
        }
    }

    fun setGlassesMode(mode: GlassesMode) {
        viewModelScope.launch { settingsRepository.setGlassesMode(mode) }
    }

    fun setSttLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setSttLanguage(language) }
    }

    fun setTtsVoice(voice: String) {
        viewModelScope.launch { settingsRepository.setTtsVoice(voice) }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDebugMode(enabled) }
    }

    fun checkHermesConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                healthCheckState = HealthCheckState.CHECKING,
                healthMessage = null
            )
            hermesApiClient.checkHealth()
                .onSuccess { health ->
                    _uiState.value = _uiState.value.copy(
                        healthCheckState = HealthCheckState.OK,
                        healthMessage = "Hermes онлайн · версия ${health.version ?: "n/a"}"
                    )
                }
                .onFailure { e ->
                    Timber.e(e, "checkHermesConnection failed")
                    _uiState.value = _uiState.value.copy(
                        healthCheckState = HealthCheckState.ERROR,
                        healthMessage = "Недоступен: ${e.message}"
                    )
                }
        }
    }

    fun clearLocalHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingHistory = true)
            voiceInteractionRepository.deleteAll()
            notesRepository.deleteAll()
            _uiState.value = _uiState.value.copy(isClearingHistory = false)
        }
    }
}
