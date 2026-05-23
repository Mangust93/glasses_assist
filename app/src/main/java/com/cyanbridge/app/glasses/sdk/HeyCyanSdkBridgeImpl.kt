package com.cyanbridge.app.glasses.sdk

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [HeyCyanSdkBridge]. All methods throw [SdkNotAvailableException]
 * until glasses_sdk_20250723_v01.aar is placed in app/libs/.
 *
 * ── HOW TO ACTIVATE ──────────────────────────────────────────────────────────
 * 1. Place glasses_sdk_20250723_v01.aar in app/libs/  (387 KB)
 *    Source: ebowwa/HeyCyanSmartGlassesSDK  or  FerSaiyan/Alternative-HeyCyan-App-and-SDK
 * 2. build.gradle.kts already includes: implementation(fileTree(dir="libs", include=["*.aar"]))
 * 3. Replace each `notAvailable()` call with the corresponding SDK call below.
 *
 * ── SDK INITIALIZATION ORDER (from AAR sample) ───────────────────────────────
 *   BleBaseControl.setmContext(context)
 *   BleOperateManager.getInstance().init()
 *   LargeDataHandler.getInstance()
 *   LocalBroadcastManager.getInstance(context)
 *       .registerReceiver(receiver, BleAction.getIntentFilter())
 *
 * ── KEY SDK CLASSES ──────────────────────────────────────────────────────────
 *   LargeDataHandler.getInstance()
 *       .glassesControl(ByteArray)            ← all capture/mode commands
 *       .getPictureThumbnails { data -> }
 *       .writeIpToSoc(url, callback)          ← OTA pull-mode
 *       .addOutDeviceListener(cmdType=100) { cmdType, response -> }
 *       .syncTime()
 *
 *   BleOperateManager.getInstance()
 *       .connectDirectly(address: String)
 *       .unBindDevice()
 *       .classicBluetoothStartScan()
 *
 *   BleScannerHelper.getInstance()
 *       .scanDevice(context, null, scanWrapperCallback)
 *
 *   QCBluetoothCallbackCloneReceiver  ← base class for BLE event callbacks
 *       override connectStatue(device, connected)
 *       override onServiceDiscovered()
 *       override onCharacteristicChange(address, uuid, data)
 *
 * ── CAPTURE COMMANDS (all via LargeDataHandler.glassesControl) ───────────────
 *   Take photo:       byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)
 *   Video start:      byteArrayOf(0x02, 0x01, 0x02)
 *   Video stop:       byteArrayOf(0x02, 0x01, 0x03)
 *   Audio start:      byteArrayOf(0x02, 0x01, 0x08)
 *   Audio stop:       byteArrayOf(0x02, 0x01, 0x0C)
 *   Wi-Fi transfer:   byteArrayOf(0x02, 0x01, 0x04)
 *   Media count:      byteArrayOf(0x02, 0x04)
 */
@Singleton
class HeyCyanSdkBridgeImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HeyCyanSdkBridge {

    override fun isAarAvailable(): Boolean = false

    override suspend fun initSdk(context: Context): Unit = notAvailable()
    override suspend fun startScan(): Unit = notAvailable()
    override suspend fun stopScan(): Unit = notAvailable()
    override suspend fun connectToDevice(address: String): Unit = notAvailable()
    override suspend fun disconnect(): Unit = notAvailable()
    override suspend fun takePhoto(): Unit = notAvailable()
    override suspend fun startVideoRecording(): Unit = notAvailable()
    override suspend fun stopVideoRecording(): Unit = notAvailable()
    override suspend fun startAudioRecording(): Unit = notAvailable()
    override suspend fun stopAudioRecording(): Unit = notAvailable()
    override suspend fun getBatteryLevel(): Int? = notAvailable()
    override suspend fun openWifiTransferMode(): Pair<String, String> = notAvailable()
    override suspend fun getDeviceWifiIp(): String? = notAvailable()
    override fun release(): Unit = Unit

    private fun notAvailable(): Nothing = throw SdkNotAvailableException(
        "glasses_sdk_20250723_v01.aar not found in app/libs/. " +
            "See docs/heycyan-sdk-analysis.md for placement instructions."
    )
}
