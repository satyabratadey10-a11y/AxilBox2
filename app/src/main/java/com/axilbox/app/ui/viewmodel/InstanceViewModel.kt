package com.axilbox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.axilbox.app.data.InstanceRepository
import com.axilbox.app.engine.EngineProvisioner
import com.axilbox.app.engine.QemuProcessRunner
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.OsType
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.util.BootLogSimulator
import com.axilbox.app.util.SystemResourceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstanceViewModel(
    private val repository: InstanceRepository,
    private val systemResourceProvider: SystemResourceProvider,
    private val engineProvisioner: EngineProvisioner? = null,
    private val qemuProcessRunner: QemuProcessRunner? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedInstanceForDelete = MutableStateFlow<VirtualInstance?>(null)
    private val _showAboutDialog = MutableStateFlow(false)

    val mainMenuUiState: StateFlow<MainMenuUiState> = combine(
        repository.getAllInstances(),
        _searchQuery,
        _selectedInstanceForDelete,
        _showAboutDialog
    ) { instances, query, deleteTarget, showAbout ->
        MainMenuUiState(
            instances = instances,
            isLoading = false,
            systemResourceInfo = systemResourceProvider.getSystemResourceInfo(),
            selectedInstanceForDelete = deleteTarget,
            showAboutDialog = showAbout,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainMenuUiState()
    )

    private val _formState = MutableStateFlow(InstanceFormState())
    val formState: StateFlow<InstanceFormState> = _formState.asStateFlow()

    private val _bootUiState = MutableStateFlow(BootScreenUiState())
    val bootUiState: StateFlow<BootScreenUiState> = _bootUiState.asStateFlow()

    private var bootJob: Job? = null
    private var uptimeJob: Job? = null

    // Search & Filtering
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // Modal Controls
    fun promptDeleteInstance(instance: VirtualInstance) {
        _selectedInstanceForDelete.value = instance
    }

    fun dismissDeleteDialog() {
        _selectedInstanceForDelete.value = null
    }

    fun confirmDeleteInstance() {
        val target = _selectedInstanceForDelete.value ?: return
        viewModelScope.launch {
            repository.deleteInstance(target)
            _selectedInstanceForDelete.value = null
        }
    }

    fun setAboutDialogVisible(visible: Boolean) {
        _showAboutDialog.value = visible
    }

    // Form Controls (Add/Edit)
    fun initCreateForm() {
        initAddInstance()
    }

    fun initAddInstance() {
        val defaultOs = OsType.AOSP_ARM64
        _formState.value = InstanceFormState(
            osType = defaultOs,
            vCpuCount = defaultOs.defaultVCpus,
            ramMb = defaultOs.defaultRamMb,
            storageGb = defaultOs.defaultStorageGb,
            extraCmdline = defaultOs.defaultCmdline,
            isEditMode = false
        )
    }

    fun loadInstanceForEdit(instanceId: Long) {
        initEditInstance(instanceId)
    }

    fun initEditInstance(instanceId: Long) {
        viewModelScope.launch {
            val instance = repository.getInstanceById(instanceId) ?: return@launch
            _formState.value = InstanceFormState(
                instanceId = instance.id,
                name = instance.name,
                osType = instance.osType,
                vCpuCount = instance.vCpuCount,
                ramMb = instance.ramMb,
                storageGb = instance.storageGb,
                imageUri = instance.imageUri,
                kernelUri = instance.kernelUri,
                initrdUri = instance.initrdUri,
                extraCmdline = instance.extraCmdline,
                displayOrientation = instance.displayOrientation,
                serialConsoleLogging = instance.serialConsoleLogging,
                isEditMode = true
            )
        }
    }

    fun onFormNameChanged(name: String) {
        val error = when {
            name.isBlank() -> "Instance name cannot be blank"
            name.length > 40 -> "Name is too long (max 40 chars)"
            else -> null
        }
        _formState.update { it.copy(name = name, nameError = error) }
    }

    fun onFormOsTypeChanged(osType: OsType) {
        _formState.update { current ->
            current.copy(
                osType = osType,
                vCpuCount = osType.defaultVCpus,
                ramMb = osType.defaultRamMb,
                storageGb = osType.defaultStorageGb,
                extraCmdline = osType.defaultCmdline
            )
        }
    }

    fun onFormVCpuChanged(vCpus: Int) {
        _formState.update { it.copy(vCpuCount = vCpus) }
    }

    fun onFormRamChanged(ramMb: Int) {
        _formState.update { it.copy(ramMb = ramMb) }
    }

    fun onFormStorageChanged(storageGb: Int) {
        _formState.update { it.copy(storageGb = storageGb) }
    }

    fun onFormImageUriChanged(uri: String?) {
        _formState.update { it.copy(imageUri = uri) }
    }

    fun onFormKernelUriChanged(uri: String?) {
        _formState.update { it.copy(kernelUri = uri) }
    }

    fun onFormInitrdUriChanged(uri: String?) {
        _formState.update { it.copy(initrdUri = uri) }
    }

    fun onFormExtraCmdlineChanged(cmdline: String) {
        _formState.update { it.copy(extraCmdline = cmdline) }
    }

    fun onFormOrientationChanged(orientation: String) {
        _formState.update { it.copy(displayOrientation = orientation) }
    }

    fun onFormSerialLoggingChanged(enabled: Boolean) {
        _formState.update { it.copy(serialConsoleLogging = enabled) }
    }

    fun saveInstance(onSuccess: (Long) -> Unit) {
        val state = _formState.value
        if (!state.isFormValid) return

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, errorMessage = null) }

            // Name uniqueness check if new or renamed
            val existing = repository.getInstanceByName(state.name.trim())
            if (existing != null && existing.id != state.instanceId) {
                _formState.update {
                    it.copy(
                        nameError = "An instance with this name already exists",
                        isSaving = false
                    )
                }
                return@launch
            }

            val instance = VirtualInstance(
                id = state.instanceId,
                name = state.name.trim(),
                osType = state.osType,
                vCpuCount = state.vCpuCount,
                ramMb = state.ramMb,
                storageGb = state.storageGb,
                imageUri = state.imageUri,
                kernelUri = state.kernelUri,
                initrdUri = state.initrdUri,
                extraCmdline = state.extraCmdline.trim(),
                displayOrientation = state.displayOrientation,
                serialConsoleLogging = state.serialConsoleLogging,
                status = InstanceStatus.STOPPED
            )

            val id = if (state.isEditMode) {
                repository.updateInstance(instance)
                instance.id
            } else {
                repository.insertInstance(instance)
            }

            _formState.update { it.copy(isSaving = false) }
            onSuccess(id)
        }
    }

    // Boot & Console Execution
    fun initBootScreen(instanceId: Long) {
        viewModelScope.launch {
            val instance = repository.getInstanceById(instanceId) ?: return@launch
            _bootUiState.value = BootScreenUiState(
                instance = instance,
                bootStatus = InstanceStatus.STOPPED,
                bootLogs = emptyList(),
                uptimeSeconds = 0,
                isLandscape = instance.displayOrientation == "LANDSCAPE"
            )
            startBootSequence(instance)
        }
    }

    fun startBootSequence(instance: VirtualInstance) {
        bootJob?.cancel()
        uptimeJob?.cancel()

        bootJob = viewModelScope.launch {
            _bootUiState.update {
                it.copy(
                    bootStatus = InstanceStatus.BOOTING,
                    bootLogs = listOf(
                        BootLogSimulator.LogEntry(
                            0.0,
                            "[AxilBox] Initializing execution pipeline for '${instance.name}'..."
                        )
                    ),
                    uptimeSeconds = 0,
                    isPoweringOff = false
                )
            }
            repository.updateStatus(instance.id, InstanceStatus.BOOTING)

            // Check if real native engine and kernel are provisioned
            val hasRealEngine = engineProvisioner?.provisionEngineIfNeeded() == true && qemuProcessRunner != null

            if (hasRealEngine) {
                _bootUiState.update { current ->
                    current.copy(
                        bootLogs = current.bootLogs + BootLogSimulator.LogEntry(
                            0.0,
                            "[AxilBox Engine] Native QEMU aarch64 binary verified. Launching guest process...",
                            BootLogSimulator.LogLevel.SUCCESS
                        )
                    )
                }

                val qemuArgs = engineProvisioner!!.buildQemuArgs(instance)
                _bootUiState.update { it.copy(bootStatus = InstanceStatus.RUNNING) }
                repository.markBooted(instance.id)
                startUptimeCounter()

                try {
                    qemuProcessRunner!!.runQemu(qemuArgs).collect { rawLine ->
                        val level = when {
                            rawLine.contains("panic", ignoreCase = true) || rawLine.contains("error", ignoreCase = true) ->
                                BootLogSimulator.LogLevel.ERROR
                            rawLine.contains("warn", ignoreCase = true) ->
                                BootLogSimulator.LogLevel.WARN
                            rawLine.contains("Freeing unused kernel memory", ignoreCase = true) || rawLine.contains("SurfaceFlinger", ignoreCase = true) ->
                                BootLogSimulator.LogLevel.SUCCESS
                            else ->
                                BootLogSimulator.LogLevel.INFO
                        }
                        if (!_bootUiState.value.isLogPaused) {
                            _bootUiState.update { current ->
                                current.copy(
                                    bootLogs = current.bootLogs + BootLogSimulator.LogEntry(
                                        timestampSec = 0.0,
                                        message = rawLine,
                                        level = level
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    _bootUiState.update { current ->
                        current.copy(
                            bootLogs = current.bootLogs + BootLogSimulator.LogEntry(
                                0.0,
                                "[AxilBox Engine] QEMU stream closed: ${e.message}",
                                BootLogSimulator.LogLevel.WARN
                            )
                        )
                    }
                }
            } else {
                // Fallback simulation when native engine binary is not packaged on host
                val fullSequence = BootLogSimulator.generateBootSequence(instance)
                for (entry in fullSequence) {
                    if (_bootUiState.value.bootStatus != InstanceStatus.BOOTING &&
                        _bootUiState.value.bootStatus != InstanceStatus.RUNNING
                    ) {
                        break
                    }
                    delay(60)
                    if (!_bootUiState.value.isLogPaused) {
                        _bootUiState.update { current ->
                            current.copy(bootLogs = current.bootLogs + entry)
                        }
                    }
                }

                _bootUiState.update { it.copy(bootStatus = InstanceStatus.RUNNING) }
                repository.markBooted(instance.id)
                startUptimeCounter()
            }
        }
    }

    private fun startUptimeCounter() {
        uptimeJob?.cancel()
        uptimeJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _bootUiState.update { it.copy(uptimeSeconds = it.uptimeSeconds + 1) }
            }
        }
    }

    fun stopInstance() {
        val instance = _bootUiState.value.instance ?: return
        bootJob?.cancel()
        uptimeJob?.cancel()

        viewModelScope.launch {
            qemuProcessRunner?.stop()

            _bootUiState.update {
                it.copy(
                    isPoweringOff = true,
                    bootLogs = it.bootLogs + BootLogSimulator.LogEntry(
                        0.0,
                        "[Kernel] ACPI poweroff signal received. Syncing filesystems and halting CPUs...",
                        BootLogSimulator.LogLevel.WARN
                    )
                )
            }
            delay(500)
            _bootUiState.update {
                it.copy(
                    bootStatus = InstanceStatus.STOPPED,
                    isPoweringOff = false,
                    bootLogs = it.bootLogs + BootLogSimulator.LogEntry(
                        0.0,
                        "[AxilBox] Virtual machine terminated safely (exit code 0).",
                        BootLogSimulator.LogLevel.INFO
                    )
                )
            }
            repository.updateStatus(instance.id, InstanceStatus.STOPPED)
        }
    }

    fun resetInstance() {
        val instance = _bootUiState.value.instance ?: return
        startBootSequence(instance)
    }

    fun toggleLogPause() {
        _bootUiState.update { it.copy(isLogPaused = !it.isLogPaused) }
    }

    fun toggleOrientation() {
        _bootUiState.update { it.copy(isLandscape = !it.isLandscape) }
    }

    fun toggleTerminalExpanded() {
        _bootUiState.update { it.copy(isTerminalExpanded = !it.isTerminalExpanded) }
    }

    fun clearLogs() {
        _bootUiState.update { it.copy(bootLogs = emptyList()) }
    }

    class Factory(
        private val repository: InstanceRepository,
        private val systemResourceProvider: SystemResourceProvider,
        private val engineProvisioner: EngineProvisioner? = null,
        private val qemuProcessRunner: QemuProcessRunner? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InstanceViewModel(
                repository,
                systemResourceProvider,
                engineProvisioner,
                qemuProcessRunner
            ) as T
        }
    }
}
