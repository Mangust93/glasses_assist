package com.cyanbridge.app.ui.screens.media

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.cyanbridge.app.domain.interfaces.MediaRepository
import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.MediaSource
import com.cyanbridge.app.domain.model.MediaType
import com.cyanbridge.app.domain.model.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class MediaUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val uploadProgress: String? = null
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val hermesApiClient: HermesApiClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val mediaItems: StateFlow<List<MediaItem>> = mediaRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    fun uploadFile(uri: Uri, type: MediaType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(uploadProgress = "Загрузка...", error = null)

            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")
                val ext = when (type) {
                    MediaType.PHOTO -> "jpg"
                    MediaType.AUDIO -> "m4a"
                    MediaType.VIDEO -> "mp4"
                }
                val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.$ext")
                tempFile.outputStream().use { out -> inputStream.use { it.copyTo(out) } }

                val localItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    createdAt = Instant.now(),
                    type = type,
                    localPath = tempFile.absolutePath,
                    remoteId = null,
                    syncStatus = SyncStatus.UPLOADING,
                    source = MediaSource.PHONE
                )
                mediaRepository.save(localItem)

                hermesApiClient.uploadMedia(tempFile, type.name.lowercase())
                    .onSuccess { remote ->
                        mediaRepository.save(remote.copy(localPath = tempFile.absolutePath))
                        mediaRepository.delete(localItem.id)
                        Timber.d("Upload OK: ${remote.id}")
                    }
                    .onFailure { e ->
                        mediaRepository.updateSyncStatus(localItem.id, SyncStatus.SYNC_ERROR)
                        throw e
                    }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(uploadProgress = null)
            }.onFailure { e ->
                Timber.e(e, "uploadFile failed")
                _uiState.value = _uiState.value.copy(
                    uploadProgress = null,
                    error = "Ошибка загрузки: ${e.message}"
                )
            }
        }
    }

    fun syncFromHermes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            hermesApiClient.getMedia()
                .onSuccess { items ->
                    items.forEach { mediaRepository.save(it) }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { e ->
                    Timber.e(e, "syncFromHermes failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Ошибка синхронизации: ${e.message}"
                    )
                }
        }
    }
}

