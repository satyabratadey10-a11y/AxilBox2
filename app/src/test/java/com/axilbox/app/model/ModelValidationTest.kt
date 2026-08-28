package com.axilbox.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelValidationTest {

    @Test
    fun osType_defaultValuesAreWithinSafeBudgets() {
        val aosp = OsType.AOSP_ARM64
        assertEquals(2048, aosp.defaultRamMb)
        assertEquals(2, aosp.defaultVCpus)
        assertEquals(16, aosp.defaultStorageGb)
        assertTrue(aosp.defaultCmdline.contains("console=ttyAMA0"))

        val debian = OsType.DEBIAN_ARM64
        assertEquals(1024, debian.defaultRamMb)
        assertEquals(2, debian.defaultVCpus)
        assertEquals(8, debian.defaultStorageGb)

        val linux = OsType.LINUX_GENERIC
        assertEquals(512, linux.defaultRamMb)
        assertEquals(1, linux.defaultVCpus)
        assertEquals(4, linux.defaultStorageGb)
    }

    @Test
    fun virtualInstance_creationAndDefaults() {
        val instance = VirtualInstance(
            name = "Test-Instance-01",
            osType = OsType.AOSP_ARM64,
            vCpuCount = 2,
            ramMb = 2048,
            storageGb = 16
        )

        assertEquals("Test-Instance-01", instance.name)
        assertEquals(InstanceStatus.STOPPED, instance.status)
        assertEquals("PORTRAIT", instance.displayOrientation)
        assertTrue(instance.serialConsoleLogging)
        assertNotNull(instance.createdAt)
    }

    @Test
    fun systemResourceInfo_computesUsedRamAndPercentageCorrectly() {
        val info = SystemResourceInfo(
            totalRamMb = 6000L,
            availableRamMb = 3000L,
            totalInternalStorageGb = 128L,
            freeInternalStorageGb = 64L,
            maxSafeGuestRamMb = 3000L
        )

        assertEquals(3000L, info.usedRamMb)
        assertEquals(0.5f, info.ramUsagePercent, 0.001f)
    }
}
