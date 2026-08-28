package com.axilbox.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderWhite
import com.axilbox.app.ui.theme.ButtonTextBlack
import com.axilbox.app.ui.theme.ButtonWhite
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        modifier = Modifier.border(1.5.dp, BorderWhite, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "About AxilBox",
                style = AxilBoxTypography.titleLarge.copy(color = TextPrimary)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "AxilBox v0.1.0-alpha (Phase 1)",
                    style = AxilBoxTypography.titleSmall.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AxilBox is an open-source native Android application designed to manage, configure, and operate virtual mobile-OS instances directly on or streamed to Android devices — similar in spirit to Oracle VirtualBox, but tailored for mobile computing.",
                    style = AxilBoxTypography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Strict Non-Goals & Boundaries:",
                    style = AxilBoxTypography.labelLarge.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Zero Apple/iOS support (permanent legal non-goal)\n• Zero bundling of proprietary ROMs or Google Mobile Services (GMS)\n• Zero dependency on AVF / pKVM\n• Zero WebViews in render path (100% native Compose & WebRTC)",
                    style = AxilBoxTypography.bodySmall.copy(color = TextMuted)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Released under the Apache 2.0 Open Source License.",
                    style = AxilBoxTypography.labelSmall.copy(color = TextMuted)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonWhite,
                    contentColor = ButtonTextBlack
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Close", style = AxilBoxTypography.labelLarge.copy(color = ButtonTextBlack))
            }
        }
    )
}
