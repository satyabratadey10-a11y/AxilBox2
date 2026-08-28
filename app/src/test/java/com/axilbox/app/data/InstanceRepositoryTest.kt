package com.axilbox.app.data

import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.OsType
import com.axilbox.app.model.VirtualInstance
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class InstanceRepositoryTest {

    private val dao: InstanceDao = mockk(relaxed = true)
    private lateinit var repository: InstanceRepository

    @Before
    fun setup() {
        repository = InstanceRepositoryImpl(dao)
    }

    @Test
    fun getAllInstances_returnsFlowFromDao() = runTest {
        val sampleInstances = listOf(
            VirtualInstance(
                id = 1L,
                name = "AOSP-Test",
                osType = OsType.AOSP_ARM64,
                vCpuCount = 2,
                ramMb = 2048,
                storageGb = 16
            )
        )
        coEvery { dao.getAllInstancesFlow() } returns flowOf(sampleInstances)

        val result = repository.getAllInstances().first()
        assertEquals(1, result.size)
        assertEquals("AOSP-Test", result.first().name)
    }

    @Test
    fun insertInstance_delegatesToDao() = runTest {
        val instance = VirtualInstance(
            name = "Debian-Test",
            osType = OsType.DEBIAN_ARM64,
            vCpuCount = 2,
            ramMb = 1024,
            storageGb = 8
        )
        coEvery { dao.insertInstance(instance) } returns 42L

        val id = repository.insertInstance(instance)
        assertEquals(42L, id)
        coVerify(exactly = 1) { dao.insertInstance(instance) }
    }

    @Test
    fun updateStatus_delegatesToDao() = runTest {
        repository.updateStatus(10L, InstanceStatus.RUNNING)
        coVerify(exactly = 1) { dao.updateStatus(10L, InstanceStatus.RUNNING) }
    }

    @Test
    fun deleteInstance_delegatesToDao() = runTest {
        val instance = VirtualInstance(
            id = 5L,
            name = "To-Delete",
            osType = OsType.CUSTOM_RAW,
            vCpuCount = 1,
            ramMb = 512,
            storageGb = 4
        )
        repository.deleteInstance(instance)
        coVerify(exactly = 1) { dao.deleteInstance(instance) }
    }
}
