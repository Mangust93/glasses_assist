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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GlassesController for HEYCYAN_SDK mode.
 *
 * When [HeyCyanSdkBridge.isAarAvailable] is true (AAR present in app/libs/), all operations
 * delegate to [HeyCyanSdkBridge]. The controller observes [HeyCyanSdkBridge.diagnosticsState]
 * and maps it to [GlassesStatus] for the rest of the app.
 *
 * Native BLE code below is kept as an unreachable fallback — it activates only when
 * [bridge.isAarAvailable()] returns false (e.g. AAR removed at build time).
 *
 * Target device: CY 01_24E5  (MAC: 91:8E:55:C7:24:E5)
 */
@Singleton
class HeyCyanSdkGlassesController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: HeyCyanSdkBridge
) : GlassesController {

    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter

    private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.Disconnected)
    override val status: Flow<GlassesStatus> = _status.asStateFlow()

    // Native BLE fields — used only when bridge is not available
    private var bleGatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var activeScanCallback: ScanCallback? = null

    var lastConnectedAddress: String? = null
        private set

    init {
        if (bridge.isAarAvailable()) {
            Timber.d("HeyCyanSdk: AAR bridge available; observing diagnosticsState")
            controllerScope.launch {
                bridge.diagnosticsState.collect { diag ->
                    _status.value = diag.toGlassesStatus()
                    if (diag.connected) {
                        lastConnectedAddress = lastConnectedAddress // keep existing address
                    }
                }
            }
        } else {
            Timber.w("HeyCyanSdk: AAR not present; SDK mode will report unavailable")
        }
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        if (bridge.isAarAvailable()) {
            _status.value = GlassesStatus.Scanning
            runCatching { bridge.startScan() }
                .onFailure {
                    _status.value = GlassesStatus.Error(it.message ?: "Scan error")
                }
            return
        }
        // Fallback: native BLE (only reached when AAR is absent)
        ensureAarAvailable()
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        if (bridge.isAarAvailable()) {
            runCatching { bridge.stopScan() }
            if (_status.value is GlassesStatus.Scanning) _status.value = GlassesStatus.Disconnected
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        activeScanCallback?.let { scanner?.stopScan(it) }
        activeScanCallback = null
        if (_status.value is GlassesStatus.Scanning) _status.value = GlassesStatus.Disconnected
    }

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    override suspend fun connect(address: String) {
        if (bridge.isAarAvailable()) {
            lastConnectedAddress = address
            runCatching { bridge.connectToDevice(address) }
                .onFailure { _status.value = GlassesStatus.Error(it.message ?: "Connect error") }
            return
        }
        // Fallback: native GATT (only reached when AAR is absent)
        ensureAarAvailable()
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        if (bridge.isAarAvailable()) {
            runCatching { bridge.disconnect() }
            return
        }
        Timber.d("HeyCyanSdk: native disconnect")
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
        if (bridge.isAarAvailable()) {
            runCatching { bridge.takePhoto() }
                .onFailure { _status.value = GlassesStatus.Error(it.message ?: "takePhoto error") }
            return
        }
        ensureAarAvailable()
        sendNativeCommand(HeyCyanProtocol.CMD_TAKE_PHOTO)
    }

    override suspend fun startRecording() {
        if (bridge.isAarAvailable()) {
            runCatching { bridge.startVideoRecording() }
                .onFailure { _status.value = GlassesStatus.Error(it.message ?: "startRec error") }
            return
        }
        ensureAarAvailable()
        sendNativeCommand(HeyCyanProtocol.CMD_VIDEO_START)
    }

    override suspend fun stopRecording() {
        if (bridge.isAarAvailable()) {
            runCatching { bridge.stopVideoRecording() }
                .onFailure { _status.value = GlassesStatus.Error(it.message ?: "stopRec error") }
            return
        }
        ensureAarAvailable()
        sendNativeCommand(HeyCyanProtocol.CMD_VIDEO_STOP)
    }

    override suspend fun getBatteryLevel(): Int? {
        if (bridge.isAarAvailable()) return bridge.getBatteryLevel()
        ensureAarAvailable()
        return null
    }

    @SuppressLint("MissingPermission")
    override fun release() {
        if (bridge.isAarAvailable()) {
            runCatching { bridge.release() }
            return
        }
        activeScanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        activeScanCallback = null
        bleGatt?.close()
        bleGatt = null
        commandChar = null
        _status.value = GlassesStatus.Disconnected
        Timber.d("HeyCyanSdk: native released")
    }

    // -------------------------------------------------------------------------
    // Status mapping
    // -------------------------------------------------------------------------

    private fun SdkDiagnosticsState.toGlassesStatus(): GlassesStatus = when {
        !sdkInitialized -> GlassesStatus.Disconnected
        !connected && !ready -> GlassesStatus.Disconnected
        connected && ready -> GlassesStatus.Connected("HeyCyan SDK", battery)
        connected -> GlassesStatus.Connecting("HeyCyan SDK")
        else -> GlassesStatus.Disconnected
    }

    // -------------------------------------------------------------------------
    // Native BLE fallback — only active when bridge.isAarAvailable() == false
    // -------------------------------------------------------------------------

    private fun ensureAarAvailable() {
        if (bridge.isAarAvailable()) return
        val message = "HeyCyan SDK AAR is not available. Place glasses_sdk_20250723_v01.aar " +
            "in app/libs/ before using HEYCYAN_SDK mode."
        _status.value = GlassesStatus.Error(message)
        throw SdkNotAvailableException(message)
    }

    @SuppressLint("MissingPermission")
    private fun doNativeConnect(device: BluetoothDevice) {
        val name = runCatching { device.name }.getOrNull() ?: device.address
        lastConnectedAddress = device.address
        _status.value = GlassesStatus.Connecting(name)
        closeGatt()
        bleGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val name = runCatching { gatt?.device?.name } .getOrNull() ?: "Unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("HeyCyanSdk: native connected $name")
                    _status.value = GlassesStatus.Connected(name, null)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("HeyCyanSdk: native disconnected $name")
                    commandChar = null
                    if (bleGatt == gatt) closeGatt() else gatt?.close()
                    _status.value = GlassesStatus.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt?.getService(HeyCyanProtocol.SERVICE_UUID)
                ?: gatt?.getService(HeyCyanProtocol.SECONDARY_SERVICE_UUID) ?: return

            commandChar = service.characteristics.firstOrNull { c ->
                c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }

            service.characteristics.firstOrNull { c ->
                c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }?.let { nc ->
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) = handleNotification(value)

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
        Timber.d("HeyCyanSdk: native notify ${data.toHexString()}")
        val updated = HeyCyanSdkStateMapper.mapNotification(data, _status.value)
        if (updated != null) _status.value = updated
    }

    @SuppressLint("MissingPermission")
    private fun sendNativeCommand(bytes: ByteArray) {
        val gatt = bleGatt ?: return
        val char = commandChar ?: return
        Timber.d("HeyCyanSdk: native → ${bytes.toHexString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    private fun closeGatt() {
        bleGatt?.close()
        bleGatt = null
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
