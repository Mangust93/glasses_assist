package com.cyanbridge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyanbridge.app.ui.theme.StatusError
import com.cyanbridge.app.ui.theme.StatusOffline
import com.cyanbridge.app.ui.theme.StatusOnline
import com.cyanbridge.app.ui.theme.StatusWarning

enum class ChipStatus { ONLINE, OFFLINE, ERROR, WARNING, NEUTRAL }

@Composable
fun StatusChip(
    label: String,
    status: ChipStatus = ChipStatus.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        ChipStatus.ONLINE -> StatusOnline
        ChipStatus.OFFLINE -> StatusOffline
        ChipStatus.ERROR -> StatusError
        ChipStatus.WARNING -> StatusWarning
        ChipStatus.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
