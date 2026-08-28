package com.axilbox.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_instances")
data class VirtualInstance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "os_type")
    val osType: OsType,

    @ColumnInfo(name = "vcpu_count")
    val vCpuCount: Int = 2,

    @ColumnInfo(name = "ram_mb")
    val ramMb: Int = 2048,

    @ColumnInfo(name = "storage_gb")
    val storageGb: Int = 16,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,

    @ColumnInfo(name = "kernel_uri")
    val kernelUri: String? = null,

    @ColumnInfo(name = "initrd_uri")
    val initrdUri: String? = null,

    @ColumnInfo(name = "extra_cmdline")
    val extraCmdline: String = "",

    @ColumnInfo(name = "display_orientation")
    val displayOrientation: String = "PORTRAIT",

    @ColumnInfo(name = "serial_console_logging")
    val serialConsoleLogging: Boolean = true,

    @ColumnInfo(name = "status")
    val status: InstanceStatus = InstanceStatus.STOPPED,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_booted_at")
    val lastBootedAt: Long? = null
)
