package com.axilbox.app.engine

import android.content.Context
import com.axilbox.app.model.VirtualInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class EngineProvisioner(private val context: Context) {

    val engineDir: File
        get() = File(context.filesDir, "engine")

    val kernelDir: File
        get() = File(context.filesDir, "kernel")

    val qemuBinary: File
        get() = File(engineDir, "qemu-system-aarch64")

    val bundledKernelImage: File
        get() = File(kernelDir, "Image")

    val libDir: File
        get() = File(engineDir, "lib")

    fun isEngineAvailable(): Boolean {
        return qemuBinary.exists() && NativeEngineBridge.hasExecutable(qemuBinary.absolutePath)
    }

    fun isKernelAvailable(): Boolean {
        return bundledKernelImage.exists() && bundledKernelImage.length() > 0
    }

    suspend fun provisionEngineIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!engineDir.exists()) {
                engineDir.mkdirs()
            }
            if (!kernelDir.exists()) {
                kernelDir.mkdirs()
            }
            if (!libDir.exists()) {
                libDir.mkdirs()
            }

            // Copy engine binaries and shared libraries from assets
            copyAssetFolder("engine", engineDir)

            // Copy kernel from assets
            copyAssetFolder("kernel", kernelDir)

            // Make QEMU binary executable
            if (qemuBinary.exists()) {
                NativeEngineBridge.chmodExecutable(qemuBinary.absolutePath)
            }

            // Make any .so in libDir executable
            libDir.listFiles()?.forEach { libFile ->
                if (libFile.isFile) {
                    NativeEngineBridge.chmodExecutable(libFile.absolutePath)
                }
            }

            isEngineAvailable()
        } catch (_: Exception) {
            false
        }
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        for (filename in files) {
            val subAssetPath = if (assetPath.isEmpty()) filename else "$assetPath/$filename"
            val targetFile = File(targetDir, filename)

            val subFiles = assetManager.list(subAssetPath)
            if (!subFiles.isNullOrEmpty()) {
                targetFile.mkdirs()
                copyAssetFolder(subAssetPath, targetFile)
            } else {
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    try {
                        assetManager.open(subAssetPath).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {
                        // Skip unreadable asset entries
                    }
                }
            }
        }
    }

    fun buildQemuArgs(instance: VirtualInstance, customKernelPath: String? = null): List<String> {
        val kernelPath = customKernelPath ?: instance.kernelUri ?: bundledKernelImage.absolutePath

        val args = mutableListOf(
            qemuBinary.absolutePath,
            "-M", "virt,gic-version=3",
            "-cpu", "cortex-a57",
            "-smp", instance.vCpuCount.toString(),
            "-m", "${instance.ramMb}M",
            "-kernel", kernelPath
        )

        // Initrd if supplied
        if (!instance.initrdUri.isNullOrBlank()) {
            args.addAll(listOf("-initrd", instance.initrdUri))
        }

        // Disk image if supplied
        if (!instance.imageUri.isNullOrBlank()) {
            args.addAll(listOf(
                "-drive", "file=${instance.imageUri},if=virtio,format=raw"
            ))
        }

        // Serial and kernel cmdline
        val cmdlineBuilder = StringBuilder("console=ttyAMA0 earlycon=pl011,0x09000000 panic=-1")
        if (instance.extraCmdline.isNotBlank()) {
            cmdlineBuilder.append(" ").append(instance.extraCmdline.trim())
        }

        args.addAll(listOf(
            "-append", cmdlineBuilder.toString(),
            "-nographic",
            "-serial", "stdio",
            "-no-reboot"
        ))

        return args
    }
}
