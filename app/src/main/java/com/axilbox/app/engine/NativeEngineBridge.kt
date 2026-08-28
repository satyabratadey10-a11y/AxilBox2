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

    private external fun nativeHasExecutable(path: String): Boolean
    private external fun nativeChmodExecutable(path: String): Boolean
}
