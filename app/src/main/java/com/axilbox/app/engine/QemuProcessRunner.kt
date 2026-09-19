package com.axilbox.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class QemuProcessRunner(
    private val provisioner: EngineProvisioner
) {
    private var activeProcess: Process? = null
    private var activeNativePid: Int? = null

    val isRunning: Boolean
        get() = activeProcess?.isAlive == true || (activeNativePid != null && activeNativePid!! > 0)

    fun runQemu(
        args: List<String>,
        sessionResources: List<AutoCloseable> = emptyList(),
        preservedFds: List<Int> = emptyList()
    ): Flow<String> = flow {
        val launchArgs = args.toMutableList()
        if (!launchArgs.contains("-L") && provisioner.pcBiosDir.exists()) {
            if (launchArgs.size > 1) {
                launchArgs.addAll(1, listOf("-L", provisioner.pcBiosDir.absolutePath))
            } else {
                launchArgs.addAll(listOf("-L", provisioner.pcBiosDir.absolutePath))
            }
        }

        val workingDir = provisioner.kernelDir.parentFile ?: provisioner.kernelDir
        val nativeLd = provisioner.nativeLibDir.absolutePath
        val existingLd = System.getenv("LD_LIBRARY_PATH") ?: ""
        val targetLd = if (existingLd.isNotEmpty()) "$nativeLd:$existingLd" else nativeLd
        val tmpDir = File(workingDir, "cache").apply { mkdirs() }

        // Check if SAF file descriptors need to survive into QEMU
        // Either passed explicitly via preservedFds or parsed from /proc/self/fd/ in launchArgs
        val explicitFds = (preservedFds + extractProcFds(launchArgs)).distinct()
        val needsNativeSpawn = explicitFds.isNotEmpty()

        if (needsNativeSpawn && NativeEngineBridge.isLoaded) {
            emit("[AxilBox Engine] Preserved SAF FDs detected: $explicitFds. Launching via native fork()+execve()...")
            val envp = listOf(
                "LD_LIBRARY_PATH=$targetLd",
                "TMPDIR=${tmpDir.absolutePath}",
                "PATH=${System.getenv("PATH") ?: "/system/bin"}"
            )

            val qemuBinaryPath = launchArgs[0]
            val spawnResult = NativeEngineBridge.forkAndExecQemu(
                qemuPath = qemuBinaryPath,
                argv = launchArgs,
                envp = envp,
                workingDir = workingDir.absolutePath,
                preservedFds = explicitFds.toIntArray()
            )

            if (spawnResult == null || spawnResult.pid <= 0) {
                emit("[AxilBox Engine] ERROR: Native forkAndExecQemu failed to launch process!")
                sessionResources.forEach {
                    try {
                        it.close()
                    } catch (_: Exception) {}
                }
                return@flow
            }

            activeNativePid = spawnResult.pid
            emit("[AxilBox Engine] QEMU process started via native fork+exec (PID: ${spawnResult.pid})")

            val pfd = try {
                android.os.ParcelFileDescriptor.adoptFd(spawnResult.stdoutFd)
            } catch (t: Throwable) {
                null
            }
            val stdinPfd = try {
                if (spawnResult.stdinFd >= 0) android.os.ParcelFileDescriptor.adoptFd(spawnResult.stdinFd) else null
            } catch (_: Throwable) {
                null
            }

            if (pfd != null) {
                val inputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                val reader = BufferedReader(InputStreamReader(inputStream))
                try {
                    var line: String? = reader.readLine()
                    while (line != null) {
                        emit(line)
                        line = reader.readLine()
                    }
                } catch (_: Exception) {
                    // Stream closed
                } finally {
                    reader.close()
                    try { stdinPfd?.close() } catch (_: Exception) {}
                    val exitCode = NativeEngineBridge.waitForProcess(spawnResult.pid)
                    emit("[AxilBox Engine] QEMU process terminated with exit code $exitCode")
                    activeNativePid = null
                    sessionResources.forEach {
                        try {
                            it.close()
                        } catch (_: Exception) {}
                    }
                }
            } else {
                emit("[AxilBox Engine] ERROR: Failed to adopt stdout pipe descriptor (fd=${spawnResult.stdoutFd})")
                try { stdinPfd?.close() } catch (_: Exception) {}
                val exitCode = NativeEngineBridge.waitForProcess(spawnResult.pid)
                emit("[AxilBox Engine] QEMU process terminated with exit code $exitCode")
                activeNativePid = null
                sessionResources.forEach {
                    try {
                        it.close()
                    } catch (_: Exception) {}
                }
            }
        } else {
            if (needsNativeSpawn && !NativeEngineBridge.isLoaded) {
                emit("[AxilBox Engine] WARN: SAF FDs present but native bridge not loaded; falling back to ProcessBuilder.")
            }
            val processBuilder = ProcessBuilder(launchArgs)
            processBuilder.directory(workingDir)

            val env = processBuilder.environment()
            val pExistingLd = env["LD_LIBRARY_PATH"] ?: ""
            env["LD_LIBRARY_PATH"] = if (pExistingLd.isNotEmpty()) "$nativeLd:$pExistingLd" else nativeLd
            env["TMPDIR"] = tmpDir.absolutePath

            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            activeProcess = process

            emit("[AxilBox Engine] QEMU process started (PID: ${getProcessPid(process)})")

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    emit(line)
                    line = reader.readLine()
                }
            } catch (_: Exception) {
                // Stream closed
            } finally {
                reader.close()
                val exitCode = try { process.waitFor() } catch (_: Exception) { -1 }
                emit("[AxilBox Engine] QEMU process terminated with exit code $exitCode")
                activeProcess = null
                // Close any held session resources (e.g. SAF ParcelFileDescriptors)
                sessionResources.forEach {
                    try {
                        it.close()
                    } catch (_: Exception) {
                        // Ignore close exceptions on cleanup
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun stop() = withContext(Dispatchers.IO) {
        activeProcess?.let { process ->
            try {
                process.destroy()
            } catch (_: Exception) {
                // Ignore
            }
        }
        activeProcess = null

        activeNativePid?.let { pid ->
            try {
                NativeEngineBridge.killProcess(pid, 15)
            } catch (_: Exception) {
                // Ignore
            }
        }
        activeNativePid = null
    }

    fun runDiagnosticProbe(): Flow<String> = flow {
        emit("[AxilBox Diagnostic] ============================================================")
        emit("[AxilBox Diagnostic] STARTING ON-DEVICE QEMU -add-fd / HMP ISOLATION PROBE")
        emit("[AxilBox Diagnostic] ============================================================")

        if (!NativeEngineBridge.isLoaded) {
            emit("[AxilBox Diagnostic] ERROR: NativeEngineBridge is not loaded!")
            return@flow
        }
        if (!provisioner.qemuBinary.exists()) {
            emit("[AxilBox Diagnostic] ERROR: QEMU binary not found at ${provisioner.qemuBinary.absolutePath}!")
            return@flow
        }

        val workingDir = provisioner.kernelDir.parentFile ?: provisioner.kernelDir
        val nativeLd = provisioner.nativeLibDir.absolutePath
        val existingLd = System.getenv("LD_LIBRARY_PATH") ?: ""
        val targetLd = if (existingLd.isNotEmpty()) "$nativeLd:$existingLd" else nativeLd
        val tmpDir = File(workingDir, "cache").apply { mkdirs() }
        val envp = listOf(
            "LD_LIBRARY_PATH=$targetLd",
            "TMPDIR=${tmpDir.absolutePath}",
            "PATH=${System.getenv("PATH") ?: "/system/bin"}"
        )

        // ---------------------------------------------------------------------
        // TEST 1: Query qemu-system-aarch64 --help for -add-fd
        // ---------------------------------------------------------------------
        emit("[AxilBox Diagnostic] [Test 1/2] Probing '${provisioner.qemuBinary.name} --help' for -add-fd...")
        val helpArgs = listOf(provisioner.qemuBinary.absolutePath, "--help")
        val helpSpawn = NativeEngineBridge.forkAndExecQemu(
            qemuPath = provisioner.qemuBinary.absolutePath,
            argv = helpArgs,
            envp = envp,
            workingDir = workingDir.absolutePath,
            preservedFds = intArrayOf()
        )

        if (helpSpawn != null && helpSpawn.pid > 0) {
            if (helpSpawn.stdinFd >= 0) {
                try { android.os.ParcelFileDescriptor.adoptFd(helpSpawn.stdinFd).close() } catch (_: Exception) {}
            }
            val pfd = try { android.os.ParcelFileDescriptor.adoptFd(helpSpawn.stdoutFd) } catch (_: Throwable) { null }
            if (pfd != null) {
                val reader = BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)))
                var addFdMatched = false
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.contains("add-fd", ignoreCase = true) || line.contains("fdset", ignoreCase = true)) {
                        emit("[QEMU --help] $line")
                        addFdMatched = true
                    }
                    line = reader.readLine()
                }
                reader.close()
                NativeEngineBridge.waitForProcess(helpSpawn.pid)
                if (addFdMatched) {
                    emit("[AxilBox Diagnostic] ✓ -add-fd is officially listed in QEMU --help options!")
                } else {
                    emit("[AxilBox Diagnostic] ⚠️ -add-fd NOT found in QEMU --help output!")
                }
            }
        } else {
            emit("[AxilBox Diagnostic] ERROR: Failed to fork QEMU for --help check!")
        }

        // ---------------------------------------------------------------------
        // TEST 2: Run QEMU with -add-fd and send 'info fdsets' over stdin
        // ---------------------------------------------------------------------
        emit("[AxilBox Diagnostic] ------------------------------------------------------------")
        emit("[AxilBox Diagnostic] [Test 2/2] Running HMP 'info fdsets' probe...")

        val testFile = File(tmpDir, "probe_test_fd.tmp").apply { writeText("axilbox_test") }
        val testPfd = try {
            android.os.ParcelFileDescriptor.open(testFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            emit("[AxilBox Diagnostic] ERROR: Failed to open test ParcelFileDescriptor: ${e.message}")
            return@flow
        }
        val testFd = testPfd.fd
        emit("[AxilBox Diagnostic] Created test file descriptor: fd=$testFd")

        try {
            android.system.Os.fcntlInt(testPfd.fileDescriptor, android.system.OsConstants.F_SETFD, 0)
        } catch (_: Exception) {}

        val probeArgs = mutableListOf(
            provisioner.qemuBinary.absolutePath,
            "-add-fd", "fd=$testFd,set=0",
            "-nographic",
            "-monitor", "stdio",
            "-S",
            "-display", "none"
        )
        if (provisioner.pcBiosDir.exists()) {
            probeArgs.addAll(1, listOf("-L", provisioner.pcBiosDir.absolutePath))
        }

        emit("[AxilBox Diagnostic] Launching: ${probeArgs.joinToString(" ")}")
        val probeSpawn = NativeEngineBridge.forkAndExecQemu(
            qemuPath = provisioner.qemuBinary.absolutePath,
            argv = probeArgs,
            envp = envp,
            workingDir = workingDir.absolutePath,
            preservedFds = intArrayOf(testFd)
        )

        if (probeSpawn == null || probeSpawn.pid <= 0) {
            emit("[AxilBox Diagnostic] ERROR: Failed to launch QEMU for HMP probe!")
            testPfd.close()
            testFile.delete()
            return@flow
        }

        emit("[AxilBox Diagnostic] QEMU probe process spawned (PID=${probeSpawn.pid}).")
        emit("[AxilBox Diagnostic] Sending HMP command 'info fdsets' over stdin...")

        if (probeSpawn.stdinFd >= 0) {
            try {
                val stdinPfd = android.os.ParcelFileDescriptor.adoptFd(probeSpawn.stdinFd)
                val outStream = android.os.ParcelFileDescriptor.AutoCloseOutputStream(stdinPfd)
                outStream.write("info fdsets\nquit\n".toByteArray(Charsets.UTF_8))
                outStream.flush()
                outStream.close()
            } catch (e: Exception) {
                emit("[AxilBox Diagnostic] WARN: Exception writing to stdin: ${e.message}")
            }
        }

        val outPfd = try { android.os.ParcelFileDescriptor.adoptFd(probeSpawn.stdoutFd) } catch (_: Throwable) { null }
        if (outPfd != null) {
            val reader = BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(outPfd)))
            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    emit("[HMP Output] $line")
                    line = reader.readLine()
                }
            } catch (_: Exception) {
            } finally {
                reader.close()
            }
        }

        val exitCode = NativeEngineBridge.waitForProcess(probeSpawn.pid)
        emit("[AxilBox Diagnostic] QEMU probe process exited with code $exitCode")

        testPfd.close()
        testFile.delete()

        emit("[AxilBox Diagnostic] ============================================================")
        emit("[AxilBox Diagnostic] END OF QEMU -add-fd / HMP ISOLATION PROBE")
        emit("[AxilBox Diagnostic] ============================================================")
    }.flowOn(Dispatchers.IO)

    private fun extractProcFds(args: List<String>): List<Int> {
        val procRegex = """/proc/self/fd/(\d+)""".toRegex()
        val addFdRegex = """(?:^|,|\s)fd=(\d+)""".toRegex()
        return args.flatMap { arg ->
            procRegex.findAll(arg).mapNotNull { it.groupValues[1].toIntOrNull() } +
            addFdRegex.findAll(arg).mapNotNull { it.groupValues[1].toIntOrNull() }
        }.distinct()
    }

    private fun getProcessPid(process: Process): Long {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getLong(process)
        } catch (_: Exception) {
            -1L
        }
    }
}
