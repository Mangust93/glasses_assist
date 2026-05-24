# Codex SDK Review

## Found Issues

- `HEYCYAN_SDK` mode looked production-ready even though `app/libs/glasses_sdk_20250723_v01.aar` is absent. The controller also attempted native raw BLE command writes without the official SDK, which could be mistaken for a working SDK integration.
- `HeyCyanProtocol.kt`, `HeyCyanSdkGlassesController.kt`, `HeyCyanSdkStateMapper.kt`, and `docs/heycyan-sdk-analysis.md` described service UUIDs, command bytes, and notification frames as confirmed. The repository does not contain the AAR/sample or a real CY 01_24E5 GATT capture, so these must remain candidate / pending verification.
- `AndroidManifest.xml` had `android:usesCleartextTraffic="true"`, which was broader than the local hotspot IP allowlist in `network_security_config.xml`.

## Fixes

- Changed SDK-mode behavior so operations fail explicitly with `SdkNotAvailableException` and a `GlassesStatus.Error` when the AAR bridge is unavailable.
- Updated comments, UI copy, and SDK analysis docs to say the SDK adapter is prepared, the AAR is required, and UUID/command data is pending verification.
- Changed `usesCleartextTraffic` to `false` and kept cleartext enabled only for the candidate local hotspot IP domains in `network_security_config.xml`.
- Verified `app/libs/` contains only `README.md`; `glasses_sdk_20250723_v01.aar` is not present.

## Check Results

- `git diff --check`: passed
- `./gradlew test --no-daemon --no-watch-fs`: passed. Unit test tasks are currently `NO-SOURCE`; debug/release Kotlin and Java compilation completed during the test lifecycle.
- `timeout 300 ./gradlew assembleDebug --no-daemon --no-watch-fs`: failed with exit code 124 after hanging at `:app:mergeExtDexDebug`. Treated as an environment issue; the leftover Gradle daemon was stopped manually.

## Manual Follow-up

- Place `glasses_sdk_20250723_v01.aar` in `app/libs/`.
- Wire `HeyCyanSdkBridgeImpl` to the real SDK classes and initialization flow.
- Test on an Android phone.
- Verify real device `CY 01_24E5`.
- Capture real GATT services/characteristics and update `HeyCyanProtocol`.
- Verify media transfer over Wi-Fi/hotspot.
