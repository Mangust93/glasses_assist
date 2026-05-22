package com.cyanbridge.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Assistant : Screen("assistant", "Ассистент", Icons.Default.Chat)
    object Notes : Screen("notes", "Заметки", Icons.Default.NoteAlt)
    object Media : Screen("media", "Медиа", Icons.Default.PermMedia)
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Assistant, Notes, Media, Settings)
    }
}
