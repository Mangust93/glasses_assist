package com.cyanbridge.app.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.cyanbridge.app.domain.interfaces.NotesRepository
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.interfaces.VoiceInteractionRepository
import com.cyanbridge.app.domain.interfaces.VoiceRecorder
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.domain.model.Note
import com.cyanbridge.app.domain.model.VoiceInteraction
import com.cyanbridge.app.domain.model.VoiceInteractionStatus
import com.cyanbridge.app.glasses.sync.DeviceSyncManager
import com.cyanbridge.app.network.DynamicBaseUrlInterceptor
import com.cyanbridge.app.voice.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class AssistantStage {
    IDLE,
    RECORDING,
    UPLOADING,
    PROCESSING,
    PLAYING,
    ERROR
}

data class AssistantUiState(
    val stage: AssistantStage = AssistantStage.IDLE,
    val glassesStatus: GlassesStatus = GlassesStatus.Idle,
    val glassesMode: GlassesMode = GlassesMode.FAKE,
    val isHermesOnline: Boolean? = null,
    val transcript: String? = null,
    val answer: String? = null,
    val error: String? = null,
    val lastAudioUrl: String? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val voiceRecorder: VoiceRecorder,
    private val hermesApiClient: HermesApiClient,
    private val voiceInteractionRepository: VoiceInteractionRepository,
    private val notesRepository: NotesRepository,
    private val settingsRepository: SettingsRepository,
    private val deviceSyncManager: DeviceSyncManager,
    private val audioPlayer: AudioPlayer,
    private val dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        checkHermesHealth()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.glassesMode.collect { mode ->
                _uiState.value = _uiState.value.copy(glassesMode = mode)
            }
        }
        viewModelScope.launch {
            settingsRepository.hermesBaseUrl.collect { url ->
                dynamicBaseUrlInterceptor.baseUrl = url
            }
        }
        viewModelScope.launch {
            deviceSyncManager.status.collect { status ->
                _uiState.value = _uiState.value.copy(glassesStatus = status)
            }
        }
    }

    fun checkHermesHealth() {
        viewModelScope.launch {
            val result = hermesApiClient.checkHealth()
            _uiState.value = _uiState.value.copy(
                isHermesOnline = result.getOrNull()?.isOnline ?: false
            )
        }
    }

    fun startGlassesScan() {
        viewModelScope.launch {
            deviceSyncManager.startScan()
        }
    }

    fun disconnectGlasses() {
        viewModelScope.launch {
            deviceSyncManager.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Push-to-Talk
    // -------------------------------------------------------------------------

    fun onPttPressed() {
        if (_uiState.value.stage == AssistantStage.IDLE ||
            _uiState.value.stage == AssistantStage.ERROR
        ) {
            startVoiceRecording()
        }
    }

    fun onPttReleased() {
        if (_uiState.value.stage == AssistantStage.RECORDING) {
            stopAndSendVoiceQuery()
        }
    }

    private fun startVoiceRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                stage = AssistantStage.RECORDING,
                transcript = null,
                answer = null,
                error = null
            )
            voiceRecorder.startRecording().onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    stage = AssistantStage.ERROR,
                    error = e.message ?: "Ошибка записи"
                )
            }
        }
    }

    private fun stopAndSendVoiceQuery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stage = AssistantStage.UPLOADING)

            val fileResult = voiceRecorder.stopRecording()
            val audioFile = fileResult.getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    stage = AssistantStage.ERROR,
                    error = "Ошибка остановки записи: ${e.message}"
                )
                return@launch
            }

            val language = settingsRepository.sttLanguage.first()
            _uiState.value = _uiState.value.copy(stage = AssistantStage.PROCESSING)

            val interactionId = UUID.randomUUID().toString()
            voiceInteractionRepository.save(
                VoiceInteraction(
                    id = interactionId,
                    createdAt = Instant.now(),
                    transcript = null,
                    answer = null,
                    audioLocalPath = null,
                    status = VoiceInteractionStatus.UPLOADING,
                    error = null
                )
            )

            val voiceResult = hermesApiClient.sendVoiceQuery(audioFile, language)

            voiceResult.onSuccess { response ->
                voiceInteractionRepository.save(
                    VoiceInteraction(
                        id = response.interactionId.ifBlank { interactionId },
                        createdAt = Instant.now(),
                        transcript = response.transcript,
                        answer = response.answer,
                        audioLocalPath = null,
                        status = VoiceInteractionStatus.DONE,
                        error = null
                    )
                )

                // Persist note if Hermes returned a noteId
                response.noteId?.let { noteId ->
                    notesRepository.save(
                        Note(
                            id = noteId,
                            createdAt = Instant.now(),
                            title = response.transcript.take(60),
                            text = response.transcript,
                            summary = response.answer,
                            tags = emptyList()
                        )
                    )
                }

                _uiState.value = _uiState.value.copy(
                    stage = if (response.audioUrl != null) AssistantStage.PLAYING else AssistantStage.IDLE,
                    transcript = response.transcript,
                    answer = response.answer,
                    lastAudioUrl = response.audioUrl,
                    error = null
                )

                response.audioUrl?.let { url ->
                    playAudioFromUrl(url)
                }
            }.onFailure { e ->
                Timber.e(e, "sendVoiceQuery failed")
                voiceInteractionRepository.updateStatus(
                    interactionId,
                    VoiceInteractionStatus.ERROR,
                    e.message
                )
                _uiState.value = _uiState.value.copy(
                    stage = AssistantStage.ERROR,
                    error = "Ошибка: ${e.message ?: "Нет ответа от сервера"}"
                )
            }
        }
    }

    fun playLastAudio() {
        _uiState.value.lastAudioUrl?.let { url ->
            playAudioFromUrl(url)
        }
    }

    private fun playAudioFromUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                val baseUrl = dynamicBaseUrlInterceptor.baseUrl.trimEnd('/')
                val fullUrl = if (url.startsWith("http")) url else "$baseUrl$url"
                audioPlayer.playFromUrl(fullUrl)
            }.onFailure { e ->
                Timber.e(e, "playAudio failed")
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(stage = AssistantStage.IDLE, error = null)
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.release()
        audioPlayer.release()
    }
}
