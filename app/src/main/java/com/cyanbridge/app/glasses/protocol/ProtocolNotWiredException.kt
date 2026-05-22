package com.cyanbridge.app.glasses.protocol

/**
 * Thrown when a BLE command or notification parsing is attempted
 * but the protocol bytes/UUIDs are not yet confirmed from the manufacturer.
 *
 * Do NOT replace this with guessed values. Wait for manufacturer-original protocol docs.
 */
class ProtocolNotWiredException(message: String) : UnsupportedOperationException(message)
