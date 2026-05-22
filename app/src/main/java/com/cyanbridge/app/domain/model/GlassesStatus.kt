package com.cyanbridge.app.domain.model

enum class GlassesMode {
    FAKE,
    NATIVE_BLE
}

sealed class GlassesStatus {
    object Idle : GlassesStatus()
    object Scanning : GlassesStatus()
    data class Connecting(val deviceName: String) : GlassesStatus()
    data class Connected(val deviceName: String, val batteryLevel: Int?) : GlassesStatus()
    object Disconnected : GlassesStatus()
    data class Error(val message: String) : GlassesStatus()
    object FakeConnected : GlassesStatus()
}

fun GlassesStatus.displayText(): String = when (this) {
    is GlassesStatus.Idle -> "Ожидание"
    is GlassesStatus.Scanning -> "Поиск очков..."
    is GlassesStatus.Connecting -> "Подключение к $deviceName..."
    is GlassesStatus.Connected -> "$deviceName" + (batteryLevel?.let { " · ${it}%" } ?: "")
    is GlassesStatus.Disconnected -> "Не подключено"
    is GlassesStatus.Error -> "Ошибка: $message"
    is GlassesStatus.FakeConnected -> "Fake режим активен"
}

fun GlassesStatus.isConnected(): Boolean = this is GlassesStatus.Connected || this is GlassesStatus.FakeConnected
