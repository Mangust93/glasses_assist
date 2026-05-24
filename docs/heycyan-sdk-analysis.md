# HeyCyan Smart Glasses SDK Analysis

**Target device:** CY 01_24E5  (BLE MAC: `91:8E:55:C7:24:E5`)  
**Research sources:**
- `ebowwa/HeyCyanSmartGlassesSDK`
- `FerSaiyan/Alternative-HeyCyan-App-and-SDK`

**Verification status:** this repository does not include
`app/libs/glasses_sdk_20250723_v01.aar`, a local SDK sample, or a real GATT dump from
CY 01_24E5. UUIDs, command bytes, and notification frames below are candidates from
external source analysis and remain pending local AAR/sample and real-device verification.

---

## AAR File

| Field | Value |
|---|---|
| Filename | `glasses_sdk_20250723_v01.aar` |
| Size | 387 KB |
| Location in repos | `android/GlassesSDKSample/app/libs/` or `android/CyanBridge/app/libs/` |
| Placement in CyanBridge | `app/libs/glasses_sdk_20250723_v01.aar` |

Build dependency (already in `app/build.gradle.kts`):
```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```

---

## BLE Service UUID Candidates

| Constant | UUID | Notes |
|---|---|---|
| `SERVICE_UUID` (primary) | `7905fff0-b5ce-4e99-a40f-4b1e122d00d0` | Candidate from `QCSDKSERVERUUID1` in iOS framework |
| `SECONDARY_SERVICE_UUID` | `6e40fff0-b5a3-f393-e0a9-e50e24dcca9e` | Candidate from `QCSDKSERVERUUID2`, Nordic UART-pattern |

**Characteristic UUIDs** are NOT directly exposed in SDK headers.  
`HeyCyanSdkGlassesController` auto-discovers them by property flags (PROPERTY_WRITE, PROPERTY_NOTIFY).  
Use `NATIVE_BLE_DIAGNOSTIC` mode to log real UUIDs from the device, then fill in
`HeyCyanProtocol.COMMAND_CHARACTERISTIC_UUID` and `NOTIFY_CHARACTERISTIC_UUID`.

---

## BLE Command Byte Candidates

Candidates attributed to `LargeDataHandler.glassesControl(ByteArray)` calls in external
SDK/sample notes. They are not confirmed in this repository until the AAR/sample is added
locally and verified against the real device:

| Action | Bytes (hex) |
|---|---|
| Camera mode (viewfinder) | `02 01 01` |
| Take photo (mode + capture) | `02 01 06 02 02` |
| Start video | `02 01 02` |
| Stop video | `02 01 03` |
| Start audio | `02 01 08` |
| Stop audio | `02 01 0C` |
| Wi-Fi transfer mode | `02 01 04` |
| Stop Wi-Fi transfer | `02 01 09` |
| Stop AI stream | `02 01 0B` |
| P2P reset | `02 01 0F` |
| Query media count | `02 04` |

---

## BLE Notification Frame Candidates

| byte[6] | Meaning | Data |
|---|---|---|
| `0x08` | Candidate: device Wi-Fi IP assigned | bytes [7..10] = IPv4 octets |
| `0x09` | Candidate: P2P/Wi-Fi error | byte[7] == 0xFF is reported as common and non-fatal |

Example IP extraction:
```kotlin
if (data[6].toInt() and 0xFF == 0x08 && data.size >= 11) {
    val ip = "${data[7].toInt() and 0xFF}.${data[8].toInt() and 0xFF}" +
             ".${data[9].toInt() and 0xFF}.${data[10].toInt() and 0xFF}"
}
```

---

## Android SDK Key Classes (from AAR)

### Initialization order
```kotlin
BleBaseControl.setmContext(context)
BleOperateManager.getInstance().init()
LargeDataHandler.getInstance()
LocalBroadcastManager.getInstance(context)
    .registerReceiver(receiver, BleAction.getIntentFilter())
```

### `LargeDataHandler` (command hub)
```kotlin
LargeDataHandler.getInstance().glassesControl(ByteArray)     // all capture commands
LargeDataHandler.getInstance().syncTime()
LargeDataHandler.getInstance().getPictureThumbnails { data -> }
LargeDataHandler.getInstance().writeIpToSoc(url, callback)   // OTA
LargeDataHandler.getInstance().addOutDeviceListener(cmdType = 100) { cmdType, response ->
    // response.imageCount, response.videoCount, response.recordCount
    // response.dataType, response.errorCode, response.workTypeIng
}
```

