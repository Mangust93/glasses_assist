package com.cyanbridge.app.glasses.ble

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
import com.cyanbridge.app.glasses.protocol.ProtocolNotWiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeBleGlassesController @Inject constructor(
    @ApplicationContext private val context: Context
) : GlassesController {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Disconnected)
    override val status: Flow<GlassesStatus> = _status.asStateFlow()

    private var bleGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

    /** Address of the most recently connected device. Set by connect() and the scan auto-connect. */
    var lastConnectedAddress: String? = null
        private set

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        if (!hasBluetoothPermissions()) {
            _status.value = GlassesStatus.Error("Bluetooth permissions not granted")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            _status.value = GlassesStatus.Error("Bluetooth is disabled")
            return
        }

        _status.value = GlassesStatus.Scanning
        Timber.d("NativeBle: starting BLE scan")

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            _status.value = GlassesStatus.Error("BLE scanner unavailable")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = device.name ?: return
                if (name.contains("HeyCyan", ignoreCase = true) ||
                    name.contains("CyanBridge", ignoreCase = true)
                ) {
                    Timber.d("NativeBle: found candidate device $name @ ${device.address}")
                    scanner.stopScan(this)
                    activeScanCallback = null
                    _status.value = GlassesStatus.Connecting(name)
                    lastConnectedAddress = device.address
                    bleGatt = device.connectGatt(
                        context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("NativeBle: scan failed, code=$errorCode")
                _status.value = GlassesStatus.Error("Scan failed (code $errorCode)")
            }
        }

        activeScanCallback = callback
        scanner.startScan(null, settings, callback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        activeScanCallback?.let { scanner?.stopScan(it) }
        activeScanCallback = null
        if (_status.value is GlassesStatus.Scanning) {
            _status.value = GlassesStatus.Disconnected
        }
        Timber.d("NativeBle: scan stopped")
    }

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        if (!hasBluetoothPermissions()) {
            _status.value = GlassesStatus.Error("Bluetooth permissions not granted")
            return
        }

        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            _status.value = GlassesStatus.Error("Invalid device address: $address")
            return
        }

        lastConnectedAddress = address
        _status.value = GlassesStatus.Connecting(device.name ?: address)
        Timber.d("NativeBle: connecting to $address")

        bleGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        Timber.d("NativeBle: disconnect")
        bleGatt?.disconnect()
        bleGatt?.close()
        bleGatt = null
        _status.value = GlassesStatus.Disconnected
    }

    // -------------------------------------------------------------------------
    // GATT Callback
    // -------------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val name = gatt?.device?.name ?: "Unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("NativeBle: connected to $name, discovering services")
                    _status.value = GlassesStatus.Connected(name, null)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("NativeBle: disconnected from $name")
                    bleGatt = null
                    _status.value = GlassesStatus.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("NativeBle: service discovery failed, status=$status")
                return
            }

            Timber.d("NativeBle: services discovered")

            // Log available services for debugging
            gatt?.services?.forEach { service ->
                Timber.d("  Service: ${service.uuid}")
                service.characteristics.forEach { char ->
                    Timber.d("    Char: ${char.uuid} props=${char.properties}")
                }
            }

            // TODO: Once HeyCyanProtocol.NOTIFY_CHARACTERISTIC_UUID is confirmed,
            // subscribe to notifications:
            //
            // val service = gatt?.getService(HeyCyanProtocol.SERVICE_UUID) ?: return
            // val notifyChar = HeyCyanProtocol.NOTIFY_CHARACTERISTIC_UUID?.let {
            //     service.getCharacteristic(it)
            // } ?: return
            // gatt.setCharacteristicNotification(notifyChar, true)
            // val descriptor = notifyChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            // descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            // gatt.writeDescriptor(descriptor)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value ?: return
            val uuid = characteristic.uuid ?: return
            handleNotification(uuid, value)
        }
    }

    private fun handleNotification(uuid: UUID, data: ByteArray) {
        Timber.d("NativeBle: notification from $uuid data=${data.toHexString()}")
        // TODO: Once HeyCyanProtocol.parseNotification() is wired, call it here:
        // try {
        //     val notification = HeyCyanProtocol.parseNotification(data)
        //     handleParsedNotification(notification)
        // } catch (e: ProtocolNotWiredException) {
        //     Timber.w("Protocol not wired: ${e.message}")
        // }
    }

    // -------------------------------------------------------------------------
    // Commands — delegated to HeyCyanProtocol (all throw until wired)
    // -------------------------------------------------------------------------

    override suspend fun takePhoto() {
        throw ProtocolNotWiredException(
            "NativeBle.takePhoto: waiting for HeyCyanProtocol.encodeTakePhoto() to be confirmed"
        )
    }

    override suspend fun startRecording() {
        throw ProtocolNotWiredException(
            "NativeBle.startRecording: waiting for HeyCyanProtocol.encodeStartRecording()"
        )
    }

    override suspend fun stopRecording() {
        throw ProtocolNotWiredException(
            "NativeBle.stopRecording: waiting for HeyCyanProtocol.encodeStopRecording()"
        )
    }

    override suspend fun getBatteryLevel(): Int? {
        // TODO: implement via HeyCyanProtocol.encodeGetBattery() once confirmed
        return null
    }

    @SuppressLint("MissingPermission")
    override fun release() {
        activeScanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        activeScanCallback = null
        bleGatt?.close()
        bleGatt = null
        _status.value = GlassesStatus.Disconnected
        Timber.d("NativeBle: released")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02x".format(it) }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
