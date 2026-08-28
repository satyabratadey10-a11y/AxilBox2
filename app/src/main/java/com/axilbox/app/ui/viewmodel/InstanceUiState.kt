package com.axilbox.app.ui.viewmodel

import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.OsType
import com.axilbox.app.model.SystemResourceInfo
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.util.BootLogSimulator

data class MainMenuUiState(
    val instances: List<VirtualInstance> = emptyList(),
    val isLoading: Boolean = true,
    val systemResourceInfo: SystemResourceInfo? = null,
    val selectedInstanceForDelete: VirtualInstance? = null,
    val showAboutDialog: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
) {
    val totalAllocatedGuestRamMb: Int
        get() = instances.sumOf { it.ramMb }

    val filteredInstances: List<VirtualInstance>
        get() = if (searchQuery.isBlank()) instances else instances.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.osType.displayName.contains(searchQuery, ignoreCase = true)
        }
}

data class InstanceFormState(
    val instanceId: Long = 0,
    val name: String = "",
    val osType: OsType = OsType.AOSP_ARM64,
    val vCpuCount: Int = 2,
    val ramMb: Int = 2048,
    val storageGb: Int = 16,
    val imageUri: String? = null,
    val kernelUri: String? = null,
    val initrdUri: String? = null,
    val extraCmdline: String = OsType.AOSP_ARM64.defaultCmdline,
    val displayOrientation: String = "PORTRAIT",
    val serialConsoleLogging: Boolean = true,
    val nameError: String? = null,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isFormValid: Boolean
        get() = name.isNotBlank() && nameError == null && !isSaving
}

data class BootScreenUiState(
    val instance: VirtualInstance? = null,
    val bootStatus: InstanceStatus = InstanceStatus.STOPPED,
    val bootLogs: List<BootLogSimulator.LogEntry> = emptyList(),
    val uptimeSeconds: Long = 0,
    val isLogPaused: Boolean = false,
    val isLandscape: Boolean = false,
    val isTerminalExpanded: Boolean = true,
    val isPoweringOff: Boolean = false
)
