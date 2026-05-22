# PROJECT_STATUS.md — CyanBridge Android App
**Итерация 1 | Дата:** 2026-05-21

---

## ✅ Что реализовано

### Архитектура
- [x] MVVM + Clean Architecture (domain / data / ui / network / glasses / voice)
- [x] Hilt DI (SingletonComponent) — все зависимости инжектируются
- [x] Kotlin Coroutines + StateFlow по всей вертикали
- [x] Package: `com.cyanbridge.app`

### Доменный слой
- [x] Интерфейс `GlassesController`
- [x] Интерфейс `VoiceRecorder` (с `amplitudeFlow` для анимации)
- [x] Интерфейс `HermesApiClient`
- [x] Интерфейс `SettingsRepository`
- [x] Интерфейс `VoiceInteractionRepository`
- [x] Интерфейс `NotesRepository`
- [x] Интерфейс `MediaRepository`
- [x] Доменные модели: `VoiceInteraction`, `Note`, `MediaItem`, `GlassesStatus`, `HermesHealth`, `VoiceResponse`

### Слой данных
- [x] Room database `CyanBridgeDatabase` (3 таблицы)
- [x] Entities: `VoiceInteractionEntity`, `NoteEntity`, `MediaItemEntity`
- [x] DAOs с Flow-запросами
- [x] `SettingsRepositoryImpl` на DataStore Preferences
- [x] `VoiceInteractionRepositoryImpl`
- [x] `NotesRepositoryImpl`
- [x] `MediaRepositoryImpl`

### Сетевой слой
- [x] Retrofit API интерфейс `HermesApi`
- [x] `DynamicBaseUrlInterceptor` — URL меняется в runtime из настроек без перезапуска
- [x] `HermesApiClientImpl` — реализация всех методов с корректной обработкой ошибок
- [x] Multipart upload для аудио (`POST /api/voice/ask`)
- [x] Multipart upload для медиа (`POST /api/media/upload`)
- [x] OkHttp с логированием и таймаутами

### Слой очков
- [x] `FakeGlassesController` — живая симуляция: scan → connecting → connected, Battery drain tick
- [x] `NativeBleGlassesController` — честный BLE-скелет:
  - BLE scan с фильтром по имени "HeyCyan"
  - GATT connect/disconnect
  - Service discovery с логированием всех UUID
  - Notification subscribe (закомментирован до получения UUID)
  - `onCharacteristicChanged` для API 33+ и legacy API
- [x] `HeyCyanProtocol` — все методы с `ProtocolNotWiredException`
- [x] `ProtocolNotWiredException` — чёткое сообщение "не выдумываем"

### Голосовой слой
- [x] `VoiceRecorderImpl` — MediaRecorder, MPEG_4/AAC, 16kHz, амплитудный поллинг
- [x] `AudioPlayer` — Media3 ExoPlayer, play from URL / File, статусы

### UI
- [x] Material 3, тёмная тема, cyan-акцент
- [x] Bottom Navigation (4 экрана)
- [x] `AssistantScreen` + `AssistantViewModel`:
  - StateMachine: IDLE → RECORDING → UPLOADING → PROCESSING → PLAYING → ERROR
  - Push-to-Talk кнопка с `detectTapGestures` (press/release)
  - Пульсирующая анимация при записи
  - Отображение transcript + answer
  - Кнопка воспроизведения аудио-ответа
  - Chips: статус Hermes + статус очков + режим
- [x] `NotesScreen` + `NotesViewModel`:
  - Список заметок из Room
  - Детальный экран заметки с AI Summary
  - Синхронизация с Hermes
- [x] `MediaScreen` + `MediaViewModel`:
  - Grid-список медиафайлов
  - Статус синхронизации
  - Upload файла через URI picker
- [x] `SettingsScreen` + `SettingsViewModel`:
  - Hermes Base URL с live-проверкой соединения
  - Переключатель Fake / Native BLE
  - STT язык (ru / en / auto)
  - TTS voice
  - Debug mode
  - Очистка локальной истории
- [x] `StatusChip`, `ErrorCard`, `LoadingIndicator` — shared компоненты

