package com.cyanbridge.app.glasses.sync

import com.cyanbridge.app.domain.interfaces.GlassesController
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.glasses.ble.NativeBleGlassesController
import com.cyanbridge.app.glasses.fake.FakeGlassesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central device sync layer.
 *
 * Responsibilities:
 * - Keeps a single [status] flow that reflects whichever [GlassesController] is active.
 * - Switches controllers when [GlassesMode] changes in [SettingsRepository].
 * - Auto-starts [FakeGlassesController] on first launch in FAKE mode.
 * - Auto-reconnects to the last BLE address on app start in NATIVE_BLE mode.
 * - Schedules reconnect after unexpected BLE disconnect (5 s cooldown).
 * - Persists the last successfully initiated device address to [SettingsRepository].
 */
@Singleton
class DeviceSyncManager @Inject constructor(
    private val fakeController: FakeGlassesController,
    private val nativeController: NativeBleGlassesController,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Idle)
    val status: Flow<GlassesStatus> = _status.asStateFlow()

    private var currentMode: GlassesMode = GlassesMode.FAKE
    private var pendingReconnectAddress: String? = null

    private var statusObserverJob: Job? = null
    private var reconnectJob: Job? = null
    private var initJob: Job? = null
    private var firstEmission = true

    init {
        scope.launch {
            settingsRepository.glassesMode.collect { mode ->
                switchToMode(mode)
            }
        }
    }

    val activeController: GlassesController
        get() = if (currentMode == GlassesMode.FAKE) fakeController else nativeController

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Trigger a scan using the currently active controller. */
    suspend fun startScan() {
        reconnectJob?.cancel()
        runCatching { activeController.startScan() }
            .onFailure { Timber.e(it, "DeviceSyncManager: startScan failed") }
    }

    /**
     * Connect to a specific BLE device address (Native mode only).
     * Saves the address so it can be used for auto-reconnect.
     */
    suspend fun connectToDevice(address: String) {
        if (currentMode != GlassesMode.NATIVE_BLE) return
        reconnectJob?.cancel()
        pendingReconnectAddress = address
        settingsRepository.setLastConnectedDeviceAddress(address)
        runCatching { nativeController.connect(address) }
            .onFailure { Timber.e(it, "DeviceSyncManager: connectToDevice failed") }
    }

    /** User-initiated disconnect. Clears the saved address so auto-reconnect won't fire. */
    suspend fun disconnect() {
        reconnectJob?.cancel()
        pendingReconnectAddress = null
        settingsRepository.setLastConnectedDeviceAddress(null)
        runCatching { activeController.disconnect() }
            .onFailure { Timber.e(it, "DeviceSyncManager: disconnect failed") }
    }

    /** Call from Application.onTerminate or a lifecycle observer to clean up resources. */
    fun release() {
        reconnectJob?.cancel()
        statusObserverJob?.cancel()
        initJob?.cancel()
        runCatching { fakeController.release() }
        runCatching { nativeController.release() }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private suspend fun switchToMode(newMode: GlassesMode) {
        if (!firstEmission && newMode == currentMode) return

        val wasInitialized = !firstEmission
        firstEmission = false

        reconnectJob?.cancel()
        statusObserverJob?.cancel()
        initJob?.cancel()

        if (wasInitialized) {
            val oldController: GlassesController =
                if (currentMode == GlassesMode.FAKE) fakeController else nativeController
            runCatching { oldController.release() }
                .onFailure { Timber.e(it, "DeviceSyncManager: release of old controller failed") }
        }

        currentMode = newMode
        Timber.d("DeviceSyncManager: switching to $newMode")

        val controllerFlow =
            if (newMode == GlassesMode.FAKE) fakeController.status else nativeController.status

        statusObserverJob = scope.launch {
            controllerFlow.collect { s ->
                _status.value = s
                if (s is GlassesStatus.Connected && currentMode == GlassesMode.NATIVE_BLE) {
                    // Persist address whenever a native connection is established
                    nativeController.lastConnectedAddress?.let { addr ->
                        pendingReconnectAddress = addr
                        settingsRepository.setLastConnectedDeviceAddress(addr)
                    }
                }
                if (s is GlassesStatus.Disconnected && currentMode == GlassesMode.NATIVE_BLE) {
                    scheduleReconnect()
                }
            }
        }

        initJob = scope.launch {
            when (newMode) {
                GlassesMode.FAKE -> {
                    runCatching { fakeController.startScan() }
                        .onFailure { Timber.e(it, "DeviceSyncManager: fake startScan failed") }
                }
                GlassesMode.NATIVE_BLE -> {
                    val savedAddress = settingsRepository.lastConnectedDeviceAddress.first()
                    if (savedAddress != null) {
                        Timber.d("DeviceSyncManager: auto-reconnecting to $savedAddress")
                        pendingReconnectAddress = savedAddress
                        runCatching { nativeController.connect(savedAddress) }
                            .onFailure { Timber.e(it, "DeviceSyncManager: auto-reconnect failed") }
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val address = pendingReconnectAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Timber.d("DeviceSyncManager: reconnect scheduled in ${RECONNECT_DELAY_MS}ms to $address")
            delay(RECONNECT_DELAY_MS)
            if (currentMode == GlassesMode.NATIVE_BLE && _status.value is GlassesStatus.Disconnected) {
                Timber.d("DeviceSyncManager: executing reconnect to $address")
                runCatching { nativeController.connect(address) }
                    .onFailure { Timber.e(it, "DeviceSyncManager: scheduled reconnect failed") }
            }
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
