package com.axilbox.app.engine

import android.content.Context
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.util.ResolvedBootResource
import com.axilbox.app.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages open boot media resources (such as SAF ParcelFileDescriptors) during the lifetime
 * of an active QEMU guest session and holds them until termination.
 */
data class InstanceBootResources(
    val diskResource: ResolvedBootResource? = null,
    val kernelResource: ResolvedBootResource? = null,
    val initrdResource: ResolvedBootResource? = null,
    val logMessages: List<String> = emptyList()
) : AutoCloseable {
    override fun close() {
        diskResource?.close()
        kernelResource?.close()
        initrdResource?.close()
    }
}

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

            // Copy bundled guest kernel Image from assets to app private storage if not already present
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

    /**
     * Resolves the instance's configured boot media (disk image, kernel, or initramfs)
     * using SAF ParcelFileDescriptor passthrough (/proc/self/fd/<fd>) with explicit
     * copyUriToCache fallback.
     */
    fun resolveInstanceBootResources(instance: VirtualInstance): InstanceBootResources {
        val logs = mutableListOf<String>()

        // 1. Resolve disk image if configured
        val diskRes = if (!instance.imageUri.isNullOrBlank()) {
            val res = UriUtils.resolveBootResource(context, instance.imageUri, writable = true)
            if (res != null) {
                val desc = when {
                    res.isDirectFd -> "SAF direct descriptor (${res.path})"
                    res.isFallbackCopy -> "cache copy fallback (${res.path})"
                    else -> "local file path (${res.path})"
                }
                logs.add("[Storage] Disk image mapped via $desc")
            } else {
                logs.add("[Storage] Warning: Failed to resolve configured disk image '${instance.imageUri}'")
            }
            res
        } else null

        // 2. Resolve custom kernel image if configured
        val kernelRes = if (!instance.kernelUri.isNullOrBlank()) {
            val res = UriUtils.resolveBootResource(context, instance.kernelUri, writable = false)
            if (res != null) {
                val desc = when {
                    res.isDirectFd -> "SAF direct descriptor (${res.path})"
                    res.isFallbackCopy -> "cache copy fallback (${res.path})"
                    else -> "local file path (${res.path})"
                }
                logs.add("[Kernel] Custom kernel mapped via $desc")
            } else {
                logs.add("[Kernel] Warning: Failed to resolve custom kernel URI '${instance.kernelUri}'")
            }
            res
        } else null

        // 3. Resolve custom initramfs if configured
        val initrdRes = if (!instance.initrdUri.isNullOrBlank()) {
            val res = UriUtils.resolveBootResource(context, instance.initrdUri, writable = false)
            if (res != null) {
                val desc = when {
                    res.isDirectFd -> "SAF direct descriptor (${res.path})"
                    res.isFallbackCopy -> "cache copy fallback (${res.path})"
                    else -> "local file path (${res.path})"
                }
                logs.add("[Initrd] Custom initramfs mapped via $desc")
            } else {
                logs.add("[Initrd] Warning: Failed to resolve initrd URI '${instance.initrdUri}'")
            }
            res
        } else null

        return InstanceBootResources(
            diskResource = diskRes,
            kernelResource = kernelRes,
            initrdResource = initrdRes,
            logMessages = logs
        )
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

    fun buildKernelCmdline(instance: VirtualInstance): String {
        // Base required parameters for virt machine serial earlycon
        val baseParams = linkedMapOf(
            "console" to "console=ttyAMA0",
            "earlycon" to "earlycon=pl011,0x09000000",
            "panic" to "panic=-1"
        )
        // If an initrd is present, include rdinit=/sbin/init as base default
        if (!instance.initrdUri.isNullOrBlank()) {
            baseParams["rdinit"] = "rdinit=/sbin/init"
        }

        val userTokens = instance.extraCmdline.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val orderedUserTokens = mutableListOf<String>()

        for (token in userTokens) {
            val key = token.substringBefore("=")
            if (baseParams.containsKey(key)) {
                // User explicitly provided this parameter; override default without duplicating
                baseParams.remove(key)
            }
            orderedUserTokens.add(token)
        }

        // Remaining base defaults first, followed by user tokens (no duplicates)
        val combined = baseParams.values + orderedUserTokens
        return combined.joinToString(" ")
    }

    fun buildQemuArgs(
        instance: VirtualInstance,
        bootResources: InstanceBootResources? = null,
        customKernelPath: String? = null,
        customInitrdPath: String? = null
    ): List<String> {
        val kernelPath = customKernelPath
            ?: bootResources?.kernelResource?.path
            ?: instance.kernelUri
            ?: (if (isKernelAvailable()) bundledKernelImage.absolutePath else null)

        val args = mutableListOf(
            qemuBinary.absolutePath,
            "-L", pcBiosDir.absolutePath,
            "-M", "virt,gic-version=3",
            "-cpu", "cortex-a57",
            "-smp", instance.vCpuCount.toString(),
            "-m", "${instance.ramMb}M"
        )

        if (!kernelPath.isNullOrBlank()) {
            args.addAll(listOf("-kernel", kernelPath))
        }

        // Initrd: ONLY from custom path, resolved boot resource, or instance.initrdUri.
        // No baked-in fallback initrd.
        val resolvedInitrd = customInitrdPath
            ?: bootResources?.initrdResource?.path
            ?: instance.initrdUri

        if (!resolvedInitrd.isNullOrBlank()) {
            args.addAll(listOf("-initrd", resolvedInitrd))
        }

        // Disk image if supplied
        val resolvedDisk = bootResources?.diskResource?.path ?: instance.imageUri
        if (!resolvedDisk.isNullOrBlank()) {
            args.addAll(listOf(
                "-drive", "file=$resolvedDisk,if=virtio,format=raw"
            ))
        }

        // Serial and kernel cmdline with deduplicated arguments
        val cmdline = buildKernelCmdline(instance)

        args.addAll(listOf(
            "-append", cmdline,
            "-display", "none",
            "-monitor", "none",
            "-serial", "stdio",
            "-no-reboot"
        ))

        return args
    }
}
