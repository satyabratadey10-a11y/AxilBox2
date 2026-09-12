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
