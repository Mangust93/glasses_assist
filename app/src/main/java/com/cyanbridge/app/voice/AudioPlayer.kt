package com.cyanbridge.app.voice

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackState { IDLE, PLAYING, PAUSED, ERROR }

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playFromUrl(url: String) {
        Timber.d("AudioPlayer: playing URL $url")
        buildPlayer()
        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            play()
        }
        _playbackState.value = PlaybackState.PLAYING
    }

    fun playFromFile(file: File) {
        Timber.d("AudioPlayer: playing file ${file.name}")
        buildPlayer()
        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            play()
        }
        _playbackState.value = PlaybackState.PLAYING
    }

    fun pause() {
        player?.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    fun resume() {
        player?.play()
        _playbackState.value = PlaybackState.PLAYING
    }

    fun stop() {
        player?.stop()
        _playbackState.value = PlaybackState.IDLE
    }

    fun release() {
        player?.release()
        player = null
        _playbackState.value = PlaybackState.IDLE
    }

    val isPlaying: Boolean get() = player?.isPlaying == true

    private fun buildPlayer() {
        player?.release()
        player = ExoPlayer.Builder(context).build()
    }
}
