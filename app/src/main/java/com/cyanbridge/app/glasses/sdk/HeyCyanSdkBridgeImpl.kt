package com.cyanbridge.app.glasses.sdk

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.OnGattEventCallback
import com.oudmon.ble.base.bluetooth.SDKInit
import com.oudmon.ble.base.communication.ILargeDataImageResponse
import com.oudmon.ble.base.communication.ILargeDataResponse
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.BatteryResponse
import com.oudmon.ble.base.communication.bigData.resp.DeviceInfoResponse
import com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse
import com.oudmon.ble.base.communication.req.GlassModelControlReq
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real SDK implementation of [HeyCyanSdkBridge] using glasses_sdk_20250723_v01.aar.
 *
 * SDK initialisation order:
 *   1. SDKInit.getInstance().setSDKType(SDK_TYPE_QC)
 *   2. BleBaseControl.getInstance(context).setmContext(context)
 *   3. BleOperateManager.getInstance(application) creates singleton with Application
 *   4. BleOperateManager.init() and enableUUID()
 *   5. BleOperateManager.setCallback(OnGattEventCallback)
 *   6. LargeDataHandler.getInstance().initEnable()
 *   7. LargeDataHandler.getInstance().addBatteryCallBack(key, callback)
 *
 * Connection state is tracked via polling BleOperateManager.isConnected()/isReady()
 * after connectDirectly() because OnGattEventCallback only delivers data frames, not
 * connection state events.
 *
 * Capture and Wi-Fi commands are disabled because request mapping has NOT been verified
 * on real CY 01_24E5 hardware. The labeled media-count diagnostic remains experimental.
 */
@Singleton
class HeyCyanSdkBridgeImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HeyCyanSdkBridge {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _diagnostics = MutableStateFlow(SdkDiagnosticsState(aarPresent = true))
    override val diagnosticsState: StateFlow<SdkDiagnosticsState> = _diagnostics.asStateFlow()

    private var sdkInitialized = false
    private var connectionPollJob: Job? = null
    private var batteryTimeoutJob: Job? = null
    private var deviceInfoTimeoutJob: Job? = null
    private var mediaControlTimeoutJob: Job? = null
    private var thumbnailsTimeoutJob: Job? = null

    // -------------------------------------------------------------------------
    // Availability
    // -------------------------------------------------------------------------

    override fun isAarAvailable(): Boolean = true

    override fun isConnected(): Boolean =
        runCatching { BleOperateManager.getInstance()?.isConnected() == true }.getOrDefault(false)

