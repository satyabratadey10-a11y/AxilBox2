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

    val isRunning: Boolean
        get() = activeProcess?.isAlive == true

    fun runQemu(args: List<String>): Flow<String> = flow {
        val processBuilder = ProcessBuilder(args)
        processBuilder.directory(provisioner.engineDir)

        val env = processBuilder.environment()
        val existingLd = env["LD_LIBRARY_PATH"] ?: ""
        val customLd = "${provisioner.libDir.absolutePath}:${provisioner.engineDir.absolutePath}"
        env["LD_LIBRARY_PATH"] = if (existingLd.isNotEmpty()) "$customLd:$existingLd" else customLd
        env["TMPDIR"] = provisioner.engineDir.absolutePath

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
