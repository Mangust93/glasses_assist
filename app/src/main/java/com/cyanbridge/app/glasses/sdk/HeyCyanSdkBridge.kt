package com.cyanbridge.app.glasses.sdk

import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter interface for the official HeyCyan AAR SDK (glasses_sdk_20250723_v01.aar).
 *
 * Implemented by [HeyCyanSdkBridgeImpl] using real SDK classes from the AAR.
 * See docs/heycyan-sdk-analysis.md for the full SDK API reference.
 *
 * [HeyCyanSdkGlassesController] queries [isAarAvailable] before running SDK-mode
 * operations. Without the AAR, SDK-mode operations must fail explicitly.
 */
interface HeyCyanSdkBridge {

    /** True only when glasses_sdk_20250723_v01.aar is present and initialized. */
    fun isAarAvailable(): Boolean

    /** Live SDK diagnostics state — observed by the Settings UI and controller. */
    val diagnosticsState: StateFlow<SdkDiagnosticsState>

    suspend fun initSdk()

    // BLE lifecycle
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(address: String)
    suspend fun disconnect()
    fun isConnected(): Boolean
    fun isReady(): Boolean

    // Capture commands (disabled pending real-device verification)
    suspend fun takePhoto()
    suspend fun startVideoRecording()
    suspend fun stopVideoRecording()
    suspend fun startAudioRecording()
    suspend fun stopAudioRecording()

    suspend fun getBatteryLevel(): Int?

    // Diagnostics actions — results surface in [diagnosticsState]
    suspend fun syncBattery()
    suspend fun readDeviceInfo()

    /**
     * [Experimental] Queries media counts via GlassModelControlReq.
     * param1/param2 mapping is not yet verified on real CY 01_24E5 hardware.
     */
    suspend fun readMediaCounts()

    /** Requests picture thumbnails from the device. */
    suspend fun readPictureThumbnails()

    /** [Experimental] Disabled pending real-device verification of Wi-Fi mode and credentials. */
    suspend fun openWifiTransferMode(): Pair<String, String>

    /** Returns device IP from last GlassModelControlResponse or BLE notification. */
    suspend fun getDeviceWifiIp(): String?

    fun release()
}
