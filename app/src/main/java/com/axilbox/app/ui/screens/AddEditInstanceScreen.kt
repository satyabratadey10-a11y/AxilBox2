package com.axilbox.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axilbox.app.model.OsType
import com.axilbox.app.ui.components.HardwareSpecSlider
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderSubtle
import com.axilbox.app.ui.theme.PrimaryCyan
import com.axilbox.app.ui.theme.StatusErrorColor
import com.axilbox.app.ui.theme.SurfacePrimary
import com.axilbox.app.ui.theme.SurfaceSecondary
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary
import com.axilbox.app.ui.viewmodel.InstanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInstanceScreen(
    viewModel: InstanceViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (Long) -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    // SAF Document Launchers
    val diskPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFormImageUriChanged(uri?.toString())
    }

    val kernelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFormKernelUriChanged(uri?.toString())
    }

    val initrdPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFormInitrdUriChanged(uri?.toString())
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (formState.isEditMode) "Edit Virtual Instance" else "New Virtual Instance",
                        style = AxilBoxTypography.titleMedium.copy(color = TextPrimary)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            // Action Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .padding(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", style = AxilBoxTypography.labelLarge.copy(color = TextSecondary))
                }

                Button(
                    onClick = { viewModel.saveInstance(onSaveSuccess) },
                    enabled = formState.isFormValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (formState.isSaving) {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (formState.isEditMode) "Save Changes" else "Create Instance",
                            style = AxilBoxTypography.labelLarge
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section 1: Instance Identity
            Text(
                text = "Instance Identity",
                style = AxilBoxTypography.titleSmall.copy(color = PrimaryCyan)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = formState.name,
                onValueChange = { viewModel.onFormNameChanged(it) },
                label = { Text("Instance Name", style = AxilBoxTypography.bodyMedium) },
                placeholder = { Text("e.g. AOSP-14-ARM64", style = AxilBoxTypography.bodyMedium.copy(color = TextMuted)) },
                isError = formState.nameError != null,
                supportingText = formState.nameError?.let {
                    { Text(it, style = AxilBoxTypography.bodySmall.copy(color = StatusErrorColor)) }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfacePrimary,
                    unfocusedContainerColor = SurfacePrimary,
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OS Profile Selection
            Text(
                text = "OS Profile Preset",
                style = AxilBoxTypography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OsType.values().forEach { os ->
                    val isSelected = formState.osType == os
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFormOsTypeChanged(os) },
                        label = {
                            Text(
                                text = when (os) {
                                    OsType.AOSP_ARM64 -> "AOSP"
                                    OsType.DEBIAN_ARM64 -> "Debian"
                                    OsType.LINUX_GENERIC -> "Linux"
                                    OsType.CUSTOM_RAW -> "Custom"
                                },
                                style = AxilBoxTypography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryCyan,
                            selectedLabelColor = TextPrimary,
                            containerColor = SurfaceSecondary,
                            labelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Virtual Hardware Allocation
            Text(
                text = "Hardware Allocation",
                style = AxilBoxTypography.titleSmall.copy(color = PrimaryCyan)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // vCPUs
            HardwareSpecSlider(
                title = "Virtual CPU Cores",
                value = formState.vCpuCount.toFloat(),
                onValueChange = { viewModel.onFormVCpuChanged(it.toInt()) },
                valueRange = 1f..4f,
                steps = 2,
                displayValue = "${formState.vCpuCount} Cores",
                warningText = if (formState.vCpuCount > 2) "Allocating >2 vCPUs may cause thermal throttling on phone hosts" else null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // RAM Allocation
            HardwareSpecSlider(
                title = "Virtual RAM",
                value = formState.ramMb.toFloat(),
                onValueChange = { viewModel.onFormRamChanged(it.toInt()) },
                valueRange = 512f..4096f,
                steps = 13, // 256MB increments
                displayValue = "${formState.ramMb} MB",
                warningText = if (formState.ramMb > 2048) "Allocating >2048 MB approaches safe host memory limits" else null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Storage Allocation
            HardwareSpecSlider(
                title = "Virtual Disk Capacity",
                value = formState.storageGb.toFloat(),
                onValueChange = { viewModel.onFormStorageChanged(it.toInt()) },
                valueRange = 4f..64f,
                steps = 14,
                displayValue = "${formState.storageGb} GB"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Storage & Kernel Images (SAF)
            Text(
                text = "Guest Images & Kernels (SAF Scoped)",
                style = AxilBoxTypography.titleSmall.copy(color = PrimaryCyan)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilePickerField(
                label = "Disk Image (.img / .qcow2 / .raw)",
                selectedUri = formState.imageUri,
                onBrowseClick = { diskPickerLauncher.launch(arrayOf("*/*")) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            FilePickerField(
                label = "Kernel Binary (Image / vmlinuz)",
                selectedUri = formState.kernelUri,
                onBrowseClick = { kernelPickerLauncher.launch(arrayOf("*/*")) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            FilePickerField(
                label = "Initial Ramdisk (initrd.img)",
                selectedUri = formState.initrdUri,
                onBrowseClick = { initrdPickerLauncher.launch(arrayOf("*/*")) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Advanced Settings (Collapsible)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Advanced Hardware & Boot Arguments",
                    style = AxilBoxTypography.titleSmall.copy(color = TextSecondary)
                )
                Icon(
                    imageVector = if (isAdvancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = isAdvancedExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfacePrimary, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = formState.extraCmdline,
                        onValueChange = { viewModel.onFormExtraCmdlineChanged(it) },
                        label = { Text("Kernel Command Line", style = AxilBoxTypography.bodySmall) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceSecondary,
                            unfocusedContainerColor = SurfaceSecondary,
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Display Orientation",
                            style = AxilBoxTypography.bodyMedium.copy(color = TextPrimary)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = formState.displayOrientation == "PORTRAIT",
                                onClick = { viewModel.onFormOrientationChanged("PORTRAIT") },
                                label = { Text("Portrait (9:16)", style = AxilBoxTypography.labelSmall) }
                            )
                            FilterChip(
                                selected = formState.displayOrientation == "LANDSCAPE",
                                onClick = { viewModel.onFormOrientationChanged("LANDSCAPE") },
                                label = { Text("Landscape (16:9)", style = AxilBoxTypography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "UART PL011 Serial Logging",
                                style = AxilBoxTypography.bodyMedium.copy(color = TextPrimary)
                            )
                            Text(
                                text = "Stream early boot console logs to app telemetry",
                                style = AxilBoxTypography.bodySmall.copy(color = TextMuted)
                            )
                        }
                        Switch(
                            checked = formState.serialConsoleLogging,
                            onCheckedChange = { viewModel.onFormSerialLoggingChanged(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryCyan,
                                checkedTrackColor = SurfaceSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FilePickerField(
    label: String,
    selectedUri: String?,
    onBrowseClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AxilBoxTypography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePrimary, RoundedCornerShape(10.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = selectedUri ?: "No file selected (Optional)",
                style = AxilBoxTypography.labelSmall.copy(
                    color = if (selectedUri != null) TextPrimary else TextMuted
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onBrowseClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "Browse",
                    tint = PrimaryCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Browse", style = AxilBoxTypography.labelSmall.copy(color = PrimaryCyan))
            }
        }
    }
}
