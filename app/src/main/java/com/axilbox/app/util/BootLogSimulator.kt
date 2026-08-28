package com.axilbox.app.util

import com.axilbox.app.model.VirtualInstance

object BootLogSimulator {

    data class LogEntry(
        val timestampSec: Double,
        val message: String,
        val level: LogLevel = LogLevel.INFO
    )

    enum class LogLevel {
        INFO, WARN, ERROR, SUCCESS
    }

    fun generateBootSequence(instance: VirtualInstance): List<LogEntry> {
        val ram = instance.ramMb
        val vcpus = instance.vCpuCount
        val os = instance.osType.displayName
        val cmdline = instance.extraCmdline.ifBlank { instance.osType.defaultCmdline }

        return listOf(
            LogEntry(0.000000, "[AxilBox-Virt] Initializing ARM64 virtual environment (virt-9.0)..."),
            LogEntry(0.000010, "[AxilBox-Virt] Machine model: QEMU ARM Virtual Machine (aarch64-virt)"),
            LogEntry(0.000020, "[AxilBox-Virt] Target CPU: cortex-a76 with GICv3 (vCPUs: $vcpus, RAM: ${ram}MB)"),
            LogEntry(0.000035, "[AxilBox-Virt] DeviceTree blob loaded at 0x40000000 (size: 65536 bytes)"),
            LogEntry(0.000050, "[Kernel] Booting Linux on physical CPU 0x0000000000 [0x410fd034]"),
            LogEntry(0.000120, "[Kernel] Linux version 6.6.0-axilbox-virt (buildroot@axilbox) (aarch64-linux-gnu-gcc 13.2.0)"),
            LogEntry(0.000200, "[Kernel] Kernel command line: $cmdline"),
            LogEntry(0.000400, "[Kernel] earlycon: pl011 at MMIO 0x0000000009000000 (options '')"),
            LogEntry(0.001200, "[Kernel] Memory: ${ram}MB available (kernel: 18MB, initrd: 8MB, reserved: 32MB)"),
            LogEntry(0.002500, "[Kernel] Primary CPU 0 online; secondary CPU 1-$(vcpus-1) starting..."),
            LogEntry(0.004800, "[Kernel] PCI: host bridge /platform/10000000.pcie ranges:"),
            LogEntry(0.005100, "[Kernel] PCI:   IO 0x000000003eff0000..0x000000003effffff -> 0x0000000000000000"),
            LogEntry(0.005300, "[Kernel] PCI:  MEM 0x0000000010000000..0x000000003efeffff -> 0x0000000010000000"),
            LogEntry(0.008400, "[Kernel] virtio-pci 0000:00:01.0: enabling device (0000 -> 0002)"),
            LogEntry(0.011200, "[Kernel] virtio_blk virtio0: [vda] ${instance.storageGb * 2097152} 512-byte logical blocks (${instance.storageGb}.0 GiB)"),
            LogEntry(0.013400, "[Kernel] virtio_gpu virtio1: 2D display adapter attached (DRM/KMS driver loaded)"),
            LogEntry(0.016000, "[Kernel] virtio_input virtio2: touchscreen & multi-touch tablet registered"),
            LogEntry(0.018500, "[Kernel] virtio_net virtio3: virtual ethernet interface up (MAC: 52:54:00:12:34:56)"),
            LogEntry(0.024000, "[Kernel] EXT4-fs (vda): mounted filesystem with ordered data mode. Opts: (null)"),
            LogEntry(0.035000, "[Init] init: //system/bin/init running stage 1 for $os"),
            LogEntry(0.042000, "[Init] init: [libprocessgroup] Created cgroup /sys/fs/cgroup/system"),
            LogEntry(0.055000, "[Init] init: starting service 'servicemanager'..."),
            LogEntry(0.068000, "[Init] init: starting service 'surfaceflinger'..."),
            LogEntry(0.081000, "[SurfaceFlinger] SurfaceFlinger: using virtio-gpu DRM display (720x1280@60Hz)"),
            LogEntry(0.095000, "[AndroidRuntime] >>>>>> START com.android.internal.os.ZygoteInit uid 0 <<<<<<"),
            LogEntry(0.120000, "[SystemServer] Entered the Android system server! Boot completed successfully.", LogLevel.SUCCESS),
            LogEntry(0.140000, "[AxilBox-Virt] Phase 1 Interactive Preview Active — Virtual Display Ready.", LogLevel.SUCCESS)
        )
    }
}