### `BleOperateManager` (connection)
```kotlin
BleOperateManager.getInstance().connectDirectly("91:8E:55:C7:24:E5")
BleOperateManager.getInstance().unBindDevice()
BleOperateManager.getInstance().classicBluetoothStartScan()
```

### `BleScannerHelper` (scan)
```kotlin
BleScannerHelper.getInstance().scanDevice(context, null, bleScanCallback)
// 15s window, max 30 devices
```

### `QCBluetoothCallbackCloneReceiver` (base class for BLE events)
```kotlin
override fun connectStatue(device: BluetoothDevice, connected: Boolean)
override fun onServiceDiscovered()
override fun onCharacteristicChange(address: String, uuid: UUID, data: ByteArray)
override fun onCharacteristicRead(uuid: UUID, data: ByteArray)
```

### Device name patterns (DeviceClassifier)
```kotlin
name.lowercase().contains("heycyan") ||
name.lowercase().contains("cyan") ||
name.startsWith("O_") ||
name.startsWith("Q_")
// → DeviceClass.HEY_CYAN
```

---

## iOS SDK (QCSDK.framework)

### `QCCentralManager` — scan/connect
```objc
[QCCentralManager shared].scan
[QCCentralManager shared] connect:peripheral timeout:15 deviceType:QCDeviceTypeGlasses
```

### `QCSDKCmdCreator` — all commands
```objc
[QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModePhoto success:^{} fail:^{}]
[QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModeVideo ...]
[QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModeVideoStop ...]
[QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModeAudio ...]
[QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModeAudioStop ...]
[QCSDKCmdCreator openWifiWithMode:QCOperatorDeviceModeTransfer
                          success:^(NSString *ssid, NSString *password) {}
                             fail:^{}]
[QCSDKCmdCreator getDeviceWifiIPSuccess:^(NSString *ip) {} failed:^{}]
```

---

## Wi-Fi Media Transfer

### Flow
1. BLE: send `02 01 04` → device activates hotspot
2. BLE notify frame (byte[6]==0x08) → parse IP
3. Connect phone to device hotspot:
   - Android: `WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork()`
   - **Critical on Samsung:** `ConnectivityManager.bindProcessToNetwork(network)`
4. Discover device IP: probe `CANDIDATE_IPS` with `GET /files/media.config` (3–5s timeout)
5. Parse manifest, download files
6. Save to app-specific storage

### HTTP Endpoints
| URL | Purpose |
|---|---|
| `http://{ip}/files/media.config` | Plain-text manifest (one filename per line) |
| `http://{ip}/manifest.json` | JSON manifest (alternative) |
| `http://{ip}/files/{filename}` | Binary file download |

### Manifest formats
```
# media.config (plain text)
IMG_001.jpg
VID_001.mp4
AUD_001.opus
```
```json
{
  "files": [
    { "filename": "IMG_001.jpg", "type": "image" },
    { "filename": "VID_001.mp4", "type": "video" }
  ]
}
```

### Candidate IPs (priority order)
```
192.168.43.1  ← Android hotspot default (most likely)
192.168.4.1
192.168.31.1
192.168.1.1
192.168.0.1
192.168.100.1
192.168.123.1
192.168.137.1
10.0.0.1
172.20.10.1
```

### Wi-Fi password
SDK may return wrong credentials. Hardcoded fallback: **`123456789`**

---

## Confirmed Public API (from javap on glasses_sdk_20250723_v01.aar)

> **Note:** The AAR file (`glasses_sdk_20250723_v01.aar`) is present locally in `app/libs/`
> but is excluded from Git via `.git/info/exclude`. Do NOT commit it.