    override fun isReady(): Boolean =
        runCatching { BleOperateManager.getInstance()?.isReady() == true }.getOrDefault(false)

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    override suspend fun initSdk() {
        if (sdkInitialized) {
            recordEvent("SDK already initialized")
            return
        }
        try {
            SDKInit.getInstance().setSDKType(SDKInit.SDK_TYPE_QC)
            BleBaseControl.getInstance(context).setmContext(context)
            val application = context.applicationContext as Application
            val manager = BleOperateManager.getInstance(application)
            manager.init()
            manager.enableUUID()
            manager.setCallback(object : OnGattEventCallback {
                override fun onReceivedData(address: String, data: ByteArray) {
                    Timber.d("HeyCyanSDK: data from $address [${data.size}B]")
                    recordEvent("Data: ${data.size}B from $address")
                }
            })
            LargeDataHandler.getInstance().initEnable()
            LargeDataHandler.getInstance().addBatteryCallBack(BATTERY_KEY, batteryCallback)
            sdkInitialized = true
            recordEvent("SDK initialized (QC type)") { it.copy(sdkInitialized = true) }
            Timber.d("HeyCyanSDK: initialized")
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: init failed")
            recordError("Init failed: ${e.message}") { it.copy(sdkInitialized = false) }
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    override suspend fun startScan() {
        requireInitialized()
        recordEvent("Scanning...")
        BleScannerHelper.getInstance().scanDevice(context, null, object : ScanWrapperCallback {
            override fun onStart() {
                recordEvent("Scan started")
            }
            override fun onStop() {
                recordEvent("Scan stopped")
            }
            override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
                val name = runCatching { device.name }.getOrNull() ?: "?"
                Timber.d("HeyCyanSDK: scan found $name @ ${device.address}")
                recordEvent("Found: $name (${device.address})")
            }
            override fun onScanFailed(errorCode: Int) {
                Timber.e("HeyCyanSDK: scan failed code=$errorCode")
                recordError("Scan failed: code $errorCode")
            }
            override fun onParsedData(device: BluetoothDevice, record: ScanRecord) {}
            override fun onBatchScanResults(results: List<ScanResult>) {}
        })
    }

    override suspend fun stopScan() {
        BleScannerHelper.getInstance().stopScan(context)
        recordEvent("Scan stopped")
    }

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    override suspend fun connectToDevice(address: String) {
        requireInitialized()
        recordEvent("Connecting to $address...") {
            it.copy(connected = false, ready = false, lastError = null)
        }
        BleOperateManager.getInstance().connectDirectly(address)
        startConnectionPolling()
    }

    override suspend fun disconnect() {
        connectionPollJob?.cancel()
        cancelResponseTimeouts()
        runCatching { BleOperateManager.getInstance()?.disconnect() }
        recordEvent("Disconnected") {
            it.copy(connected = false, ready = false, battery = null, isCharging = null)
        }
        Timber.d("HeyCyanSDK: disconnected")
    }

    // -------------------------------------------------------------------------
    // Battery
    // -------------------------------------------------------------------------

    override suspend fun getBatteryLevel(): Int? {
        if (!isConnected()) return null
        syncBattery()
        return null
    }

    override suspend fun syncBattery() {
        if (!isConnected()) {
            recordError("Battery request failed: not connected")
            return
        }
        recordEvent("Requesting battery...")
        batteryTimeoutJob = responseTimeout(batteryTimeoutJob, "Battery request")
        LargeDataHandler.getInstance().syncBattery()
    }

    private val batteryCallback = object : ILargeDataResponse<BatteryResponse> {
        override fun parseData(code: Int, data: BatteryResponse) {
            batteryTimeoutJob?.cancel()
            val level = data.getBattery()
            val charging = data.isCharging()
            Timber.d("HeyCyanSDK: battery $level% charging=$charging (code=$code)")
            recordEvent("Battery: $level%${if (charging) " (charging)" else ""}") {
                it.copy(battery = level, isCharging = charging)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Device Info
    // -------------------------------------------------------------------------

    override suspend fun readDeviceInfo() {
        if (!isConnected()) {
            recordError("Device info request failed: not connected")
            return
        }
        recordEvent("Requesting device info...")
        deviceInfoTimeoutJob = responseTimeout(deviceInfoTimeoutJob, "Device info request")
        LargeDataHandler.getInstance().syncDeviceInfo(object : ILargeDataResponse<DeviceInfoResponse> {
            override fun parseData(code: Int, data: DeviceInfoResponse) {
                deviceInfoTimeoutJob?.cancel()
                val fw = data.getFirmwareVersion()
                val hw = data.getHardwareVersion()
                val wfw = data.getWifiFirmwareVersion()
                val whw = data.getWifiHardwareVersion()
                Timber.d("HeyCyanSDK: fw=$fw hw=$hw wfw=$wfw whw=$whw (code=$code)")
                recordEvent("Device info: fw=$fw hw=$hw") {
                    it.copy(
                        firmwareVersion = fw, hardwareVersion = hw,
                        wifiFirmwareVersion = wfw, wifiHardwareVersion = whw
                    )
                }
            }
        })
    }

    // -------------------------------------------------------------------------
    // Media counts — Experimental
    // -------------------------------------------------------------------------

    /**
     * [Experimental] Sends a GlassModelControlReq to query current media status.
     * GlassModelControlReq(param1=0, param2=0) maps to subData=[2,0,0].
     * param1/param2 semantics NOT verified on real CY 01_24E5 — treat response as diagnostics only.
     */
    override suspend fun readMediaCounts() {
        if (!isConnected()) {
            recordError("[Exp] Media counts request failed: not connected")
            return
        }
        recordEvent("[Exp] Requesting media counts...")
        try {
            val req = GlassModelControlReq(0, 0)
            mediaControlTimeoutJob = responseTimeout(mediaControlTimeoutJob, "[Exp] Media counts request")
            LargeDataHandler.getInstance().glassesControl(
                req.getData(),
                object : ILargeDataResponse<GlassModelControlResponse> {
                    override fun parseData(code: Int, data: GlassModelControlResponse) {
                        mediaControlTimeoutJob?.cancel()
                        val imgs = data.getImageCount()
                        val vids = data.getVideoCount()
                        val recs = data.getRecordCount()
                        val ip = data.getP2pIp()
                        val error = data.getErrorCode()
                        Timber.d("HeyCyanSDK: [Exp] mediaCount imgs=$imgs vids=$vids recs=$recs ip=$ip code=$code err=$error")
                        if (error != 0) {
                            recordError("[Exp] Media counts callback error: code=$code err=$error")
                            return
                        }
                        recordEvent("[Exp] Media: imgs=$imgs vids=$vids recs=$recs") {
                            it.copy(
                                imageCount = imgs, videoCount = vids, recordCount = recs,
                                p2pIp = ip?.takeIf { value -> value.isNotBlank() }
                            )
                        }
                    }
                }
            )
        } catch (e: Exception) {
            mediaControlTimeoutJob?.cancel()
            Timber.e(e, "HeyCyanSDK: readMediaCounts failed")
            recordError("[Exp] Media counts failed: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Picture thumbnails
    // -------------------------------------------------------------------------

    override suspend fun readPictureThumbnails() {
        if (!isConnected()) {
            recordError("Thumbnail request failed: not connected")
            return
        }
        recordEvent("Requesting picture thumbnails...")
        thumbnailsTimeoutJob = responseTimeout(thumbnailsTimeoutJob, "Picture thumbnails request")
        var count = 0
        LargeDataHandler.getInstance().getPictureThumbnails(object : ILargeDataImageResponse {
            override fun parseData(code: Int, isLast: Boolean, data: ByteArray) {
                thumbnailsTimeoutJob?.cancel()
                count++
                Timber.d("HeyCyanSDK: thumbnail chunk $count [${data.size}B] last=$isLast code=$code")
                recordEvent("Thumbnail $count [${data.size}B]${if (isLast) " (last)" else ""}") {
                    it.copy(thumbnailsReceived = count)
                }
                if (!isLast) {
                    thumbnailsTimeoutJob = responseTimeout(null, "Picture thumbnails request")
                }
            }
        })
    }

    // -------------------------------------------------------------------------
    // Capture commands — Experimental
    // -------------------------------------------------------------------------

    /**
     * [Experimental] Capture mappings are candidates only.
     * Disabled until explicit real-device verification on CY 01_24E5.
     */
    override suspend fun takePhoto() = rejectUnverifiedCommand("take photo")

    /** [Experimental] Video mapping is disabled pending real-device verification. */
    override suspend fun startVideoRecording() = rejectUnverifiedCommand("start video")

    /** [Experimental] Video mapping is disabled pending real-device verification. */
    override suspend fun stopVideoRecording() = rejectUnverifiedCommand("stop video")

    /** [Experimental] Audio mapping is disabled pending real-device verification. */
    override suspend fun startAudioRecording() = rejectUnverifiedCommand("start audio")

    /** [Experimental] Audio mapping is disabled pending real-device verification. */
    override suspend fun stopAudioRecording() = rejectUnverifiedCommand("stop audio")

    // -------------------------------------------------------------------------
    // Wi-Fi transfer
    // -------------------------------------------------------------------------

    /**
     * [Experimental] Disabled pending verification of the Wi-Fi command and credentials.
     */
    override suspend fun openWifiTransferMode(): Pair<String, String> =
        rejectUnverifiedCommand("Wi-Fi transfer mode")

    override suspend fun getDeviceWifiIp(): String? = _diagnostics.value.p2pIp

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    override fun release() {
        connectionPollJob?.cancel()
        cancelResponseTimeouts()
        runCatching {
            BleOperateManager.getInstance()?.disconnect()
            LargeDataHandler.getInstance().removeBatteryCallBack(BATTERY_KEY)
            LargeDataHandler.getInstance().disEnable()
        }
        sdkInitialized = false
        recordEvent("Released") {
            it.copy(sdkInitialized = false, connected = false, ready = false, battery = null, isCharging = null)
        }
        Timber.d("HeyCyanSDK: released")
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun startConnectionPolling() {
        connectionPollJob?.cancel()
        connectionPollJob = scope.launch {
            repeat(30) {
                delay(1_000)
                val connected = isConnected()
                val ready = isReady()
                _diagnostics.value = _diagnostics.value.copy(
                    connected = connected,
                    ready = ready
                )
                if (connected && ready) {
                    recordEvent("Connected and ready")
                    Timber.d("HeyCyanSDK: connected and ready")
                    return@launch
                }
            }
            runCatching { BleOperateManager.getInstance()?.disconnect() }
            recordError("Connection timeout: device did not become ready") {
                it.copy(connected = false, ready = false)
            }
        }
    }

    private fun responseTimeout(previous: Job?, request: String): Job {
        previous?.cancel()
        return scope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            recordError("$request timeout: no SDK callback")
        }
    }

    private fun cancelResponseTimeouts() {
        batteryTimeoutJob?.cancel()
        deviceInfoTimeoutJob?.cancel()
        mediaControlTimeoutJob?.cancel()
        thumbnailsTimeoutJob?.cancel()
    }

    private fun recordEvent(message: String, update: (SdkDiagnosticsState) -> SdkDiagnosticsState = { it }) {
        val next = update(_diagnostics.value)
        _diagnostics.value = next.copy(
            lastEvent = message,
            eventLog = (next.eventLog + message).takeLast(EVENT_LOG_LIMIT)
        )
    }

    private fun recordError(message: String, update: (SdkDiagnosticsState) -> SdkDiagnosticsState = { it }) {
        val next = update(_diagnostics.value)
        _diagnostics.value = next.copy(
            lastError = message,
            eventLog = (next.eventLog + "ERROR: $message").takeLast(EVENT_LOG_LIMIT)
        )
    }

    private fun rejectUnverifiedCommand(command: String): Nothing {
        val message = "[Experimental] $command disabled pending real-device verification"
        recordError(message)
        throw UnsupportedOperationException(message)
    }

    private fun requireInitialized() {
        if (!sdkInitialized) throw SdkNotAvailableException(
            "SDK not initialized — call initSdk() before this operation."
        )
    }

    companion object {
        private const val BATTERY_KEY = "cyanbridge_battery"
        private const val RESPONSE_TIMEOUT_MS = 10_000L
        private const val EVENT_LOG_LIMIT = 20
    }
}
