package com.axilbox.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.StatusBootingColor
import com.axilbox.app.ui.theme.StatusErrorColor
import com.axilbox.app.ui.theme.StatusRunningColor
import com.axilbox.app.ui.theme.StatusStoppedColor

@Composable
fun StatusBadge(
    status: InstanceStatus,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusBg) = when (status) {
        InstanceStatus.STOPPED -> Pair(StatusStoppedColor, StatusStoppedColor.copy(alpha = 0.15f))
        InstanceStatus.BOOTING -> Pair(StatusBootingColor, StatusBootingColor.copy(alpha = 0.20f))
        InstanceStatus.RUNNING -> Pair(StatusRunningColor, StatusRunningColor.copy(alpha = 0.20f))
        InstanceStatus.PAUSED -> Pair(StatusBootingColor, StatusBootingColor.copy(alpha = 0.15f))
        InstanceStatus.ERROR -> Pair(StatusErrorColor, StatusErrorColor.copy(alpha = 0.20f))
    }

    val alphaAnim = if (status == InstanceStatus.BOOTING) {
        val infiniteTransition = rememberInfiniteTransition(label = "boot_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        alpha
    } else {
        1.0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color = statusBg, shape = RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = statusColor.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(alphaAnim)
                .background(color = statusColor, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.label.uppercase(),
            style = AxilBoxTypography.labelSmall.copy(
                color = statusColor,
                fontSize = 10.sp
            )
        )
    }
}
