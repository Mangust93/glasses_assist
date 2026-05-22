package com.cyanbridge.app.network.api

import com.cyanbridge.app.network.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface HermesApi {

    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    @Multipart
    @POST("api/voice/ask")
    suspend fun sendVoiceQuery(
        @Part audio: MultipartBody.Part,
        @Part("language") language: RequestBody,
        @Part("sessionId") sessionId: RequestBody?
    ): VoiceAskResponse

    @GET("api/notes")
    suspend fun getNotes(): List<NoteResponse>

    @GET("api/notes/{id}")
    suspend fun getNoteById(@Path("id") id: String): NoteResponse

    @POST("api/notes")
    suspend fun createNote(@Body request: CreateNoteRequest): NoteResponse

    @GET("api/media")
    suspend fun getMedia(): List<MediaResponse>

    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("type") type: RequestBody
    ): MediaResponse
}
