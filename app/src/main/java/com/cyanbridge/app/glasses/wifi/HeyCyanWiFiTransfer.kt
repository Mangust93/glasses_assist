package com.cyanbridge.app.glasses.wifi

/**
 * Wi-Fi media transfer layer for HeyCyan smart glasses.
 *
 * STATUS: TODO — structure documented, implementation pending.
 *
 * ── TRANSFER FLOW ────────────────────────────────────────────────────────────
 *
 * 1. TRIGGER (BLE)
 *    Send HeyCyanProtocol.CMD_WIFI_TRANSFER_START via BLE to wake device hotspot.
 *    Device returns SSID + password in BLE notification (or via openWifiWithMode response).
 *    NOTE: official SDK sometimes returns wrong credentials — fallback password: "123456789"
 *
 * 2. HOTSPOT CONNECTION (Android)
 *    Build WifiNetworkSpecifier with SSID + WPA2 password from step 1.
 *    Request network via ConnectivityManager.requestNetwork().
 *    CRITICAL on Samsung devices: call ConnectivityManager.bindProcessToNetwork(network)
 *    after onAvailable() — otherwise HTTP requests still go through mobile data.
 *
 * 3. DEVICE IP DISCOVERY
 *    Primary IP (Android hotspot default): 192.168.43.1
 *    Fallback candidates in order:
 *      192.168.4.1, 192.168.31.1, 192.168.1.1, 192.168.0.1,
 *      192.168.100.1, 192.168.123.1, 192.168.137.1, 10.0.0.1, 172.20.10.1
 *    Discovery: GET http://{ip}/files/media.config  (timeout 3–5 s, expect HTTP 200)
 *
 * 4. MEDIA MANIFEST
 *    Primary:   GET http://{ip}/files/media.config
 *      Format:  plain text, one filename per line; lines starting with # are comments
 *    Secondary: GET http://{ip}/manifest.json
 *      Format:  { "files": [{ "filename": "IMG_001.jpg", "type": "image" }] }
 *    Supported types: jpeg/png/heic (image), mp4/mov/m4v (video), opus (audio)
 *
 * 5. FILE DOWNLOAD
 *    GET http://{ip}/files/{filename}   (port 80, no HTTPS)
 *    Headers: Cache-Control: no-cache
 *    Requires usesCleartextTraffic="true" (see AndroidManifest + network_security_config.xml)
 *
 * 6. STORAGE
 *    Save to app-specific internal storage (not gallery).
 *    Gallery export is a secondary optional function.
 *    Suggested path: context.filesDir / "glasses_media" / filename
 *
 * ── ANDROID REQUIREMENTS ─────────────────────────────────────────────────────
 *    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 *    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
 *    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
 *    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 *
 * ── LOGCAT TAGS (from FerSaiyan SDK research) ─────────────────────────────────
 *    DataDownload  DeviceNotify  WifiP2pManagerSingleton
 *    WifiP2pBroadcastReceiver  BleIpBridge
 */
object HeyCyanWiFiTransfer {

    // TODO: implement Wi-Fi hotspot connection via WifiNetworkSpecifier
    // TODO: implement IP discovery loop with 3-5s timeout per candidate
    // TODO: parse media.config (plain text) and manifest.json
    // TODO: download files via OkHttp on the bound network
    // TODO: save to app-specific storage
    // TODO: emit progress via Flow<TransferProgress>

    /**
     * Candidate device IPs to probe during discovery.
     * First match responding to GET /files/media.config wins.
     */
    val CANDIDATE_IPS: List<String> = listOf(
        "192.168.43.1",
        "192.168.4.1",
        "192.168.31.1",
        "192.168.1.1",
        "192.168.0.1",
        "192.168.100.1",
        "192.168.123.1",
        "192.168.137.1",
        "10.0.0.1",
        "172.20.10.1"
    )

    /** Hardcoded fallback password when device returns wrong credentials. */
    const val WIFI_PASSWORD_FALLBACK: String = "123456789"

    const val MEDIA_CONFIG_PATH: String = "/files/media.config"
    const val MANIFEST_JSON_PATH: String = "/manifest.json"
    const val FILES_BASE_PATH: String = "/files/"
}
