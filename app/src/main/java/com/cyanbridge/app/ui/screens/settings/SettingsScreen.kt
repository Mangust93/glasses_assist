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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyanbridge.app.domain.model.GlassesMode
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
                        label = { Text("Fake (симуляция)") },
                        leadingIcon = if (glassesMode == GlassesMode.FAKE) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                    FilterChip(
                        selected = glassesMode == GlassesMode.NATIVE_BLE,
                        onClick = { viewModel.setGlassesMode(GlassesMode.NATIVE_BLE) },
                        label = { Text("Native BLE") },
                        leadingIcon = if (glassesMode == GlassesMode.NATIVE_BLE) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                }
                if (glassesMode == GlassesMode.NATIVE_BLE) {
                    Text(
                        "BLE скелет подключён. Реальные команды будут доступны после получения протокола от производителя.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
