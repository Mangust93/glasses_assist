package com.cyanbridge.app.network

import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.cyanbridge.app.domain.model.*
import com.cyanbridge.app.network.api.HermesApi
import com.cyanbridge.app.network.model.CreateNoteRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesApiClientImpl @Inject constructor(
    private val api: HermesApi
) : HermesApiClient {

    override suspend fun checkHealth(): Result<HermesHealth> = runCatching {
        val response = api.checkHealth()
        HermesHealth(
            status = response.status,
            version = response.version,
            isOnline = response.status == "ok" || response.status == "healthy"
        )
    }.onFailure { Timber.e(it, "checkHealth failed") }

    override suspend fun sendVoiceQuery(
        audioFile: File,
        language: String,
        sessionId: String?
    ): Result<VoiceResponse> = runCatching {
        val requestFile = audioFile.asRequestBody("audio/mpeg".toMediaType())
        val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
        val languagePart = language.toRequestBody("text/plain".toMediaType())
        val sessionPart = sessionId?.toRequestBody("text/plain".toMediaType())

        val response = api.sendVoiceQuery(audioPart, languagePart, sessionPart)
        VoiceResponse(
            interactionId = response.interactionId,
            transcript = response.transcript,
            answer = response.answer,
            audioUrl = response.audioUrl,
            noteId = response.noteId,
            status = response.status,
            error = response.error
        )
    }.onFailure { Timber.e(it, "sendVoiceQuery failed") }

    override suspend fun getNotes(): Result<List<Note>> = runCatching {
        api.getNotes().map { n ->
            Note(
                id = n.id,
                createdAt = runCatching { Instant.parse(n.createdAt) }.getOrElse { Instant.now() },
                title = n.title,
                text = n.text,
                summary = n.summary,
                tags = n.tags
            )
        }
    }.onFailure { Timber.e(it, "getNotes failed") }

    override suspend fun getNoteById(id: String): Result<Note> = runCatching {
        val n = api.getNoteById(id)
        Note(
            id = n.id,
            createdAt = runCatching { Instant.parse(n.createdAt) }.getOrElse { Instant.now() },
            title = n.title,
            text = n.text,
            summary = n.summary,
            tags = n.tags
        )
    }.onFailure { Timber.e(it, "getNoteById failed") }

    override suspend fun createNote(title: String, text: String): Result<Note> = runCatching {
        val n = api.createNote(CreateNoteRequest(title, text))
        Note(
            id = n.id,
            createdAt = runCatching { Instant.parse(n.createdAt) }.getOrElse { Instant.now() },
            title = n.title,
            text = n.text,
            summary = n.summary,
            tags = n.tags
        )
    }.onFailure { Timber.e(it, "createNote failed") }

    override suspend fun getMedia(): Result<List<MediaItem>> = runCatching {
        api.getMedia().map { m ->
            MediaItem(
                id = m.id,
                createdAt = runCatching { Instant.parse(m.createdAt) }.getOrElse { Instant.now() },
                type = runCatching { MediaType.valueOf(m.type.uppercase()) }.getOrDefault(MediaType.PHOTO),
                localPath = null,
                remoteId = m.id,
                syncStatus = SyncStatus.SYNCED,
                source = MediaSource.GLASSES
            )
        }
    }.onFailure { Timber.e(it, "getMedia failed") }

    override suspend fun uploadMedia(file: File, type: String): Result<MediaItem> = runCatching {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val typePart = type.toRequestBody("text/plain".toMediaType())
        val m = api.uploadMedia(filePart, typePart)
        MediaItem(
            id = m.id,
            createdAt = runCatching { Instant.parse(m.createdAt) }.getOrElse { Instant.now() },
            type = runCatching { MediaType.valueOf(m.type.uppercase()) }.getOrDefault(MediaType.PHOTO),
            localPath = file.absolutePath,
            remoteId = m.id,
            syncStatus = SyncStatus.SYNCED,
            source = MediaSource.PHONE
        )
    }.onFailure { Timber.e(it, "uploadMedia failed") }

    override suspend fun downloadAudio(audioUrl: String, destFile: File): Result<File> = runCatching {
        // Audio download is handled directly via OkHttp outside of Retrofit API
        // because audioUrl may be a full URL (not just a path)
        throw UnsupportedOperationException("Use OkHttpClient directly for audio download")
    }
}
