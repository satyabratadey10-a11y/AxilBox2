package com.axilbox.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.ui.components.StatusBadge
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderWhite
import com.axilbox.app.ui.theme.MonospaceCodeStyle
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary
import com.axilbox.app.ui.viewmodel.InstanceViewModel
import com.axilbox.app.util.BootLogSimulator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootScreen(
    instanceId: Long,
    viewModel: InstanceViewModel,
    onNavigateBack: () -> Unit
) {
    val bootState by viewModel.bootUiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(instanceId) {
        viewModel.initBootScreen(instanceId)
    }

    // Auto-scroll logs
    LaunchedEffect(bootState.bootLogs.size) {
        if (bootState.bootLogs.isNotEmpty() && !bootState.isLogPaused) {
            listState.animateScrollToItem(bootState.bootLogs.size - 1)
        }
    }

    val instance = bootState.instance

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = instance?.name ?: "Virtual Console",
                            style = AxilBoxTypography.titleSmall.copy(color = TextPrimary)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Uptime: %02d:%02d".format(
                                    bootState.uptimeSeconds / 60,
                                    bootState.uptimeSeconds % 60
                                ),
                                style = AxilBoxTypography.labelSmall.copy(color = TextPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${instance?.vCpuCount ?: 2} vCPUs / ${instance?.ramMb ?: 2048}MB",
                                style = AxilBoxTypography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    StatusBadge(status = bootState.bootStatus)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.resetInstance() },
                        enabled = bootState.bootStatus != InstanceStatus.BOOTING
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            if (bootState.bootStatus == InstanceStatus.RUNNING || bootState.bootStatus == InstanceStatus.BOOTING) {
                                viewModel.stopInstance()
                            } else if (instance != null) {
                                viewModel.startBootSequence(instance)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Section 1: Virtual Display Viewport
            val aspectRatio = if (bootState.isLandscape) (16f / 9f) else (9f / 16f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (bootState.isTerminalExpanded) 1.1f else 2.0f),
                contentAlignment = Alignment.Center
            ) {
                VirtualDisplayContainer(
                    aspectRatio = aspectRatio,
                    bootStatus = bootState.bootStatus,
                    instance = instance,
                    isLandscape = bootState.isLandscape
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 2: Virtual Device Controls Toolbar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderWhite, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.toggleOrientation() }) {
                        Icon(
                            imageVector = Icons.Filled.ScreenRotation,
                            contentDescription = "Rotate",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "Virtual Keyboard stub invoked", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = "Keyboard",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "Virtual frame snapshot saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.toggleLogPause() }) {
                        Icon(
                            imageVector = if (bootState.isLogPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (bootState.isLogPaused) "Resume Logs" else "Pause Logs",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        val allLogs = bootState.bootLogs.joinToString("\n") { it.message }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AxilBox Logs", allLogs))
                        Toast.makeText(context, "Boot logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleTerminalExpanded() }) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = "Toggle Terminal",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 3: Live Telemetry & Monospace Boot Console
            AnimatedVisibility(
                visible = bootState.isTerminalExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark, RoundedCornerShape(16.dp))
                        .border(1.5.dp, BorderWhite, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "UART PL011 Serial Console (/dev/ttyAMA0)",
                            style = AxilBoxTypography.labelSmall.copy(color = TextPrimary)
                        )
                        Text(
                            text = "${bootState.bootLogs.size} lines",
                            style = AxilBoxTypography.labelSmall.copy(color = TextSecondary)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(bootState.bootLogs) { logEntry ->
                            Text(
                                text = logEntry.message,
                                style = MonospaceCodeStyle.copy(color = TextPrimary, fontSize = 11.sp),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualDisplayContainer(
    aspectRatio: Float,
    bootStatus: InstanceStatus,
    instance: com.axilbox.app.model.VirtualInstance?,
    isLandscape: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline_alpha"
    )

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundDark)
            .border(1.5.dp, BorderWhite, RoundedCornerShape(16.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (bootStatus) {
            InstanceStatus.STOPPED -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Guest VM Inactive",
                        style = AxilBoxTypography.titleSmall.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Tap Power button to start",
                        style = AxilBoxTypography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
            InstanceStatus.BOOTING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Booting ARM64 virt kernel...",
                        style = AxilBoxTypography.titleSmall.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Mounting virtio-blk and DRM compositor",
                        style = AxilBoxTypography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
            InstanceStatus.RUNNING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(scanAlpha)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "VIRTIO-GPU DISPLAY ACTIVE",
                        style = AxilBoxTypography.labelMedium.copy(color = TextPrimary, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isLandscape) "1280 x 720 @ 60Hz" else "720 x 1280 @ 60Hz",
                        style = AxilBoxTypography.labelSmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Phase 1 Interactive UI Stub",
                        style = AxilBoxTypography.bodySmall.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Native QEMU JNI pipeline connects in Phase 2\nCloud WebRTC engine connects in Phase 3",
                        style = AxilBoxTypography.labelSmall.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> {}
        }
    }
}
