package com.axilbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.axilbox.app.model.SystemResourceInfo
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderWhite
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary

@Composable
fun ResourceSummaryCard(
    resourceInfo: SystemResourceInfo,
    activeGuestRamAllocatedMb: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = BackgroundDark, shape = RoundedCornerShape(16.dp))
            .border(width = 1.5.dp, color = BorderWhite, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = "Host Hardware",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Host Hardware Resources",
                    style = AxilBoxTypography.titleSmall.copy(color = TextPrimary)
                )
            }
            Text(
                text = "${resourceInfo.availableRamMb} MB Free",
                style = AxilBoxTypography.labelMedium.copy(color = TextPrimary)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // RAM Bar
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Physical RAM (${resourceInfo.usedRamMb} / ${resourceInfo.totalRamMb} MB)",
                style = AxilBoxTypography.bodySmall.copy(color = TextSecondary)
            )
            Text(
                text = "${(resourceInfo.ramUsagePercent * 100).toInt()}% Used",
                style = AxilBoxTypography.labelSmall.copy(color = TextSecondary)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { resourceInfo.ramUsagePercent.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp)),
            color = Color.White,
            trackColor = Color(0xFF222222)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Storage & Guest Budget Row
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Storage: ${resourceInfo.freeInternalStorageGb} GB Free",
                    style = AxilBoxTypography.bodySmall.copy(color = TextSecondary)
                )
            }

            Text(
                text = "Safe Guest Cap: ${resourceInfo.maxSafeGuestRamMb} MB",
                style = AxilBoxTypography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}
