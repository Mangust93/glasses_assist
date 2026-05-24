package com.cyanbridge.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.glasses.sdk.SdkDiagnosticsState
import com.cyanbridge.app.ui.theme.DarkCard
import com.cyanbridge.app.ui.theme.StatusError
import com.cyanbridge.app.ui.theme.StatusOnline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val hermesBaseUrl by viewModel.hermesBaseUrl.collectAsStateWithLifecycle()
    val glassesMode by viewModel.glassesMode.collectAsStateWithLifecycle()
    val sttLanguage by viewModel.sttLanguage.collectAsStateWithLifecycle()
    val ttsVoice by viewModel.ttsVoice.collectAsStateWithLifecycle()
    val isDebugMode by viewModel.isDebugMode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sdkDiagnostics by viewModel.sdkDiagnostics.collectAsStateWithLifecycle()

    var urlDraft by remember(hermesBaseUrl) { mutableStateOf(hermesBaseUrl) }
    var voiceDraft by remember(ttsVoice) { mutableStateOf(ttsVoice) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Hermes ----
            SettingsSection(title = "Hermes Backend") {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.1.100:8000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.setHermesBaseUrl(urlDraft)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.setHermesBaseUrl(urlDraft)
                            focusManager.clearFocus()
                            viewModel.checkHermesConnection()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        when (uiState.healthCheckState) {
                            HealthCheckState.CHECKING -> CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).padding(2.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            HealthCheckState.OK -> Icon(Icons.Default.CloudDone, contentDescription = null)
                            HealthCheckState.ERROR -> Icon(Icons.Default.CloudOff, contentDescription = null)
                            else -> Icon(Icons.Default.WifiFind, contentDescription = null)
                        }
                        Text("  Проверить")
                    }
                }

                uiState.healthMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.healthCheckState == HealthCheckState.OK) StatusOnline else StatusError
                    )
                }
            }

            // ---- Glasses ----
            SettingsSection(title = "Режим очков") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = glassesMode == GlassesMode.FAKE,
                        onClick = { viewModel.setGlassesMode(GlassesMode.FAKE) },
                        label = { Text("Fake") },
                        leadingIcon = if (glassesMode == GlassesMode.FAKE) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                    FilterChip(
                        selected = glassesMode == GlassesMode.NATIVE_BLE_DIAGNOSTIC,
                        onClick = { viewModel.setGlassesMode(GlassesMode.NATIVE_BLE_DIAGNOSTIC) },
                        label = { Text("BLE Диагностика") },
                        leadingIcon = if (glassesMode == GlassesMode.NATIVE_BLE_DIAGNOSTIC) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                    FilterChip(
                        selected = glassesMode == GlassesMode.HEYCYAN_SDK,
                        onClick = { viewModel.setGlassesMode(GlassesMode.HEYCYAN_SDK) },
                        label = { Text("HeyCyan SDK") },
                        leadingIcon = if (glassesMode == GlassesMode.HEYCYAN_SDK) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                }
                when (glassesMode) {
                    GlassesMode.NATIVE_BLE_DIAGNOSTIC ->
                        Text(
                            "Режим диагностики BLE. Подключается и логирует сервисы/характеристики устройства.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    GlassesMode.HEYCYAN_SDK ->
                        Text(
                            "Диагностика HeyCyan SDK. Capture-команды отключены до проверки на реальных очках.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    else -> {}
                }
            }

            if (glassesMode == GlassesMode.HEYCYAN_SDK) {
                SdkDiagnosticsSection(
                    state = sdkDiagnostics,
                    onInit = viewModel::runSdkInit,
                    onScan = viewModel::runSdkScan,
                    onStopScan = viewModel::runSdkStopScan,
                    onDisconnect = viewModel::runSdkDisconnect,
                    onBattery = viewModel::runSdkBattery,
                    onDeviceInfo = viewModel::runSdkDeviceInfo,
                    onMediaCounts = viewModel::runSdkMediaCounts,
                    onThumbnails = viewModel::runSdkThumbnails
                )
            }

            // ---- STT ----
            SettingsSection(title = "Голос и язык") {
                Text("Язык STT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ru" to "Русский", "en" to "English", "auto" to "Авто").forEach { (code, label) ->
                        FilterChip(
                            selected = sttLanguage == code,
                            onClick = { viewModel.setSttLanguage(code) },
                            label = { Text(label) },
                            leadingIcon = if (sttLanguage == code) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = voiceDraft,
                    onValueChange = { voiceDraft = it },
                    label = { Text("TTS Voice") },
                    placeholder = { Text("ru-RU-SvetlanaNeural") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.setTtsVoice(voiceDraft)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Debug ----
            SettingsSection(title = "Разработка") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Debug режим",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isDebugMode,
                        onCheckedChange = viewModel::setDebugMode
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedButton(
                    onClick = viewModel::clearLocalHistory,
                    enabled = !uiState.isClearingHistory,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isClearingHistory) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .padding(2.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text("  Очистить локальную историю")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SdkDiagnosticsSection(
    state: SdkDiagnosticsState,
    onInit: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit,
    onBattery: () -> Unit,
    onDeviceInfo: () -> Unit,
    onMediaCounts: () -> Unit,
    onThumbnails: () -> Unit
) {
    SettingsSection(title = "HeyCyan SDK Диагностика") {
        // Status rows
        SdkStatusRow("AAR present", if (state.aarPresent) "yes" else "no", state.aarPresent)
        SdkStatusRow("SDK initialized", if (state.sdkInitialized) "yes" else "no", state.sdkInitialized)
        SdkStatusRow("Connected", if (state.connected) "yes" else "no", state.connected)
        SdkStatusRow("Ready", if (state.ready) "yes" else "no", state.ready)

        state.battery?.let { SdkStatusRow("Battery", "$it%${if (state.isCharging == true) " (charging)" else ""}") }
        state.firmwareVersion?.let { SdkStatusRow("FW version", it) }
        state.hardwareVersion?.let { SdkStatusRow("HW version", it) }
        state.wifiFirmwareVersion?.let { SdkStatusRow("WiFi FW", it) }
        state.wifiHardwareVersion?.let { SdkStatusRow("WiFi HW", it) }
        state.imageCount?.let { SdkStatusRow("Images", "$it") }
        state.videoCount?.let { SdkStatusRow("Videos", "$it") }
        state.recordCount?.let { SdkStatusRow("Records", "$it") }
        state.p2pIp?.let { SdkStatusRow("P2P IP", it) }
        state.thumbnailsReceived?.let { SdkStatusRow("Thumbnails rx", "$it chunks") }

        if (state.eventLog.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Event log:", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace)
            state.eventLog.takeLast(6).forEach { event ->
                Text(event, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        state.lastError?.let {
            Text("Error: $it", style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                fontFamily = FontFamily.Monospace)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Action buttons
        Text("Действия", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInit, modifier = Modifier.weight(1f)) { Text("Init SDK") }
            Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan") }
            Button(onClick = onStopScan, modifier = Modifier.weight(1f)) { Text("Stop") }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBattery, enabled = state.connected,
                modifier = Modifier.weight(1f)) { Text("Battery") }
            Button(onClick = onDeviceInfo, enabled = state.connected,
                modifier = Modifier.weight(1f)) { Text("Device Info") }
            Button(onClick = onDisconnect, enabled = state.connected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Disconnect") }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onMediaCounts, enabled = state.connected,
                modifier = Modifier.weight(1f)) { Text("[Exp] Counts") }
            Button(onClick = onThumbnails, enabled = state.connected,
                modifier = Modifier.weight(1f)) { Text("[Test] Thumbs") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "[Exp]/[Test] = диагностика, ожидающая проверки на реальном CY 01_24E5. Photo/video/audio не включены.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SdkStatusRow(label: String, value: String, ok: Boolean? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = when (ok) {
                true -> StatusOnline
                false -> StatusError
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
