package com.axilbox.app.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import com.axilbox.app.model.OsType
import com.axilbox.app.model.VirtualInstance
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class EngineProvisionerTest {

    private val context: Context = mockk(relaxed = true)
    private val appInfo = ApplicationInfo()
    private val fakeNativeLibDir = File("/data/app/com.axilbox.app-test/lib/arm64")
    private val fakeFilesDir = File("/data/user/0/com.axilbox.app/files")

    private lateinit var provisioner: EngineProvisioner

    @Before
    fun setup() {
        appInfo.nativeLibraryDir = fakeNativeLibDir.absolutePath
        every { context.applicationInfo } returns appInfo
        every { context.filesDir } returns fakeFilesDir

        provisioner = EngineProvisioner(context)
    }

    @Test
    fun qemuBinary_resolvesToNativeLibraryDirWithLibSoNaming() {
        val qemuBin = provisioner.qemuBinary
        assertEquals(
            File(fakeNativeLibDir, "libqemu_system_aarch64.so").absolutePath,
            qemuBin.absolutePath
        )
    }

    @Test
    fun kernelDir_resolvesToFilesDirKernel() {
        val kernelDir = provisioner.kernelDir
        assertEquals(
            File(fakeFilesDir, "kernel").absolutePath,
            kernelDir.absolutePath
        )
    }

    @Test
    fun buildQemuArgs_usesNativeLibraryQemuBinaryAndVirtMachine() {
        val instance = VirtualInstance(
            id = 1L,
            name = "TestInstance",
            osType = OsType.AOSP_ARM64,
            ramMb = 2048,
            vCpuCount = 2
        )

        val args = provisioner.buildQemuArgs(instance)
        assertEquals(provisioner.qemuBinary.absolutePath, args[0])
        assertTrue(args.contains("-M"))
        assertTrue(args.contains("virt,gic-version=3"))
        assertTrue(args.contains("-m"))
        assertTrue(args.contains("2048M"))
        assertTrue(args.contains("-smp"))
        assertTrue(args.contains("2"))
        assertTrue(args.contains("-display"))
        assertTrue(args.contains("none"))
        assertTrue(args.contains("-monitor"))
        assertTrue(args.contains("none"))
        assertTrue(args.contains("-serial"))
        assertTrue(args.contains("stdio"))
    }
}
