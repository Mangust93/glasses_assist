package com.cyanbridge.app.glasses.sdk

import android.content.Context

/**
 * Adapter interface for the official HeyCyan AAR SDK (glasses_sdk_20250723_v01.aar).
 *
 * Implemented by [HeyCyanSdkBridgeImpl], which is a documented stub until the AAR is placed
 * in app/libs/. See docs/heycyan-sdk-analysis.md for the full SDK API reference.
 *
 * [HeyCyanSdkGlassesController] queries [isAarAvailable] before running SDK-mode
 * operations. Without the AAR, SDK-mode operations must fail explicitly.
 */
interface HeyCyanSdkBridge {

    /** True only when glasses_sdk_20250723_v01.aar is present and initialized. */
    fun isAarAvailable(): Boolean

    suspend fun initSdk(context: Context)

    // BLE lifecycle
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(address: String)
    suspend fun disconnect()

    // Capture commands
    suspend fun takePhoto()
    suspend fun startVideoRecording()
    suspend fun stopVideoRecording()
    suspend fun startAudioRecording()
    suspend fun stopAudioRecording()

    suspend fun getBatteryLevel(): Int?

    /**
     * Triggers device Wi-Fi hotspot mode via BLE and returns (ssid, password).
     * Note: SDK may return wrong credentials — override password with "123456789" if needed.
     */
    suspend fun openWifiTransferMode(): Pair<String, String>

    /** Returns device IP discovered via BLE notification (byte[6]==0x08 frame). */
    suspend fun getDeviceWifiIp(): String?

    fun release()
}
