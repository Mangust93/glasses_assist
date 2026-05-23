# CODEX_REVIEW.md

## Найденные проблемы

- `DeviceSyncManager` стартовал только через `AssistantViewModel`, поэтому app-level sync мог не запускаться при открытии других экранов.
- `DeviceSyncManager.release()` не отменял корневой scope, а collect из `init` не был сохранен в `Job`.
- `NativeBleGlassesController` терял ссылку на `BluetoothGatt` при disconnect callback без `close()` и не закрывал старый GATT перед новым `connectGatt`.
- `connectToDevice()` сохранял BLE address до подтвержденного подключения.
- `gradlew.bat` мог содержать trailing whitespace.

## Исправления

- `CyanBridgeApplication` теперь инжектит singleton `DeviceSyncManager`, что создает sync layer на уровне приложения.
- В `DeviceSyncManager` добавлен `managerJob = SupervisorJob()`, сохранен `modeObserverJob`, а `release()` отменяет observer jobs и корневой job.
- Раннее сохранение address из `connectToDevice()` удалено; адрес сохраняется только при `GlassesStatus.Connected`.
- В `NativeBleGlassesController` старый `BluetoothGatt` закрывается перед новым подключением, а disconnected callback закрывает GATT resource перед очисткой ссылки.
- `gradlew.bat` нормализован от trailing whitespace.

## Проверки

- `git diff --check` — PASS.
- `./gradlew test --no-daemon --no-watch-fs` — PASS (`BUILD SUCCESSFUL`; unit-test source отсутствует, Gradle отметил `NO-SOURCE`).
- `./gradlew assembleDebug --no-daemon --no-watch-fs` — завис на `:app:mergeExtDexDebug`; процесс остановлен, чтобы не крутить бесконечно. Это совпадает с известным окруженческим зависанием.
