package com.axilbox.app.model

enum class OsType(
    val displayName: String,
    val defaultRamMb: Int,
    val defaultVCpus: Int,
    val defaultStorageGb: Int,
    val defaultCmdline: String
) {
    AOSP_ARM64(
        displayName = "AOSP ARM64 (Android)",
        defaultRamMb = 2048,
        defaultVCpus = 2,
        defaultStorageGb = 16,
        defaultCmdline = "console=ttyAMA0 earlycon=pl011,0x09000000 androidboot.hardware=virt androidboot.serialno=axil01"
    ),
    DEBIAN_ARM64(
        displayName = "Debian Linux ARM64",
        defaultRamMb = 1024,
        defaultVCpus = 2,
        defaultStorageGb = 8,
        defaultCmdline = "console=ttyAMA0 earlycon=pl011,0x09000000 root=/dev/vda rw init=/sbin/init"
    ),
    LINUX_GENERIC(
        displayName = "Generic Linux Kernel",
        defaultRamMb = 512,
        defaultVCpus = 1,
        defaultStorageGb = 4,
        defaultCmdline = "console=ttyAMA0 earlycon=pl011,0x09000000 panic=-1"
    ),
    CUSTOM_RAW(
        displayName = "Custom Raw Disk",
        defaultRamMb = 2048,
        defaultVCpus = 2,
        defaultStorageGb = 16,
        defaultCmdline = "console=ttyAMA0"
    )
}
