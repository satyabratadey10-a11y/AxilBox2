package com.axilbox.app.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import com.axilbox.app.model.OsType
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.util.ResolvedBootResource
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
    fun pcBiosDir_resolvesToFilesDirEnginePcBios() {
        val pcBiosDir = provisioner.pcBiosDir
        assertEquals(
            File(fakeFilesDir, "engine/pc-bios").absolutePath,
            pcBiosDir.absolutePath
        )
    }

    @Test
    fun bundledKernelImage_resolvesToFilesDirKernelImage() {
        val kernel = provisioner.bundledKernelImage
        assertEquals(
            File(fakeFilesDir, "kernel/Image").absolutePath,
            kernel.absolutePath
        )
    }

    @Test
    fun buildKernelCmdline_deduplicatesConsoleAndEarlycon() {
        val instanceWithDuplicates = VirtualInstance(
            id = 2L,
            name = "TestCmdline",
            osType = OsType.LINUX_GENERIC,
            extraCmdline = "console=ttyAMA0 earlycon=pl011,0x09000000 panic=-1 custom_arg=1"
        )

        val cmdline = provisioner.buildKernelCmdline(instanceWithDuplicates)

        // Ensure console and earlycon appear exactly once
        assertEquals(1, "console=ttyAMA0".toRegex().findAll(cmdline).count())
        assertEquals(1, "earlycon=pl011,0x09000000".toRegex().findAll(cmdline).count())
        assertEquals(1, "panic=-1".toRegex().findAll(cmdline).count())
        assertTrue(cmdline.contains("custom_arg=1"))
        // No initrd configured, so rdinit is not present
        assertFalse(cmdline.contains("rdinit=/sbin/init"))
    }

    @Test
    fun buildKernelCmdline_includesRdinitWhenInitrdConfigured() {
        val instanceWithInitrd = VirtualInstance(
            id = 3L,
            name = "InitrdInstance",
            osType = OsType.LINUX_GENERIC,
            initrdUri = "content://com.android.providers.media.documents/document/123"
        )

        val cmdline = provisioner.buildKernelCmdline(instanceWithInitrd)
        assertTrue(cmdline.contains("rdinit=/sbin/init"))
    }

    @Test
    fun buildQemuArgs_neverIncludesInitrdWhenNoneConfigured() {
        val instance = VirtualInstance(
            id = 1L,
            name = "TestInstance",
            osType = OsType.AOSP_ARM64,
            ramMb = 2048,
            vCpuCount = 2,
            imageUri = "content://media/disk.img"
        )

        val args = provisioner.buildQemuArgs(instance)
        assertEquals(provisioner.qemuBinary.absolutePath, args[0])
        assertTrue(args.contains("-L"))
        val lIndex = args.indexOf("-L")
        assertEquals(provisioner.pcBiosDir.absolutePath, args[lIndex + 1])
        assertTrue(args.contains("-M"))
        assertTrue(args.contains("virt,gic-version=3"))
        assertTrue(args.contains("-m"))
        assertTrue(args.contains("2048M"))
        assertTrue(args.contains("-smp"))
        assertTrue(args.contains("2"))

        // Initrd must NOT be present when no initrd is configured
        assertFalse(args.contains("-initrd"))

        // Disk image must be present
        assertTrue(args.contains("-drive"))
        val dIndex = args.indexOf("-drive")
        assertEquals("file=content://media/disk.img,if=virtio,format=raw", args[dIndex + 1])
    }

    @Test
    fun buildQemuArgs_usesAddFdAndDevFdsetForSafBootResources() {
        val instance = VirtualInstance(
            id = 4L,
            name = "SafInstance",
            osType = OsType.DEBIAN_ARM64,
            ramMb = 1024,
            vCpuCount = 1,
            imageUri = "content://saf/disk.raw"
        )

        val fakeBootResources = InstanceBootResources(
            diskResource = ResolvedBootResource(path = "/proc/self/fd/42", isDirectFd = true),
            kernelResource = null,
            initrdResource = ResolvedBootResource(path = "/proc/self/fd/43", isDirectFd = true)
        )

        val args = provisioner.buildQemuArgs(instance, bootResources = fakeBootResources)

        // Verifies -add-fd and /dev/fdset are emitted instead of /proc/self/fd
        assertTrue(args.contains("-add-fd"))
        val addFdIndices = args.mapIndexedNotNull { index, elem -> if (elem == "-add-fd") index else null }
        assertEquals(2, addFdIndices.size)

        // Initrd is processed first (set=0)
        assertEquals("fd=43,set=0", args[addFdIndices[0] + 1])
        val iIndex = args.indexOf("-initrd")
        assertEquals("/dev/fdset/0", args[iIndex + 1])

        // Disk image is processed next (set=1)
        assertEquals("fd=42,set=1", args[addFdIndices[1] + 1])
        val dIndex = args.indexOf("-drive")
        assertEquals("file=/dev/fdset/1,if=virtio,format=raw", args[dIndex + 1])

        // Direct /proc/self/fd must NOT appear in QEMU file arguments
        assertFalse(args.any { it.contains("file=/proc/self/fd") })
    }

    @Test
    fun buildQemuArgs_withSingleSafDisk_emitsSet0() {
        val instance = VirtualInstance(
            id = 5L,
            name = "SingleDiskInstance",
            osType = OsType.LINUX_GENERIC,
            ramMb = 2048,
            vCpuCount = 2,
            imageUri = "content://saf/alpine.img"
        )
        val fakeResources = InstanceBootResources(
            diskResource = ResolvedBootResource(path = "/proc/self/fd/102", isDirectFd = true),
            kernelResource = null,
            initrdResource = null
        )
        val args = provisioner.buildQemuArgs(instance, bootResources = fakeResources)

        val addFdIndex = args.indexOf("-add-fd")
        assertTrue(addFdIndex >= 0)
        assertEquals("fd=102,set=0", args[addFdIndex + 1])

        val driveIndex = args.indexOf("-drive")
        assertTrue(driveIndex >= 0)
        assertEquals("file=/dev/fdset/0,if=virtio,format=raw", args[driveIndex + 1])
    }

    @Test
    fun instanceBootResources_getActiveFds_extractsOnlyDirectFds() {
        val fakePfd42: android.os.ParcelFileDescriptor = io.mockk.mockk(relaxed = true)
        io.mockk.every { fakePfd42.fd } returns 42
        val fakePfd43: android.os.ParcelFileDescriptor = io.mockk.mockk(relaxed = true)
        io.mockk.every { fakePfd43.fd } returns 43

        val resources = InstanceBootResources(
            diskResource = ResolvedBootResource(path = "/proc/self/fd/42", pfd = fakePfd42, isDirectFd = true),
            kernelResource = ResolvedBootResource(path = "/data/local/Image", pfd = null, isDirectFd = false),
            initrdResource = ResolvedBootResource(path = "/proc/self/fd/43", pfd = fakePfd43, isDirectFd = true)
        )

        val activeFds = resources.getActiveFds()
        assertEquals(listOf(42, 43), activeFds)
    }
}
