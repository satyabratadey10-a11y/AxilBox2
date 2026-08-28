package com.axilbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BorderSubtle
import com.axilbox.app.ui.theme.PrimaryCyan
import com.axilbox.app.ui.theme.StatusErrorColor
import com.axilbox.app.ui.theme.SurfaceElevated
import com.axilbox.app.ui.theme.SurfacePrimary
import com.axilbox.app.ui.theme.SurfaceSecondary
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstanceCard(
    instance: VirtualInstance,
    onBootClick: (VirtualInstance) -> Unit,
    onEditClick: (VirtualInstance) -> Unit,
    onDeleteClick: (VirtualInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = SurfacePrimary, shape = RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.name,
                    style = AxilBoxTypography.titleMedium.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = instance.osType.displayName,
                    style = AxilBoxTypography.bodySmall.copy(color = PrimaryCyan)
                )
            }
            StatusBadge(status = instance.status)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Spec Grid
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SpecPill(
                icon = Icons.Outlined.Speed,
                label = "${instance.vCpuCount} vCPUs",
                modifier = Modifier.weight(1f)
            )
            SpecPill(
                icon = Icons.Outlined.Memory,
                label = "${instance.ramMb} MB",
                modifier = Modifier.weight(1f)
            )
            SpecPill(
                icon = Icons.Outlined.Storage,
                label = "${instance.storageGb} GB",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Last booted timestamp
        val lastBootText = instance.lastBootedAt?.let {
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            "Last run: ${sdf.format(Date(it))}"
        } ?: "Never booted"

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = lastBootText,
                style = AxilBoxTypography.labelSmall.copy(color = TextMuted)
            )
            Text(
                text = "${instance.displayOrientation.lowercase().replaceFirstChar { it.uppercase() }} mode",
                style = AxilBoxTypography.labelSmall.copy(color = TextMuted)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val isRunning = instance.status == InstanceStatus.RUNNING || instance.status == InstanceStatus.BOOTING

            Button(
                onClick = { onBootClick(instance) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) SurfaceElevated else PrimaryCyan,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "Open Console" else "Boot Instance",
                    style = AxilBoxTypography.labelLarge.copy(fontSize = 13.sp)
                )
            }

            IconButton(
                onClick = { onEditClick(instance) },
                modifier = Modifier
                    .background(color = SurfaceSecondary, shape = RoundedCornerShape(10.dp))
                    .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Instance",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { onDeleteClick(instance) },
                modifier = Modifier
                    .background(color = SurfaceSecondary, shape = RoundedCornerShape(10.dp))
                    .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Instance",
                    tint = StatusErrorColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SpecPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(color = SurfaceSecondary, shape = RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = BorderSubtle.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryCyan,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = AxilBoxTypography.labelSmall.copy(
                color = TextPrimary,
                fontSize = 11.sp
            )
        )
    }
}
