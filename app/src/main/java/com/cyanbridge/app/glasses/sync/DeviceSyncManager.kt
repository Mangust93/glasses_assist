package com.cyanbridge.app.glasses.sync

import com.cyanbridge.app.domain.interfaces.GlassesController
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.glasses.ble.NativeBleGlassesController
import com.cyanbridge.app.glasses.fake.FakeGlassesController
import com.cyanbridge.app.glasses.sdk.HeyCyanSdkGlassesController
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
 * Central device sync layer. Supports three modes:
 *   [GlassesMode.FAKE]                 — FakeGlassesController (UI testing)
 *   [GlassesMode.NATIVE_BLE_DIAGNOSTIC] — NativeBleGlassesController (BLE diagnostics / fallback)
 *   [GlassesMode.HEYCYAN_SDK]           — HeyCyanSdkGlassesController (real device, confirmed protocol)
 */
@Singleton
class DeviceSyncManager @Inject constructor(
    private val fakeController: FakeGlassesController,
    private val nativeController: NativeBleGlassesController,
    private val sdkController: HeyCyanSdkGlassesController,
    private val settingsRepository: SettingsRepository
) {
    private val managerJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + managerJob)

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Idle)
    val status: Flow<GlassesStatus> = _status.asStateFlow()

    private var currentMode: GlassesMode = GlassesMode.FAKE
    private var pendingReconnectAddress: String? = null

    private var statusObserverJob: Job? = null
    private var reconnectJob: Job? = null
    private var modeObserverJob: Job? = null
    private var initJob: Job? = null
    private var firstEmission = true

    init {
        modeObserverJob = scope.launch {
            settingsRepository.glassesMode.collect { mode ->
                switchToMode(mode)
            }
        }
    }

    val activeController: GlassesController
        get() = controllerFor(currentMode)

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    suspend fun startScan() {
        reconnectJob?.cancel()
        runCatching { activeController.startScan() }
            .onFailure { Timber.e(it, "DeviceSyncManager: startScan failed") }
    }

    /**
     * Connect to a specific BLE device address (NATIVE_BLE_DIAGNOSTIC or HEYCYAN_SDK only).
     * Address is persisted only after the controller reports a successful connection.
     */
    suspend fun connectToDevice(address: String) {
        if (currentMode == GlassesMode.FAKE) return
        reconnectJob?.cancel()
        pendingReconnectAddress = address
        runCatching { activeController.connect(address) }
            .onFailure { Timber.e(it, "DeviceSyncManager: connectToDevice failed") }
    }

    /** User-initiated disconnect. Clears saved address to prevent auto-reconnect. */
    suspend fun disconnect() {
        reconnectJob?.cancel()
        pendingReconnectAddress = null
        settingsRepository.setLastConnectedDeviceAddress(null)
        runCatching { activeController.disconnect() }
            .onFailure { Timber.e(it, "DeviceSyncManager: disconnect failed") }
    }

    fun release() {
        reconnectJob?.cancel()
        statusObserverJob?.cancel()
        modeObserverJob?.cancel()
        initJob?.cancel()
        managerJob.cancel()
        runCatching { fakeController.release() }
        runCatching { nativeController.release() }
        runCatching { sdkController.release() }
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
            runCatching { controllerFor(currentMode).release() }
                .onFailure { Timber.e(it, "DeviceSyncManager: release of old controller failed") }
        }

        currentMode = newMode
        Timber.d("DeviceSyncManager: switching to $newMode")

        statusObserverJob = scope.launch {
            controllerFor(newMode).status.collect { s ->
                _status.value = s
                if (s is GlassesStatus.Connected && currentMode != GlassesMode.FAKE) {
                    lastAddressFor(currentMode)?.let { addr ->
                        pendingReconnectAddress = addr
                        settingsRepository.setLastConnectedDeviceAddress(addr)
                    }
                }
                if (s is GlassesStatus.Disconnected && currentMode != GlassesMode.FAKE) {
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
                GlassesMode.NATIVE_BLE_DIAGNOSTIC -> {
                    val savedAddress = settingsRepository.lastConnectedDeviceAddress.first()
                    if (savedAddress != null) {
                        Timber.d("DeviceSyncManager: NATIVE_BLE_DIAGNOSTIC auto-reconnecting to $savedAddress")
                        pendingReconnectAddress = savedAddress
                        runCatching { nativeController.connect(savedAddress) }
                            .onFailure { Timber.e(it, "DeviceSyncManager: native auto-reconnect failed") }
                    }
                }
                GlassesMode.HEYCYAN_SDK -> {
                    val savedAddress = settingsRepository.lastConnectedDeviceAddress.first()
                    if (savedAddress != null) {
                        Timber.d("DeviceSyncManager: HEYCYAN_SDK auto-reconnecting to $savedAddress")
                        pendingReconnectAddress = savedAddress
                        runCatching { sdkController.connect(savedAddress) }
                            .onFailure { Timber.e(it, "DeviceSyncManager: sdk auto-reconnect failed") }
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val address = pendingReconnectAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Timber.d("DeviceSyncManager: reconnect in ${RECONNECT_DELAY_MS}ms to $address")
            delay(RECONNECT_DELAY_MS)
            if (currentMode != GlassesMode.FAKE && _status.value is GlassesStatus.Disconnected) {
                Timber.d("DeviceSyncManager: executing reconnect to $address")
                runCatching { activeController.connect(address) }
                    .onFailure { Timber.e(it, "DeviceSyncManager: scheduled reconnect failed") }
            }
        }
    }

    private fun controllerFor(mode: GlassesMode): GlassesController = when (mode) {
        GlassesMode.FAKE -> fakeController
        GlassesMode.NATIVE_BLE_DIAGNOSTIC -> nativeController
        GlassesMode.HEYCYAN_SDK -> sdkController
    }

    private fun lastAddressFor(mode: GlassesMode): String? = when (mode) {
        GlassesMode.NATIVE_BLE_DIAGNOSTIC -> nativeController.lastConnectedAddress
        GlassesMode.HEYCYAN_SDK -> sdkController.lastConnectedAddress
        GlassesMode.FAKE -> null
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
