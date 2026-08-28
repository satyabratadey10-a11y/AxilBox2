package com.axilbox.app.ui.viewmodel

import com.axilbox.app.data.InstanceRepository
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.OsType
import com.axilbox.app.model.SystemResourceInfo
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.util.SystemResourceProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: InstanceRepository = mockk(relaxed = true)
    private val systemResourceProvider: SystemResourceProvider = mockk(relaxed = true)

    private lateinit var viewModel: InstanceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { systemResourceProvider.getSystemResourceInfo() } returns SystemResourceInfo(
            totalRamMb = 6000L,
            availableRamMb = 3200L,
            totalInternalStorageGb = 128L,
            freeInternalStorageGb = 64L,
            maxSafeGuestRamMb = 3000L
        )
        coEvery { repository.getAllInstances() } returns flowOf(
            listOf(
                VirtualInstance(id = 1L, name = "AOSP-14", osType = OsType.AOSP_ARM64),
                VirtualInstance(id = 2L, name = "Debian-Dev", osType = OsType.DEBIAN_ARM64)
            )
        )

        viewModel = InstanceViewModel(repository, systemResourceProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initCreateForm_populatesDefaults() {
        viewModel.initCreateForm()
        val state = viewModel.formState.value

        assertEquals(OsType.AOSP_ARM64, state.osType)
        assertEquals(2048, state.ramMb)
        assertEquals(2, state.vCpuCount)
        assertEquals(16, state.storageGb)
        assertFalse(state.isEditMode)
    }

    @Test
    fun onFormNameChanged_validatesBlankAndValidNames() {
        viewModel.onFormNameChanged("")
        assertNotNull(viewModel.formState.value.nameError)
        assertFalse(viewModel.formState.value.isFormValid)

        viewModel.onFormNameChanged("AOSP-Instance")
        assertNull(viewModel.formState.value.nameError)
        assertTrue(viewModel.formState.value.isFormValid)
    }

    @Test
    fun onFormOsTypeChanged_updatesHardwareDefaults() {
        viewModel.onFormOsTypeChanged(OsType.LINUX_GENERIC)
        val state = viewModel.formState.value

        assertEquals(OsType.LINUX_GENERIC, state.osType)
        assertEquals(512, state.ramMb)
        assertEquals(1, state.vCpuCount)
        assertEquals(4, state.storageGb)
    }

    @Test
    fun searchFiltering_filtersInstancesByName() = runTest {
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("Debian")
        advanceUntilIdle()

        val uiState = viewModel.mainMenuUiState.value
        assertEquals(1, uiState.filteredInstances.size)
        assertEquals("Debian-Dev", uiState.filteredInstances.first().name)
    }

    @Test
    fun promptDelete_and_confirmDelete_executesRepositoryCall() = runTest {
        val target = VirtualInstance(id = 1L, name = "AOSP-14", osType = OsType.AOSP_ARM64)
        viewModel.promptDeleteInstance(target)
        assertEquals(target, viewModel.mainMenuUiState.value.selectedInstanceForDelete)

        viewModel.confirmDeleteInstance()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteInstance(target) }
        assertNull(viewModel.mainMenuUiState.value.selectedInstanceForDelete)
    }

    @Test
    fun saveInstance_whenNameIsDuplicate_setsError() = runTest {
        viewModel.initCreateForm()
        viewModel.onFormNameChanged("Existing-Name")

        coEvery { repository.getInstanceByName("Existing-Name") } returns VirtualInstance(
            id = 99L,
            name = "Existing-Name",
            osType = OsType.AOSP_ARM64
        )

        var saved = false
        viewModel.saveInstance { saved = true }
        advanceUntilIdle()

        assertFalse(saved)
        assertNotNull(viewModel.formState.value.nameError)
    }
}