### Разрешения Android
- [x] RECORD_AUDIO
- [x] CAMERA
- [x] BLUETOOTH_SCAN + BLUETOOTH_CONNECT (API 31+)
- [x] BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION (API < 31)
- [x] READ_MEDIA_IMAGES/AUDIO/VIDEO (API 33+)
- [x] READ/WRITE_EXTERNAL_STORAGE (API < 33)

---

## ✅ Что работает (без Hermes)

1. Приложение открывается без очков
2. FakeGlassesController симулирует подключение очков
3. Статусы очков обновляются в реальном времени
4. Переключение Fake / Native BLE в настройках
5. Hermes URL сохраняется в DataStore
6. DynamicBaseUrlInterceptor применяет URL без перезапуска
7. Локальная история сохраняется в Room
8. Настройки персистентны между запусками
9. Темная тема Material 3 применена

---

## ⚠️ Что требует реального Hermes

| Функция | Зависимость |
|---|---|
| Распознавание речи | `POST /api/voice/ask` с whisper.cpp |
| Текстовый ответ | Flowise + OpenRouter через Hermes |
| Голосовой ответ | edge-tts через Hermes |
| Синхронизация заметок | `GET /api/notes` |
| Синхронизация медиа | `GET /api/media` |
| Проверка health | `GET /api/health` |

---

## ⛔ Что требует протокола очков

### Файл: `HeyCyanProtocol.kt`
Все методы бросают `ProtocolNotWiredException`:

| Метод | Статус |
|---|---|
| `encodeTakePhoto()` | ❌ байты неизвестны |
| `encodeStartRecording()` | ❌ байты неизвестны |
| `encodeStopRecording()` | ❌ байты неизвестны |
| `encodeGetBattery()` | ❌ байты неизвестны |
| `parseNotification()` | ❌ формат неизвестен |
| `COMMAND_CHARACTERISTIC_UUID` | ❌ UUID = null |
| `NOTIFY_CHARACTERISTIC_UUID` | ❌ UUID = null |

### Файл: `NativeBleGlassesController.kt`
Скелет BLE готов, но subscribe к нотификациям закомментирован до получения UUID характеристик.

---

## 📋 TODO для следующей итерации

### Итерация 2 — Hermes Backend
- [ ] FastAPI + whisper.cpp + edge-tts
- [ ] PostgreSQL + история
- [ ] Flowise интеграция
- [ ] Docker Compose
- [ ] Реальный `GET /api/health` с версией

### Итерация 3 — Медиа
- [ ] CameraX для съёмки с телефона
- [ ] Audio/Video capture
- [ ] Accompanist Permissions runtime flow
- [ ] Локальная галерея

### Итерация 4 — Очки
- [ ] Раскомментировать и реализовать HeyCyanProtocol
- [ ] Реальные UUID из manufacturer-original
- [ ] Command queue с retry
- [ ] WiFi media sync

---

## 📂 Что нужно прислать для подключения протокола

Чтобы реализовать `HeyCyanProtocol.kt` полностью, нужны:

1. **Файлы из ветки `manufacturer-original`:**
   - `*Ble*.kt` / `*Ble*.java` — BLE logic
   - `*Protocol*.kt` / `*Command*.kt` — command definitions
   - `*Service*.kt` — GATT service handling

2. **Конкретные данные:**
   - Service UUID (возможно не `0000fff0-...`)
   - Command Characteristic UUID (write)
   - Notify Characteristic UUID (notify/indicate)
   - Байтовые последовательности: take photo, start rec, stop rec, get battery
   - Формат notification: offset 0 = opcode, offset 1+ = payload?

3. **Firmware version** — протокол может отличаться между версиями прошивки

4. **Любые APK** от производителя для BLE-анализа через nRF Connect или Wireshark BLE capture

---

## 🔧 Как запустить

```bash
cd CyanBridge
./gradlew assembleDebug
```

Или открыть в Android Studio → Run on device/emulator.

**Минимальные требования:**
- Android 8.0+ (API 26)
- Target: API 34

**Для Hermes:** установить базовый URL в Settings → Hermes Backend.  
Без Hermes приложение работает в Fake-режиме.
