# app/libs — HeyCyan SDK AAR

## Required file

```
glasses_sdk_20250723_v01.aar  (387 KB)
```

## Where to get it

1. **ebowwa/HeyCyanSmartGlassesSDK** — `android/GlassesSDKSample/app/libs/glasses_sdk_20250723_v01.aar`
2. **FerSaiyan/Alternative-HeyCyan-App-and-SDK** — `android/CyanBridge/app/libs/glasses_sdk_20250723_v01.aar`

## After placing the AAR

1. `./gradlew assembleDebug` - the fileTree dependency in `build.gradle.kts` picks it up automatically.
2. Open `HeyCyanSdkBridgeImpl.kt` and replace each `notAvailable()` call with the corresponding
   SDK call (exact signatures are documented in the file header).
3. Set `isAarAvailable()` to return `true` in `HeyCyanSdkBridgeImpl`.
4. Initialize the SDK in `CyanBridgeApplication.onCreate()`:
   ```kotlin
   BleBaseControl.setmContext(this)
   BleOperateManager.getInstance().init()
   LargeDataHandler.getInstance()
   LocalBroadcastManager.getInstance(this)
       .registerReceiver(bleReceiver, BleAction.getIntentFilter())
   ```

Until this file is added and `HeyCyanSdkBridgeImpl` is wired, `HEYCYAN_SDK` mode reports
`SdkNotAvailableException`. Use `NATIVE_BLE_DIAGNOSTIC` for raw BLE/GATT discovery.

## Gradle dependency (already in build.gradle.kts)

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```
