package com.cyanbridge.app.glasses.sdk

import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.glasses.protocol.GlassesNotification
import com.cyanbridge.app.glasses.protocol.HeyCyanProtocol
import timber.log.Timber

/**
 * Maps raw BLE notification bytes to [GlassesStatus] updates.
 *
 * Notification frame structure (confirmed from SDK reverse-engineering):
 *   byte[6] == 0x08 → device Wi-Fi IP; bytes[7..10] are IPv4 octets
 *   byte[6] == 0x09 → P2P/Wi-Fi error; byte[7] == 0xFF is common and non-fatal
 *
 * Returns null when the notification does not change [GlassesStatus].
 */
object HeyCyanSdkStateMapper {

    fun mapNotification(data: ByteArray, current: GlassesStatus): GlassesStatus? {
        val notification = HeyCyanProtocol.parseNotification(data)
        return when (notification) {
            is GlassesNotification.BatteryLevel -> {
                if (current is GlassesStatus.Connected) {
                    current.copy(batteryLevel = notification.level)
                } else null
            }
            is GlassesNotification.WifiIpReceived -> {
                Timber.d("HeyCyanSdkStateMapper: device Wi-Fi IP = ${notification.ip}")
                // TODO: forward IP to HeyCyanWiFiTransfer once implemented
                null
            }
            is GlassesNotification.P2pError -> {
                Timber.w("HeyCyanSdkStateMapper: P2P error code=0x%02x".format(notification.code))
                // 0xFF is common and non-fatal — do not update status
                null
            }
            is GlassesNotification.PhotoTaken -> {
                Timber.d("HeyCyanSdkStateMapper: photo taken id=${notification.id}")
                null
            }
            is GlassesNotification.RecordingStarted -> {
                Timber.d("HeyCyanSdkStateMapper: recording started id=${notification.id}")
                null
            }
            is GlassesNotification.RecordingStopped -> {
                Timber.d("HeyCyanSdkStateMapper: recording stopped id=${notification.id}")
                null
            }
            is GlassesNotification.Unknown -> {
                Timber.v("HeyCyanSdkStateMapper: unknown notification ${data.toHexString()}")
                null
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }
}
