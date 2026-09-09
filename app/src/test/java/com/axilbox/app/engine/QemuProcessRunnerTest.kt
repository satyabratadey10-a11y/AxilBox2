package com.axilbox.app.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QemuProcessRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val appInfo = ApplicationInfo()
    private lateinit var fakeNativeLibDir: File
    private lateinit var fakeFilesDir: File
    private lateinit var provisioner: EngineProvisioner
    private lateinit var runner: QemuProcessRunner

    @Before
    fun setup() {
        fakeNativeLibDir = tempFolder.newFolder("lib")
        fakeFilesDir = tempFolder.newFolder("files")
        appInfo.nativeLibraryDir = fakeNativeLibDir.absolutePath
        every { context.applicationInfo } returns appInfo
        every { context.filesDir } returns fakeFilesDir

        provisioner = EngineProvisioner(context)
        runner = QemuProcessRunner(provisioner)
    }

    @Test
    fun nativeEngineBridge_whenNotLoaded_safelyReturnsNullOrFallback() {
        // In headless JVM unit test runner, ARM64 .so cannot be loaded
        assertFalse(NativeEngineBridge.isLoaded)
        assertFalse(NativeEngineBridge.hasExecutable("/non/existent/path"))
        assertFalse(NativeEngineBridge.chmodExecutable("/non/existent/path"))
        val spawnRes = NativeEngineBridge.forkAndExecQemu(
            qemuPath = "/bin/echo",
            argv = listOf("echo", "hello"),
            envp = emptyList(),
            workingDir = "/",
            preservedFds = intArrayOf(3)
        )
        // Must safely return null without throwing UnsatisfiedLinkError
        assertTrue(spawnRes == null)
    }

    @Test
    fun runQemu_withProcFd_fallsBackGracefullyWhenNativeNotLoaded() = runTest {
        val testEcho = if (File("/bin/echo").exists()) "/bin/echo" else "echo"
        val launchArgs = listOf(testEcho, "-drive", "file=/proc/self/fd/99,if=virtio,format=raw")

        val logs = runner.runQemu(
            args = launchArgs,
            sessionResources = emptyList(),
            preservedFds = listOf(99)
        ).take(3).toList()

        assertNotNull(logs)
        // Verifies runner handled the request and either emitted process started or fallback warning
        assertTrue(logs.isNotEmpty())
    }
}