| Class | Method | Notes |
|---|---|---|
| `SDKInit` | `getInstance()`, `setSDKType(int)`, `SDK_TYPE_QC`, `SDK_TYPE_MY` | Init type |
| `BleBaseControl` | `getInstance(Context)`, `setmContext(Context)` | Context setup |
| `BleOperateManager` | `getInstance(Application)`, `getInstance()`, `setCallback(OnGattEventCallback)`, `connectDirectly(String)`, `connectWithScan(String)`, `disconnect()`, `isConnected()`, `isReady()` | BLE connection |
| `BleScannerHelper` | `getInstance()`, `scanDevice(Context, UUID, ScanWrapperCallback)`, `scanTheDevice(Context, String, OnTheScanResult)`, `stopScan(Context)` | BLE scan |
| `LargeDataHandler` | `getInstance()`, `initEnable()`, `disEnable()`, `syncBattery()`, `addBatteryCallBack(String, ILargeDataResponse<BatteryResponse>)`, `removeBatteryCallBack(String)`, `syncDeviceInfo(ILargeDataResponse<DeviceInfoResponse>)`, `glassesControl(byte[], ILargeDataResponse<GlassModelControlResponse>)`, `getPictureThumbnails(ILargeDataImageResponse)` | Commands |
| `CameraReq` | `CameraReq(byte)`, `getData()` (from BaseReqCmd), `ACTION_INTO_CAMARA_UI=4`, `ACTION_KEEP_SCREEN_ON=5`, `ACTION_FINISH=6` | Camera commands |
| `GlassModelControlReq` | `GlassModelControlReq(int, int)`, `getData()` (from BaseReqCmd) | Media/mode control |
| `BatteryResponse` | `getBattery()`, `isCharging()` | Battery data |
| `DeviceInfoResponse` | `getFirmwareVersion()`, `getHardwareVersion()`, `getWifiFirmwareVersion()`, `getWifiHardwareVersion()` | Device info |
| `GlassModelControlResponse` | `getDataType()`, `getGlassWorkType()`, `getImageCount()`, `getVideoCount()`, `getRecordCount()`, `getP2pIp()`, `getErrorCode()`, `getWorkTypeIng()`, `getVideoAngle()`, `getVideoDuration()`, `getOtaStatus()` | Media/mode response |

### GlassModelControlReq — Experimental mapping

`GlassModelControlReq(param1, param2)` produces `subData = [2, param1, param2]` (from bytecode).
**param1/param2 semantic mapping has NOT been verified on real CY 01_24E5 hardware.**
Current candidates used in `HeyCyanSdkBridgeImpl`:

| Command | param1 | param2 | Basis |
|---|---|---|---|
| Video start | 1 | 2 | Candidate from CMD_VIDEO_START byte 0x02 |
| Video stop | 1 | 3 | Candidate from CMD_VIDEO_STOP byte 0x03 |
| Audio start | 1 | 8 | Candidate from CMD_AUDIO_START byte 0x08 |
| Audio stop | 1 | 12 | Candidate from CMD_AUDIO_STOP byte 0x0C |
| Wi-Fi start | 1 | 4 | Candidate from CMD_WIFI_TRANSFER_START byte 0x04 |
| Media query | 0 | 0 | Speculative — unconfirmed |

All experimental commands require explicit user confirmation before use in production flows.

---

## CyanBridge Integration Status

| Component | File | Status |
|---|---|---|
| GlassesMode enum | `domain/model/GlassesStatus.kt` | ✅ FAKE / NATIVE_BLE_DIAGNOSTIC / HEYCYAN_SDK |
| BLE protocol constants | `glasses/protocol/HeyCyanProtocol.kt` | ⚠️ candidate service UUIDs + command bytes, pending verification |
| SDK adapter interface | `glasses/sdk/HeyCyanSdkBridge.kt` | ✅ real SDK methods + diagnosticsState |
| SDK adapter impl | `glasses/sdk/HeyCyanSdkBridgeImpl.kt` | ✅ real SDK calls via AAR |
| SDK diagnostics state | `glasses/sdk/SdkDiagnosticsState.kt` | ✅ full diagnostic data class |
| State mapper | `glasses/sdk/HeyCyanSdkStateMapper.kt` | ✅ notification frame parsing |
| SDK controller | `glasses/sdk/HeyCyanSdkGlassesController.kt` | ✅ delegates to bridge; observes diagnosticsState |
| Settings diagnostics | `ui/screens/settings/SettingsScreen.kt` | ✅ SDK diagnostics panel in HEYCYAN_SDK mode |
| Settings ViewModel | `ui/screens/settings/SettingsViewModel.kt` | ✅ SDK action methods |
| Wi-Fi transfer | `glasses/wifi/HeyCyanWiFiTransfer.kt` | 📋 TODO structure |
| Device sync | `glasses/sync/DeviceSyncManager.kt` | ✅ 3-mode support |
| DI binding | `core/di/GlassesModule.kt` | ✅ bridge binding |
| AAR dependency | `app/build.gradle.kts` | ✅ fileTree in libs/ |
| AAR file | `app/libs/glasses_sdk_20250723_v01.aar` | ✅ present locally, excluded from Git |
