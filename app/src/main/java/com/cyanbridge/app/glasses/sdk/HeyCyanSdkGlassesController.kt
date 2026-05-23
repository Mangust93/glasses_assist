package com.cyanbridge.app.glasses.sdk

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.cyanbridge.app.domain.interfaces.GlassesController
import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.glasses.protocol.HeyCyanProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GlassesController for HEYCYAN_SDK mode.
 *
 * Prepared adapter for the official HeyCyan SDK.
 *
 * The AAR is not committed to the repository. Until glasses_sdk_20250723_v01.aar
 * is placed in app/libs/ and [HeyCyanSdkBridge] is wired to the official SDK, this
 * controller must fail explicitly instead of pretending SDK commands work.
 *
 * Target device: CY 01_24E5  (MAC: 91:8E:55:C7:24:E5)
 * Candidate primary service: 7905fff0-b5ce-4e99-a40f-4b1e122d00d0
 *
 * Characteristic UUIDs are auto-discovered at runtime by property flags (writable / notify).
 * After connecting with a real device, characteristic UUIDs will be logged — paste them into
 * [HeyCyanProtocol.COMMAND_CHARACTERISTIC_UUID] and [HeyCyanProtocol.NOTIFY_CHARACTERISTIC_UUID]
 * to skip discovery on future connections.
 *
 * Native BLE service discovery remains here only as implementation scaffolding for later
 * verification. Use NATIVE_BLE_DIAGNOSTIC mode for fallback/diagnostic BLE work.
 */
