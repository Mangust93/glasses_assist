package com.cyanbridge.app.ui.screens.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyanbridge.app.domain.model.GlassesMode
import com.cyanbridge.app.domain.model.GlassesStatus
import com.cyanbridge.app.domain.model.displayText
import com.cyanbridge.app.ui.components.ChipStatus
import com.cyanbridge.app.ui.components.ErrorCard
import com.cyanbridge.app.ui.components.StatusChip
import com.cyanbridge.app.ui.theme.DarkCard
import com.cyanbridge.app.ui.theme.StatusRecording

@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AssistantTopBar(
            isHermesOnline = uiState.isHermesOnline,
            onRefreshHealth = viewModel::checkHermesHealth
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusChip(
                    label = if (uiState.isHermesOnline == true) "Hermes: онлайн"
                    else if (uiState.isHermesOnline == false) "Hermes: офлайн"
                    else "Hermes: ...",
                    status = when (uiState.isHermesOnline) {
                        true -> ChipStatus.ONLINE
                        false -> ChipStatus.OFFLINE
                        null -> ChipStatus.NEUTRAL
                    }
                )
                StatusChip(
                    label = if (uiState.glassesMode == GlassesMode.FAKE) "Fake" else "BLE",
                    status = ChipStatus.NEUTRAL
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Glasses status
            StatusChip(
                label = uiState.glassesStatus.displayText(),
                status = when (uiState.glassesStatus) {
                    is GlassesStatus.FakeConnected, is GlassesStatus.Connected -> ChipStatus.ONLINE
                    is GlassesStatus.Scanning, is GlassesStatus.Connecting -> ChipStatus.WARNING
                    is GlassesStatus.Error -> ChipStatus.ERROR
                    else -> ChipStatus.OFFLINE
                },
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stage label
            StageLabel(stage = uiState.stage)

            Spacer(modifier = Modifier.height(16.dp))

            // Error card
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { error ->
                    ErrorCard(
                        message = error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // Transcript card
            AnimatedVisibility(visible = uiState.transcript != null) {
                uiState.transcript?.let { text ->
                    ConversationCard(
                        label = "Вы сказали",
                        text = text,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Answer card
            AnimatedVisibility(visible = uiState.answer != null) {
                uiState.answer?.let { text ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Ассистент",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (uiState.lastAudioUrl != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(onClick = viewModel::playLastAudio) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Воспроизвести ответ",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // PTT Button
            PushToTalkButton(
                stage = uiState.stage,
                onPress = viewModel::onPttPressed,
                onRelease = viewModel::onPttReleased
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (uiState.stage == AssistantStage.RECORDING) "Отпустите для отправки"
                else "Удержите для записи",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantTopBar(
    isHermesOnline: Boolean?,
    onRefreshHealth: () -> Unit
) {
    TopAppBar(
        title = {
            Text("CyanBridge", style = MaterialTheme.typography.titleLarge)
        },
        actions = {
            IconButton(onClick = onRefreshHealth) {
                Icon(Icons.Default.Refresh, contentDescription = "Проверить Hermes")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun StageLabel(stage: AssistantStage) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "stageLabel"
    ) { targetStage ->
        val text = when (targetStage) {
            AssistantStage.IDLE -> "Готов к работе"
            AssistantStage.RECORDING -> "Слушаю..."
            AssistantStage.UPLOADING -> "Отправляю..."
            AssistantStage.PROCESSING -> "Думаю..."
            AssistantStage.PLAYING -> "Озвучиваю..."
            AssistantStage.ERROR -> "Ошибка"
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (targetStage == AssistantStage.ERROR)
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationCard(label: String, text: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PushToTalkButton(
    stage: AssistantStage,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val isRecording = stage == AssistantStage.RECORDING
    val isActive = stage == AssistantStage.IDLE || stage == AssistantStage.RECORDING || stage == AssistantStage.ERROR

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "ptt_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(120.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(
                if (isRecording)
                    Brush.radialGradient(listOf(StatusRecording, StatusRecording.copy(alpha = 0.6f)))
                else
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
            )
            .pointerInput(isActive) {
                if (isActive) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                }
            }
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Push to Talk",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp)
        )
    }
}
