package com.cyanbridge.app.glasses.protocol

import java.util.UUID

/**
 * BLE protocol constants for HeyCyan smart glasses (CY 01_24E5, MAC: 91:8E:55:C7:24:E5).
 *
 * Service UUIDs and command bytes confirmed via SDK reverse-engineering:
 *   - ebowwa/HeyCyanSmartGlassesSDK
 *   - FerSaiyan/Alternative-HeyCyan-App-and-SDK
 *
 * Individual characteristic UUIDs are NOT in SDK headers — they are auto-discovered
 * at runtime by scanning the service characteristics (see HeyCyanSdkGlassesController).
 * Use NATIVE_BLE_DIAGNOSTIC mode to log actual characteristic UUIDs from your device.
 */
object HeyCyanProtocol {

    // Primary BLE service UUID (confirmed: QCSDKSERVERUUID1 in iOS SDK framework)
    val SERVICE_UUID: UUID = UUID.fromString("7905fff0-b5ce-4e99-a40f-4b1e122d00d0")

    // Secondary service UUID (Nordic UART-pattern: QCSDKSERVERUUID2)
    val SECONDARY_SERVICE_UUID: UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")

    // Characteristic UUIDs: not individually exposed in SDK headers.
    // HeyCyanSdkGlassesController discovers them at runtime by property flags.
    // Once logged from a real device, fill these in to skip discovery.
    val COMMAND_CHARACTERISTIC_UUID: UUID? = null  // TODO: confirm from BLE scan log
    val NOTIFY_CHARACTERISTIC_UUID: UUID? = null   // TODO: confirm from BLE scan log

    // -------------------------------------------------------------------------
    // Command byte sequences (confirmed: LargeDataHandler.glassesControl calls)
    // -------------------------------------------------------------------------

    /** Switch to camera mode (device UI shows viewfinder). */
    val CMD_CAMERA_MODE: ByteArray = byteArrayOf(0x02, 0x01, 0x01)

    /** Camera mode + capture: switch to camera, then trigger shutter. */
    val CMD_TAKE_PHOTO: ByteArray = byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)

    val CMD_VIDEO_START: ByteArray = byteArrayOf(0x02, 0x01, 0x02)
    val CMD_VIDEO_STOP: ByteArray = byteArrayOf(0x02, 0x01, 0x03)

    val CMD_AUDIO_START: ByteArray = byteArrayOf(0x02, 0x01, 0x08)
    val CMD_AUDIO_STOP: ByteArray = byteArrayOf(0x02, 0x01, 0x0C.toByte())

    /** Trigger device Wi-Fi hotspot + receive SSID/password in BLE notification. */
    val CMD_WIFI_TRANSFER_START: ByteArray = byteArrayOf(0x02, 0x01, 0x04)

    val CMD_QUERY_MEDIA_COUNT: ByteArray = byteArrayOf(0x02, 0x04)

    val CMD_AI_STOP: ByteArray = byteArrayOf(0x02, 0x01, 0x0B.toByte())
    val CMD_P2P_RESET: ByteArray = byteArrayOf(0x02, 0x01, 0x0F.toByte())

    // -------------------------------------------------------------------------
    // Notification frame constants
    // -------------------------------------------------------------------------

    /** byte[6] == 0x08: device Wi-Fi IP is present at bytes [7..10] as IPv4 octets. */
    const val NOTIFY_WIFI_IP: Int = 0x08

    /** byte[6] == 0x09: P2P/Wi-Fi error; byte[7] == 0xFF is common and non-fatal. */
    const val NOTIFY_P2P_ERROR: Int = 0x09

    // -------------------------------------------------------------------------
    // Encode helpers (kept for compatibility with NativeBleGlassesController)
    // -------------------------------------------------------------------------

    fun encodeTakePhoto(): ByteArray = CMD_TAKE_PHOTO
    fun encodeStartRecording(): ByteArray = CMD_VIDEO_START
    fun encodeStopRecording(): ByteArray = CMD_VIDEO_STOP

    fun encodeGetBattery(): ByteArray =
        throw ProtocolNotWiredException(
            "Battery is reported via BLE notifications — no confirmed explicit query command."
        )

    // -------------------------------------------------------------------------
    // Notification parsing
    // -------------------------------------------------------------------------

    fun parseNotification(data: ByteArray): GlassesNotification {
        if (data.size < 7) return GlassesNotification.Unknown(data)
        return when (data[6].toInt() and 0xFF) {
            NOTIFY_WIFI_IP -> {
                if (data.size >= 11) {
                    val ip = "${data[7].toInt() and 0xFF}.${data[8].toInt() and 0xFF}" +
                        ".${data[9].toInt() and 0xFF}.${data[10].toInt() and 0xFF}"
                    GlassesNotification.WifiIpReceived(ip)
                } else GlassesNotification.Unknown(data)
            }
            NOTIFY_P2P_ERROR -> {
                val code = data.getOrElse(7) { 0 }.toInt() and 0xFF
                GlassesNotification.P2pError(code)
            }
            else -> GlassesNotification.Unknown(data)
        }
    }
}

sealed class GlassesNotification {
    data class BatteryLevel(val level: Int) : GlassesNotification()
    data class PhotoTaken(val id: String) : GlassesNotification()
    data class RecordingStarted(val id: String) : GlassesNotification()
    data class RecordingStopped(val id: String) : GlassesNotification()
    data class WifiIpReceived(val ip: String) : GlassesNotification()
    data class P2pError(val code: Int) : GlassesNotification()
    data class Unknown(val rawBytes: ByteArray) : GlassesNotification()
}
