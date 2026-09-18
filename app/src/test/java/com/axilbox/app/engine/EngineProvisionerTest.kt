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
import org.junit.Assert.assertNotNull
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
    fun buildQemuArgs_withReadOnlySafDisk_emitsReadOnlyDrive() {
        val instance = VirtualInstance(
            id = 6L,
            name = "ReadOnlyDiskInstance",
            osType = OsType.LINUX_GENERIC,
            ramMb = 2048,
            vCpuCount = 2,
            imageUri = "content://saf/alpine.img"
        )
        val fakeResources = InstanceBootResources(
            diskResource = ResolvedBootResource(path = "/proc/self/fd/102", isDirectFd = true, isReadOnly = true),
            kernelResource = null,
            initrdResource = null
        )
        val args = provisioner.buildQemuArgs(instance, bootResources = fakeResources)

        val addFdIndex = args.indexOf("-add-fd")
        assertTrue(addFdIndex >= 0)
        assertEquals("fd=102,set=0", args[addFdIndex + 1])

        val driveIndex = args.indexOf("-drive")
        assertTrue(driveIndex >= 0)
        assertEquals("file=/dev/fdset/0,if=virtio,format=raw,readonly=on", args[driveIndex + 1])
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

    @Test
    fun buildQemuArgs_allSafResources_strictlySatisfyFdSetInvariants() {
        val instance = VirtualInstance(
            id = 7L,
            name = "AllSafInstance",
            osType = OsType.DEBIAN_ARM64,
            ramMb = 2048,
            vCpuCount = 2,
            kernelUri = "content://saf/custom_kernel",
            initrdUri = "content://saf/alpine.cpio.gz",
            imageUri = "content://saf/rootfs.img"
        )
        val allSafResources = InstanceBootResources(
            kernelResource = ResolvedBootResource(path = "/proc/self/fd/101", isDirectFd = true),
            initrdResource = ResolvedBootResource(path = "/proc/self/fd/102", isDirectFd = true),
            diskResource = ResolvedBootResource(path = "/proc/self/fd/103", isDirectFd = true, isReadOnly = true)
        )
        val args = provisioner.buildQemuArgs(instance, bootResources = allSafResources)

        // Verify invariant helper with all boot resources
        assertFdSetInvariants(args, allSafResources)

        // Verify distinct sets: kernel=0, initrd=1, disk=2
        val kIndex = args.indexOf("-kernel")
        assertEquals("/dev/fdset/0", args[kIndex + 1])
        val iIndex = args.indexOf("-initrd")
        assertEquals("/dev/fdset/1", args[iIndex + 1])
        val dIndex = args.indexOf("-drive")
        assertEquals("file=/dev/fdset/2,if=virtio,format=raw,readonly=on", args[dIndex + 1])
    }

    @Test
    fun buildQemuArgs_singleSafInitrdWithBundledKernel_strictlySatisfiesFdSetInvariants() {
        val instance = VirtualInstance(
            id = 8L,
            name = "AlpineInitrdInstance",
            osType = OsType.LINUX_GENERIC,
            ramMb = 1024,
            vCpuCount = 1,
            initrdUri = "content://saf/alpine-minirootfs.cpio.gz"
        )
        val mockPfd125: android.os.ParcelFileDescriptor = io.mockk.mockk(relaxed = true)
        io.mockk.every { mockPfd125.fd } returns 125

        val initrdOnlyResource = InstanceBootResources(
            kernelResource = null, // uses bundled kernel
            initrdResource = ResolvedBootResource(path = "/proc/self/fd/125", pfd = mockPfd125, isDirectFd = true, isReadOnly = true),
            diskResource = null
        )
        val args = provisioner.buildQemuArgs(instance, bootResources = initrdOnlyResource)

        // Verify invariant helper with boot resources
        assertFdSetInvariants(args, initrdOnlyResource)

        // Kernel must be bundled local path (no /dev/fdset)
        val kIndex = args.indexOf("-kernel")
        assertFalse(args[kIndex + 1].contains("/dev/fdset"))

        // Initrd must be set=0
        val iIndex = args.indexOf("-initrd")
        assertEquals("/dev/fdset/0", args[iIndex + 1])

        val addFdIndex = args.indexOf("-add-fd")
        assertTrue("Expected -add-fd in args", addFdIndex >= 0)
        assertEquals("fd=125,set=0", args[addFdIndex + 1])
        assertTrue("Expected -add-fd to precede -initrd", addFdIndex < iIndex)
    }

    @Test(expected = AssertionError::class)
    fun assertFdSetInvariants_failsWhenAddFdMissingForDevFdset() {
        val badArgs = listOf(
            "/data/app/qemu",
            "-initrd", "/dev/fdset/0"
        )
        assertFdSetInvariants(badArgs)
    }

    @Test(expected = AssertionError::class)
    fun assertFdSetInvariants_failsWhenSetNumbersDiverge() {
        val badArgs = listOf(
            "/data/app/qemu",
            "-add-fd", "fd=125,set=0",
            "-initrd", "/dev/fdset/1"
        )
        assertFdSetInvariants(badArgs)
    }

    @Test(expected = AssertionError::class)
    fun assertFdSetInvariants_failsWhenInheritedFdNotRegistered() {
        val mockPfd125: android.os.ParcelFileDescriptor = io.mockk.mockk(relaxed = true)
        io.mockk.every { mockPfd125.fd } returns 125
        val resources = InstanceBootResources(
            initrdResource = ResolvedBootResource(path = "/proc/self/fd/125", pfd = mockPfd125, isDirectFd = true)
        )
        // Args without -add-fd for 125
        val badArgs = listOf(
            "/data/app/qemu",
            "-initrd", "/data/app/initrd.img"
        )
        assertFdSetInvariants(badArgs, resources)
    }

    private fun assertFdSetInvariants(args: List<String>, bootResources: InstanceBootResources? = null) {
        val devFdsetRegex = """/dev/fdset/(\d+)""".toRegex()
        val addFdRegex = """fd=(\d+),set=(\d+)""".toRegex()

        // 1. Collect all -add-fd specifications and their index in argv
        val registeredFdSets = mutableMapOf<Int, Int>() // setIndex -> argIndex
        val registeredFds = mutableSetOf<Int>()
        for (i in 0 until args.size - 1) {
            if (args[i] == "-add-fd") {
                val match = addFdRegex.matchEntire(args[i + 1])
                assertNotNull("-add-fd argument must match 'fd=<M>,set=<N>', got: '${args[i + 1]}'", match)
                val rawFd = match!!.groupValues[1].toInt()
                val setNum = match.groupValues[2].toInt()
                registeredFdSets[setNum] = i
                registeredFds.add(rawFd)
            }
        }

        // 2. Check that every inherited FD from bootResources is registered via -add-fd
        if (bootResources != null) {
            val activeFds = bootResources.getActiveFds()
            for (fd in activeFds) {
                assertTrue(
                    "Inherited active fd $fd from bootResources was not registered via -add-fd in argv: $args",
                    registeredFds.contains(fd)
                )
            }
        }

        // 3. Check every -kernel, -initrd, -drive references a valid preceding -add-fd
        val referencedSets = mutableSetOf<Int>()
        for (i in 0 until args.size - 1) {
            val opt = args[i]
            if (opt in listOf("-kernel", "-initrd", "-drive")) {
                val value = args[i + 1]
                val match = devFdsetRegex.find(value)
                if (match != null) {
                    val setNum = match.groupValues[1].toInt()
                    assertTrue(
                        "Argument '$opt $value' at index $i references /dev/fdset/$setNum, but no -add-fd ... set=$setNum was found earlier in argv",
                        registeredFdSets.containsKey(setNum) && registeredFdSets[setNum]!! < i
                    )
                    referencedSets.add(setNum)
                }
            }
        }

        // 4. Check that there are no orphaned registered sets with diverging set numbers
        for (setNum in registeredFdSets.keys) {
            assertTrue(
                "Registered set=$setNum via -add-fd was never referenced by -kernel, -initrd, or -drive",
                referencedSets.contains(setNum)
            )
        }
    }
}
