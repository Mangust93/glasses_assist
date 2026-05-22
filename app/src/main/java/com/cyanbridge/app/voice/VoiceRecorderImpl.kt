package com.cyanbridge.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.cyanbridge.app.domain.interfaces.RecorderState
import com.cyanbridge.app.domain.interfaces.VoiceRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: Flow<RecorderState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    override val amplitudeFlow: Flow<Int> = _amplitude.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "recordings").also { it.mkdirs() }
            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(128_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _state.value = RecorderState.Recording
            startAmplitudePolling()
            Timber.d("VoiceRecorder: started → ${file.name}")
        }.onFailure { e ->
            Timber.e(e, "VoiceRecorder: startRecording failed")
            _state.value = RecorderState.Error(e.message ?: "Recording failed")
        }
    }

    override suspend fun stopRecording(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            amplitudeJob?.cancel()
            amplitudeJob = null
            _amplitude.value = 0

            val recorder = mediaRecorder ?: throw IllegalStateException("No active recording")
            recorder.stop()
            recorder.release()
            mediaRecorder = null

            _state.value = RecorderState.Idle
            val file = outputFile ?: throw IllegalStateException("Output file missing")
            Timber.d("VoiceRecorder: stopped → ${file.name} (${file.length()} bytes)")
            file
        }.onFailure { e ->
            Timber.e(e, "VoiceRecorder: stopRecording failed")
            _state.value = RecorderState.Error(e.message ?: "Stop failed")
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    override fun release() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        mediaRecorder?.release()
        mediaRecorder = null
        _state.value = RecorderState.Idle
        _amplitude.value = 0
    }

    private fun startAmplitudePolling() {
        amplitudeJob = scope.launch {
            while (true) {
                delay(100)
                val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                _amplitude.value = amp
            }
        }
    }
}
