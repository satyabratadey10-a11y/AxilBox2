package com.axilbox.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BorderSubtle
import com.axilbox.app.ui.theme.StatusErrorColor
import com.axilbox.app.ui.theme.SurfacePrimary
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary

@Composable
fun ConfirmDeleteDialog(
    instance: VirtualInstance,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfacePrimary,
        title = {
            Text(
                text = "Delete Virtual Instance?",
                style = AxilBoxTypography.titleMedium.copy(color = TextPrimary)
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${instance.name}\"? All configured virtual disk bindings and hardware allocations will be removed.",
                style = AxilBoxTypography.bodyMedium.copy(color = TextSecondary)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusErrorColor,
                    contentColor = Color.White
                )
            ) {
                Text("Delete", style = AxilBoxTypography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", style = AxilBoxTypography.labelLarge.copy(color = TextSecondary))
            }
        }
    )
}
