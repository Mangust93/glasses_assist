package com.cyanbridge.app.glasses.protocol

import java.util.UUID

/**
 * BLE protocol definition for HeyCyan smart glasses.
 *
 * STATUS: INCOMPLETE — waiting for confirmed bytes from manufacturer-original branch.
 *
 * To complete this file, provide:
 *   1. COMMAND_CHARACTERISTIC_UUID — UUID of the write characteristic
 *   2. NOTIFY_CHARACTERISTIC_UUID  — UUID of the notification characteristic
 *   3. Command byte sequences for each operation
 *   4. Notification byte format for parsing
 *
 * Source to check: https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK
 *   branch: manufacturer-original
 */
object HeyCyanProtocol {

    // Candidate service UUID found in open-source alternative SDK research.
    // TODO: Confirm with manufacturer-original branch — may differ per firmware version.
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    // TODO: Obtain from manufacturer-original branch
    val COMMAND_CHARACTERISTIC_UUID: UUID? = null

    // TODO: Obtain from manufacturer-original branch
    val NOTIFY_CHARACTERISTIC_UUID: UUID? = null

    // -------------------------------------------------------------------------
    // Commands — all throw ProtocolNotWiredException until bytes are confirmed
    // -------------------------------------------------------------------------

    fun encodeTakePhoto(): ByteArray =
        throw ProtocolNotWiredException(
            "encodeTakePhoto: command bytes not confirmed. " +
                "Check manufacturer-original branch for the correct byte sequence."
        )

    fun encodeStartRecording(): ByteArray =
        throw ProtocolNotWiredException(
            "encodeStartRecording: command bytes not confirmed. " +
                "Check manufacturer-original branch."
        )

    fun encodeStopRecording(): ByteArray =
        throw ProtocolNotWiredException(
            "encodeStopRecording: command bytes not confirmed. " +
                "Check manufacturer-original branch."
        )

    fun encodeGetBattery(): ByteArray =
        throw ProtocolNotWiredException(
            "encodeGetBattery: command bytes not confirmed. " +
                "Check manufacturer-original branch."
        )

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    fun parseNotification(data: ByteArray): GlassesNotification =
        throw ProtocolNotWiredException(
            "parseNotification: notification format not confirmed. " +
                "Check manufacturer-original branch for byte layout."
        )
}

sealed class GlassesNotification {
    data class BatteryLevel(val level: Int) : GlassesNotification()
    data class PhotoTaken(val id: String) : GlassesNotification()
    data class RecordingStarted(val id: String) : GlassesNotification()
    data class RecordingStopped(val id: String) : GlassesNotification()
    data class Unknown(val rawBytes: ByteArray) : GlassesNotification()
}
