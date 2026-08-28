package com.axilbox.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderWhite
import com.axilbox.app.ui.theme.ButtonTextBlack
import com.axilbox.app.ui.theme.ButtonWhite
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
        containerColor = BackgroundDark,
        modifier = Modifier.border(1.5.dp, BorderWhite, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
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
                    containerColor = ButtonWhite,
                    contentColor = ButtonTextBlack
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Delete", style = AxilBoxTypography.labelLarge.copy(color = ButtonTextBlack))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderWhite)
            ) {
                Text("Cancel", style = AxilBoxTypography.labelLarge.copy(color = TextPrimary))
            }
        }
    )
}
