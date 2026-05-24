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
import com.oudmon.ble.base.communication.req.CameraReq
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
 *   3. BleOperateManager.getInstance(application)  ← creates singleton with Application
 *   4. BleOperateManager.getInstance().setCallback(OnGattEventCallback)
 *   5. LargeDataHandler.getInstance().initEnable()
 *   6. LargeDataHandler.getInstance().addBatteryCallBack(key, callback)
 *
 * Connection state is tracked via polling BleOperateManager.isConnected()/isReady()
 * after connectDirectly() because OnGattEventCallback only delivers data frames, not
 * connection state events.
 *
 * Capture commands marked [Experimental] use GlassModelControlReq whose param1/param2
 * mapping has NOT been verified on real CY 01_24E5 hardware. Do NOT expose them as
 * automated actions — require explicit user confirmation in the UI.
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
        try {
            SDKInit.getInstance().setSDKType(SDKInit.SDK_TYPE_QC)
            BleBaseControl.getInstance(context).setmContext(context)
            val application = context as Application
            BleOperateManager.getInstance(application)
            BleOperateManager.getInstance().setCallback(object : OnGattEventCallback {
                override fun onReceivedData(address: String, data: ByteArray) {
                    Timber.d("HeyCyanSDK: data from $address [${data.size}B]")
                    _diagnostics.value = _diagnostics.value.copy(
                        lastEvent = "Data: ${data.size}B from $address"
                    )
                }
            })
            LargeDataHandler.getInstance().initEnable()
            LargeDataHandler.getInstance().addBatteryCallBack(BATTERY_KEY, batteryCallback)
            sdkInitialized = true
            _diagnostics.value = _diagnostics.value.copy(
                sdkInitialized = true,
                lastEvent = "SDK initialized (QC type)"
            )
            Timber.d("HeyCyanSDK: initialized")
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: init failed")
            _diagnostics.value = _diagnostics.value.copy(
                sdkInitialized = false,
                lastError = "Init failed: ${e.message}"
            )
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    override suspend fun startScan() {
        requireInitialized()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "Scanning…")
        BleScannerHelper.getInstance().scanDevice(context, null, object : ScanWrapperCallback {
            override fun onStart() {
                _diagnostics.value = _diagnostics.value.copy(lastEvent = "Scan started")
            }
            override fun onStop() {
                _diagnostics.value = _diagnostics.value.copy(lastEvent = "Scan stopped")
            }
            override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
                val name = runCatching { device.name }.getOrNull() ?: "?"
                Timber.d("HeyCyanSDK: scan found $name @ ${device.address}")
                _diagnostics.value = _diagnostics.value.copy(
                    lastEvent = "Found: $name (${device.address})"
                )
            }
            override fun onScanFailed(errorCode: Int) {
                Timber.e("HeyCyanSDK: scan failed code=$errorCode")
                _diagnostics.value = _diagnostics.value.copy(
                    lastError = "Scan failed: code $errorCode"
                )
            }
            override fun onParsedData(device: BluetoothDevice, record: ScanRecord) {}
            override fun onBatchScanResults(results: List<ScanResult>) {}
        })
    }

    override suspend fun stopScan() {
        BleScannerHelper.getInstance().stopScan(context)
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "Scan stopped")
    }

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    override suspend fun connectToDevice(address: String) {
        requireInitialized()
        _diagnostics.value = _diagnostics.value.copy(
            connected = false, ready = false,
            lastEvent = "Connecting to $address…"
        )
        BleOperateManager.getInstance().connectDirectly(address)
        startConnectionPolling()
    }

    override suspend fun disconnect() {
        connectionPollJob?.cancel()
        runCatching { BleOperateManager.getInstance()?.disconnect() }
        LargeDataHandler.getInstance().removeBatteryCallBack(BATTERY_KEY)
        _diagnostics.value = _diagnostics.value.copy(
            connected = false,
            ready = false,
            battery = null,
            lastEvent = "Disconnected"
        )
        Timber.d("HeyCyanSDK: disconnected")
    }

    // -------------------------------------------------------------------------
    // Battery
    // -------------------------------------------------------------------------

    override suspend fun getBatteryLevel(): Int? {
        if (!isConnected()) return null
        LargeDataHandler.getInstance().syncBattery()
        return _diagnostics.value.battery
    }

    override suspend fun syncBattery() {
        if (!isConnected()) {
            _diagnostics.value = _diagnostics.value.copy(lastError = "Not connected")
            return
        }
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "Syncing battery…")
        LargeDataHandler.getInstance().syncBattery()
    }

    private val batteryCallback = object : ILargeDataResponse<BatteryResponse> {
        override fun parseData(code: Int, data: BatteryResponse) {
            val level = data.getBattery()
            val charging = data.isCharging()
            Timber.d("HeyCyanSDK: battery $level% charging=$charging (code=$code)")
            _diagnostics.value = _diagnostics.value.copy(
                battery = level,
                isCharging = charging,
                lastEvent = "Battery: $level%${if (charging) " (charging)" else ""}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Device Info
    // -------------------------------------------------------------------------

    override suspend fun readDeviceInfo() {
        if (!isConnected()) {
            _diagnostics.value = _diagnostics.value.copy(lastError = "Not connected")
            return
        }
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "Reading device info…")
        LargeDataHandler.getInstance().syncDeviceInfo(object : ILargeDataResponse<DeviceInfoResponse> {
            override fun parseData(code: Int, data: DeviceInfoResponse) {
                val fw = data.getFirmwareVersion()
                val hw = data.getHardwareVersion()
                val wfw = data.getWifiFirmwareVersion()
                Timber.d("HeyCyanSDK: fw=$fw hw=$hw wfw=$wfw (code=$code)")
                _diagnostics.value = _diagnostics.value.copy(
                    firmwareVersion = fw,
                    hardwareVersion = hw,
                    wifiFirmwareVersion = wfw,
                    lastEvent = "Device info: fw=$fw hw=$hw"
                )
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
            _diagnostics.value = _diagnostics.value.copy(lastError = "Not connected")
            return
        }
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] Reading media counts…")
        try {
            val req = GlassModelControlReq(0, 0)
            LargeDataHandler.getInstance().glassesControl(
                req.getData(),
                object : ILargeDataResponse<GlassModelControlResponse> {
                    override fun parseData(code: Int, data: GlassModelControlResponse) {
                        val imgs = data.getImageCount()
                        val vids = data.getVideoCount()
                        val recs = data.getRecordCount()
                        val ip = data.getP2pIp()
                        Timber.d("HeyCyanSDK: [Exp] mediaCount imgs=$imgs vids=$vids recs=$recs ip=$ip code=$code err=${data.getErrorCode()}")
                        _diagnostics.value = _diagnostics.value.copy(
                            imageCount = imgs,
                            videoCount = vids,
                            recordCount = recs,
                            p2pIp = ip?.takeIf { it.isNotBlank() },
                            lastEvent = "[Exp] Media: imgs=$imgs vids=$vids recs=$recs"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: readMediaCounts failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "readMediaCounts: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Picture thumbnails
    // -------------------------------------------------------------------------

    override suspend fun readPictureThumbnails() {
        if (!isConnected()) {
            _diagnostics.value = _diagnostics.value.copy(lastError = "Not connected")
            return
        }
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "Fetching thumbnails…")
        var count = 0
        LargeDataHandler.getInstance().getPictureThumbnails(object : ILargeDataImageResponse {
            override fun parseData(code: Int, isLast: Boolean, data: ByteArray) {
                count++
                Timber.d("HeyCyanSDK: thumbnail chunk $count [${data.size}B] last=$isLast code=$code")
                _diagnostics.value = _diagnostics.value.copy(
                    thumbnailsReceived = count,
                    lastEvent = "Thumbnail $count [${data.size}B]${if (isLast) " (last)" else ""}"
                )
            }
        })
    }

    // -------------------------------------------------------------------------
    // Capture commands — Experimental
    // -------------------------------------------------------------------------

    /**
     * [Experimental] Enters camera UI via CameraReq(ACTION_INTO_CAMARA_UI=4).
     * ACTION_FINISH (6) corresponds to byte 0x06 in CMD_TAKE_PHOTO candidate sequence.
     * Verified payload behaviour on real CY 01_24E5 pending.
     */
    override suspend fun takePhoto() {
        requireConnected()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] takePhoto…")
        try {
            val req = CameraReq(CameraReq.ACTION_INTO_CAMARA_UI)
            LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: takePhoto failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "takePhoto: ${e.message}")
        }
    }

    /**
     * [Experimental] GlassModelControlReq param mapping for video start not verified.
     * subData=[2,1,2] is a candidate based on bytecode analysis.
     */
    override suspend fun startVideoRecording() {
        requireConnected()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] startVideo…")
        try {
            val req = GlassModelControlReq(1, 2)
            LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: startVideoRecording failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "startVideo: ${e.message}")
        }
    }

    /** [Experimental] GlassModelControlReq param mapping for video stop not verified. */
    override suspend fun stopVideoRecording() {
        requireConnected()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] stopVideo…")
        try {
            val req = GlassModelControlReq(1, 3)
            LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: stopVideoRecording failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "stopVideo: ${e.message}")
        }
    }

    /** [Experimental] GlassModelControlReq param mapping for audio start not verified. */
    override suspend fun startAudioRecording() {
        requireConnected()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] startAudio…")
        try {
            val req = GlassModelControlReq(1, 8)
            LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: startAudioRecording failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "startAudio: ${e.message}")
        }
    }

    /** [Experimental] GlassModelControlReq param mapping for audio stop not verified. */
    override suspend fun stopAudioRecording() {
        requireConnected()
        _diagnostics.value = _diagnostics.value.copy(lastEvent = "[Exp] stopAudio…")
        try {
            val req = GlassModelControlReq(1, 12)
            LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        } catch (e: Exception) {
            Timber.e(e, "HeyCyanSDK: stopAudioRecording failed")
            _diagnostics.value = _diagnostics.value.copy(lastError = "stopAudio: ${e.message}")
        }
    }

    private val mediaControlCallback = object : ILargeDataResponse<GlassModelControlResponse> {
        override fun parseData(code: Int, data: GlassModelControlResponse) {
            val err = data.getErrorCode()
            val workType = data.getGlassWorkType()
            val ip = data.getP2pIp()
            Timber.d("HeyCyanSDK: mediaControl code=$code err=$err workType=$workType ip=$ip")
            if (err != 0) {
                _diagnostics.value = _diagnostics.value.copy(
                    lastError = "Media control error: code=$code err=$err"
                )
            } else {
                _diagnostics.value = _diagnostics.value.copy(
                    p2pIp = ip?.takeIf { it.isNotBlank() },
                    imageCount = data.getImageCount().takeIf { it != 0 },
                    videoCount = data.getVideoCount().takeIf { it != 0 },
                    recordCount = data.getRecordCount().takeIf { it != 0 },
                    lastEvent = "Media control ok (workType=$workType)"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wi-Fi transfer
    // -------------------------------------------------------------------------

    /**
     * [Experimental] Triggers Wi-Fi transfer mode via GlassModelControlReq.
     * param mapping for Wi-Fi start not fully verified.
     */
    override suspend fun openWifiTransferMode(): Pair<String, String> {
        requireConnected()
        val req = GlassModelControlReq(1, 4)
        LargeDataHandler.getInstance().glassesControl(req.getData(), mediaControlCallback)
        return Pair("", "123456789")
    }

    override suspend fun getDeviceWifiIp(): String? = _diagnostics.value.p2pIp

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    override fun release() {
        connectionPollJob?.cancel()
        runCatching {
            LargeDataHandler.getInstance().removeBatteryCallBack(BATTERY_KEY)
            LargeDataHandler.getInstance().disEnable()
        }
        _diagnostics.value = _diagnostics.value.copy(
            connected = false, ready = false, lastEvent = "Released"
        )
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
                    _diagnostics.value = _diagnostics.value.copy(lastEvent = "Connected and ready")
                    Timber.d("HeyCyanSDK: connected and ready")
                    return@launch
                }
                if (connected) {
                    _diagnostics.value = _diagnostics.value.copy(lastEvent = "Connected, waiting for ready…")
                }
            }
            if (!isConnected()) {
                _diagnostics.value = _diagnostics.value.copy(
                    connected = false, ready = false,
                    lastError = "Connection timeout"
                )
            }
        }
    }

    private fun requireInitialized() {
        if (!sdkInitialized) throw SdkNotAvailableException(
            "SDK not initialized — call initSdk() before this operation."
        )
    }

    private fun requireConnected() {
        requireInitialized()
        if (!isConnected()) throw SdkNotAvailableException("Not connected to device.")
    }

    companion object {
        private const val BATTERY_KEY = "cyanbridge_battery"
    }
}
