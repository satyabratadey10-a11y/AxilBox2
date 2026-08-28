package com.axilbox.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.PrimaryCyan
import com.axilbox.app.ui.theme.StatusBootingColor
import com.axilbox.app.ui.theme.SurfaceSecondary
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary

@Composable
fun HardwareSpecSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    warningText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = AxilBoxTypography.bodyMedium.copy(color = TextPrimary)
            )
            Text(
                text = displayValue,
                style = AxilBoxTypography.labelMedium.copy(color = PrimaryCyan)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryCyan,
                activeTrackColor = PrimaryCyan,
                inactiveTrackColor = SurfaceSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (warningText != null) {
            Text(
                text = warningText,
                style = AxilBoxTypography.labelSmall.copy(color = StatusBootingColor)
            )
        }
    }
}
