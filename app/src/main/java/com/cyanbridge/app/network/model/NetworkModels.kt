package com.cyanbridge.app.network.model

import com.google.gson.annotations.SerializedName

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String?
)

data class VoiceAskResponse(
    @SerializedName("interactionId") val interactionId: String,
    @SerializedName("transcript") val transcript: String,
    @SerializedName("answer") val answer: String,
    @SerializedName("audioUrl") val audioUrl: String?,
    @SerializedName("noteId") val noteId: String?,
    @SerializedName("status") val status: String,
    @SerializedName("error") val error: String?
)

data class NoteResponse(
    @SerializedName("id") val id: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String,
    @SerializedName("summary") val summary: String?,
    @SerializedName("tags") val tags: List<String>
)

data class MediaResponse(
    @SerializedName("id") val id: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("type") val type: String,
    @SerializedName("url") val url: String?,
    @SerializedName("syncStatus") val syncStatus: String
)

data class CreateNoteRequest(
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String
)
