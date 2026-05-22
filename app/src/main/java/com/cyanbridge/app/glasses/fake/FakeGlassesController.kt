package com.cyanbridge.app.glasses.fake

import com.cyanbridge.app.domain.interfaces.GlassesController
import com.cyanbridge.app.domain.model.GlassesStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeGlassesController @Inject constructor() : GlassesController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Idle)
    override val status: Flow<GlassesStatus> = _status.asStateFlow()

    private var batteryLevel: Int = 78
    private var batteryTickJob: Job? = null

    override suspend fun startScan() {
        Timber.d("FakeGlassesController: startScan")
        _status.value = GlassesStatus.Scanning
        delay(1_200)
        _status.value = GlassesStatus.Connecting("HeyCyan-Fake")
        delay(900)
        _status.value = GlassesStatus.FakeConnected
        startBatteryTick()
    }

    override suspend fun stopScan() {
        Timber.d("FakeGlassesController: stopScan")
        _status.value = GlassesStatus.Idle
    }

    override suspend fun connect(address: String) {
        // In fake mode, startScan() already simulates full connection flow
        Timber.d("FakeGlassesController: connect($address) — no-op in fake mode")
    }

    override suspend fun disconnect() {
        Timber.d("FakeGlassesController: disconnect")
        batteryTickJob?.cancel()
        batteryTickJob = null
        _status.value = GlassesStatus.Disconnected
    }

    override suspend fun takePhoto() {
        Timber.d("FakeGlassesController: takePhoto — simulated")
    }

    override suspend fun startRecording() {
        Timber.d("FakeGlassesController: startRecording — simulated")
    }

    override suspend fun stopRecording() {
        Timber.d("FakeGlassesController: stopRecording — simulated")
    }

    override suspend fun getBatteryLevel(): Int = batteryLevel

    override fun release() {
        batteryTickJob?.cancel()
        batteryTickJob = null
        _status.value = GlassesStatus.Idle
        Timber.d("FakeGlassesController: released")
    }

    private fun startBatteryTick() {
        batteryTickJob?.cancel()
        batteryTickJob = scope.launch {
            while (true) {
                delay(60_000) // drop 1% per minute for visual feedback
                if (batteryLevel > 10) batteryLevel--
                Timber.d("FakeGlassesController: battery=$batteryLevel%")
            }
        }
    }
}
