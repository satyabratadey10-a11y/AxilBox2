package com.axilbox.app.engine

import java.io.File

object NativeEngineBridge {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("axilbox-native")
            isNativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    fun hasExecutable(path: String): Boolean {
        return if (isNativeLoaded) {
            try {
                nativeHasExecutable(path)
            } catch (_: Throwable) {
                fallbackHasExecutable(path)
            }
        } else {
            fallbackHasExecutable(path)
        }
    }

    fun chmodExecutable(path: String): Boolean {
        return if (isNativeLoaded) {
            try {
                nativeChmodExecutable(path)
            } catch (_: Throwable) {
                fallbackChmodExecutable(path)
            }
        } else {
            fallbackChmodExecutable(path)
        }
    }

    private fun fallbackHasExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }

    private fun fallbackChmodExecutable(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val success = file.setExecutable(true, false)
        if (!success) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
                p.waitFor()
                return p.exitValue() == 0
            } catch (_: Exception) {
                return false
            }
        }
        return true
    }

    fun formatBiosArg(biosPath: String): String {
        return if (isNativeLoaded) {
            try {
                nativeFormatBiosArg(biosPath)
            } catch (_: Throwable) {
                "-L $biosPath"
            }
        } else {
            "-L $biosPath"
        }
    }

    val isLoaded: Boolean
        get() = isNativeLoaded

    data class NativeSpawnResult(
        val pid: Int,
        val stdoutFd: Int
    )

    fun forkAndExecQemu(
        qemuPath: String,
        argv: List<String>,
        envp: List<String>,
        workingDir: String,
        preservedFds: IntArray
    ): NativeSpawnResult? {
        if (!isNativeLoaded) return null
        return try {
            val res = nativeForkAndExecQemu(
                qemuPath,
                argv.toTypedArray(),
                envp.toTypedArray(),
                workingDir,
                preservedFds
            )
            if (res != null && res.size >= 2 && res[0] > 0) {
                NativeSpawnResult(pid = res[0], stdoutFd = res[1])
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun waitForProcess(pid: Int): Int {
        if (!isNativeLoaded || pid <= 0) return -1
        return try {
            nativeWaitForProcess(pid)
        } catch (_: Throwable) {
            -1
        }
    }

    fun killProcess(pid: Int, signal: Int = 15): Boolean {
        if (!isNativeLoaded || pid <= 0) return false
        return try {
            nativeKillProcess(pid, signal)
        } catch (_: Throwable) {
            false
        }
    }

    private external fun nativeHasExecutable(path: String): Boolean
    private external fun nativeChmodExecutable(path: String): Boolean
    private external fun nativeFormatBiosArg(path: String): String

    private external fun nativeForkAndExecQemu(
        qemuPath: String,
        argv: Array<String>,
        envp: Array<String>,
        workingDir: String,
        preservedFds: IntArray
    ): IntArray?

    private external fun nativeWaitForProcess(pid: Int): Int
    private external fun nativeKillProcess(pid: Int, sig: Int): Boolean
}
