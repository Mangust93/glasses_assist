package com.cyanbridge.app.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.MediaSource
import com.cyanbridge.app.domain.model.MediaType
import com.cyanbridge.app.domain.model.SyncStatus
import com.cyanbridge.app.ui.components.ErrorCard
import com.cyanbridge.app.ui.components.LoadingIndicator
import com.cyanbridge.app.ui.theme.DarkCard
import com.cyanbridge.app.ui.theme.StatusError
import com.cyanbridge.app.ui.theme.StatusOffline
import com.cyanbridge.app.ui.theme.StatusOnline
import com.cyanbridge.app.ui.theme.StatusWarning
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(viewModel: MediaViewModel = hiltViewModel()) {
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Медиа", style = MaterialTheme.typography.titleLarge) },
            actions = {
                IconButton(onClick = viewModel::syncFromHermes) {
                    Icon(Icons.Default.Sync, contentDescription = "Синхронизировать")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (uiState.isLoading || uiState.uploadProgress != null) {
            LoadingIndicator(
                message = uiState.uploadProgress ?: "Синхронизация...",
                modifier = Modifier.padding(16.dp)
            )
        }

        uiState.error?.let {
            ErrorCard(message = it, modifier = Modifier.padding(16.dp))
        }

        if (items.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Нет медиафайлов.\nСделайте снимок или запись через приложение.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    MediaCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem) {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
        .withZone(ZoneId.systemDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (item.type) {
                        MediaType.PHOTO -> Icons.Default.Image
                        MediaType.AUDIO -> Icons.Default.AudioFile
                        MediaType.VIDEO -> Icons.Default.VideoFile
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                SyncStatusDot(syncStatus = item.syncStatus)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (item.type) {
                    MediaType.PHOTO -> "Фото"
                    MediaType.AUDIO -> "Аудио"
                    MediaType.VIDEO -> "Видео"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatter.format(item.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (item.source) {
                    MediaSource.PHONE -> "Телефон"
                    MediaSource.GLASSES -> "Очки"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SyncStatusDot(syncStatus: SyncStatus) {
    val (color, label) = when (syncStatus) {
        SyncStatus.LOCAL_ONLY -> Pair(StatusOffline, "Локально")
        SyncStatus.UPLOADING -> Pair(StatusWarning, "Загрузка")
        SyncStatus.SYNCED -> Pair(StatusOnline, "Синхронизировано")
        SyncStatus.SYNC_ERROR -> Pair(StatusError, "Ошибка")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}
