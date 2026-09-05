package com.axilbox.app.engine

import android.content.Context
import com.axilbox.app.model.VirtualInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EngineProvisioner(private val context: Context) {

    /**
     * Native library directory where Android extracts lib*.so files at install time
     * (exempt from Android 10+ W^X / noexec restriction on writable data directories).
     */
    val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    /**
     * Packaged QEMU aarch64 binary named per Android's required lib*.so pattern.
     */
    val qemuBinary: File
        get() = File(nativeLibDir, "libqemu_system_aarch64.so")

    val kernelDir: File
        get() = File(context.filesDir, "kernel")

    val bundledKernelImage: File
        get() = File(kernelDir, "Image")

    val engineDir: File
        get() = File(context.filesDir, "engine")

    val pcBiosDir: File
        get() = File(engineDir, "pc-bios")

    fun isEngineAvailable(): Boolean {
        return qemuBinary.exists() && (qemuBinary.canExecute() || NativeEngineBridge.hasExecutable(qemuBinary.absolutePath))
    }

    fun isKernelAvailable(): Boolean {
        return bundledKernelImage.exists() && bundledKernelImage.length() > 0
    }

    fun isPcBiosAvailable(): Boolean {
        return pcBiosDir.exists() && (File(pcBiosDir, "efi-virtio.rom").exists() || (pcBiosDir.listFiles()?.isNotEmpty() == true))
    }

    suspend fun provisionEngineIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!kernelDir.exists()) {
                kernelDir.mkdirs()
            }
            if (!engineDir.exists()) {
                engineDir.mkdirs()
            }

            // Copy bundled guest kernel from assets to app private storage if not already present
            if (!isKernelAvailable()) {
                copyAssetFolder("kernel", kernelDir)
            }

            // Copy bundled QEMU pc-bios option-ROMs/firmware from assets to app private storage
            if (!isPcBiosAvailable()) {
                pcBiosDir.mkdirs()
                copyAssetFolder("engine/pc-bios", pcBiosDir)
            }

            isEngineAvailable() && isKernelAvailable()
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
            "-L", pcBiosDir.absolutePath,
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
            "-display", "none",
            "-monitor", "none",
            "-serial", "stdio",
            "-no-reboot"
        ))

        return args
    }
}