@Singleton
class HeyCyanSdkGlassesController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: HeyCyanSdkBridge
) : GlassesController {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Disconnected)
    override val status: Flow<GlassesStatus> = _status.asStateFlow()

    private var bleGatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var activeScanCallback: ScanCallback? = null

    var lastConnectedAddress: String? = null
        private set

    init {
        if (bridge.isAarAvailable()) {
            Timber.d("HeyCyanSdk: official AAR bridge available; commands will delegate to SDK")
        } else {
            Timber.w("HeyCyanSdk: AAR not present; SDK mode will report unavailable")
        }
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        ensureAarAvailable()
        if (!hasBluetoothPermissions()) {
            _status.value = GlassesStatus.Error("Bluetooth permissions not granted")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            _status.value = GlassesStatus.Error("Bluetooth is disabled")
            return
        }

        _status.value = GlassesStatus.Scanning
        Timber.d("HeyCyanSdk: starting BLE scan")

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            _status.value = GlassesStatus.Error("BLE scanner unavailable")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = device.name ?: return
                if (!isHeyCyanDevice(name)) return
                Timber.d("HeyCyanSdk: found $name @ ${device.address}")
                scanner.stopScan(this)
                activeScanCallback = null
                doConnect(device)
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("HeyCyanSdk: scan failed code=$errorCode")
                _status.value = GlassesStatus.Error("Scan failed (code $errorCode)")
            }
        }

        activeScanCallback = cb
        scanner.startScan(null, settings, cb)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        activeScanCallback?.let { scanner?.stopScan(it) }
        activeScanCallback = null
        if (_status.value is GlassesStatus.Scanning) {
            _status.value = GlassesStatus.Disconnected
        }
        Timber.d("HeyCyanSdk: scan stopped")
    }

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        ensureAarAvailable()
        if (!hasBluetoothPermissions()) {
            _status.value = GlassesStatus.Error("Bluetooth permissions not granted")
            return
        }
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            _status.value = GlassesStatus.Error("Invalid address: $address")
            return
        }
        doConnect(device)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        Timber.d("HeyCyanSdk: disconnect")
        bleGatt?.disconnect()
        bleGatt?.close()
        bleGatt = null
        commandChar = null
        _status.value = GlassesStatus.Disconnected
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    override suspend fun takePhoto() {
        ensureAarAvailable()
        sendCommand(HeyCyanProtocol.CMD_TAKE_PHOTO)
    }

    override suspend fun startRecording() {
        ensureAarAvailable()
        sendCommand(HeyCyanProtocol.CMD_VIDEO_START)
    }

    override suspend fun stopRecording() {
        ensureAarAvailable()
        sendCommand(HeyCyanProtocol.CMD_VIDEO_STOP)
    }

    override suspend fun getBatteryLevel(): Int? {
        ensureAarAvailable()
        return bridge.getBatteryLevel()
    }

    @SuppressLint("MissingPermission")
    override fun release() {
        activeScanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        activeScanCallback = null
        bleGatt?.close()
        bleGatt = null
        commandChar = null
        _status.value = GlassesStatus.Disconnected
        Timber.d("HeyCyanSdk: released")
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun doConnect(device: BluetoothDevice) {
        val name = device.name ?: device.address
        lastConnectedAddress = device.address
        _status.value = GlassesStatus.Connecting(name)
        closeGatt()
        bleGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val name = gatt?.device?.name ?: "Unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("HeyCyanSdk: connected $name — discovering services")
                    _status.value = GlassesStatus.Connected(name, null)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("HeyCyanSdk: disconnected $name")
                    commandChar = null
                    if (bleGatt == gatt) closeGatt() else gatt?.close()
                    _status.value = GlassesStatus.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("HeyCyanSdk: service discovery failed status=$status")
                return
            }

            val service = gatt?.getService(HeyCyanProtocol.SERVICE_UUID)
                ?: gatt?.getService(HeyCyanProtocol.SECONDARY_SERVICE_UUID)

            if (service == null) {
                Timber.w("HeyCyanSdk: HeyCyan service not found in discovered services:")
                gatt?.services?.forEach { s ->
                    Timber.d("  Service: ${s.uuid}")
                    s.characteristics.forEach { c ->
                        Timber.d("    Char: ${c.uuid}  props=0x%02x".format(c.properties))
                    }
                }
                return
            }

            Timber.d("HeyCyanSdk: service found ${service.uuid}")

            val writeChar = service.characteristics.firstOrNull { c ->
                c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            val notifyChar = service.characteristics.firstOrNull { c ->
                c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }

            writeChar?.let {
                commandChar = it
                Timber.d("HeyCyanSdk: command characteristic = ${it.uuid}")
            } ?: Timber.w("HeyCyanSdk: no writable characteristic in ${service.uuid}")

            notifyChar?.let { nc ->
                Timber.d("HeyCyanSdk: notify characteristic = ${nc.uuid}")
                gatt?.setCharacteristicNotification(nc, true)
                nc.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { desc ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt?.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt?.writeDescriptor(desc)
                    }
                }
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value ?: return
            handleNotification(value)
        }
    }

    private fun handleNotification(data: ByteArray) {
        Timber.d("HeyCyanSdk: notification ${data.toHexString()}")
        val updated = HeyCyanSdkStateMapper.mapNotification(data, _status.value)
        if (updated != null) _status.value = updated
    }

    private fun ensureAarAvailable() {
        if (bridge.isAarAvailable()) return
        val message = "HeyCyan SDK AAR is not available. Place glasses_sdk_20250723_v01.aar " +
            "in app/libs/ and wire HeyCyanSdkBridgeImpl before using HEYCYAN_SDK mode."
        _status.value = GlassesStatus.Error(message)
        throw SdkNotAvailableException(message)
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(bytes: ByteArray) {
        val gatt = bleGatt ?: run {
            Timber.w("HeyCyanSdk: sendCommand — not connected")
            return
        }
        val char = commandChar ?: run {
            Timber.w("HeyCyanSdk: sendCommand — command characteristic not discovered")
            return
        }
        Timber.d("HeyCyanSdk: → ${bytes.toHexString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    private fun isHeyCyanDevice(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("heycyan") || lower.contains("cyan") ||
            lower.startsWith("o_") || lower.startsWith("q_")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

    private fun closeGatt() {
        bleGatt?.close()
        bleGatt = null
    }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
