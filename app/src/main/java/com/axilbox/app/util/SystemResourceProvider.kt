package com.axilbox.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.axilbox.app.model.SystemResourceInfo
import kotlin.math.min

class SystemResourceProvider(private val context: Context) {

    fun getSystemResourceInfo(): SystemResourceInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        var totalStorageGb: Long = 64
        var freeStorageGb: Long = 32
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
        } catch (e: Exception) {
            // fallback defaults
        }

        // Safe guest memory calculation: cap at 3072 MB or 60% of total host RAM
        val maxSafeGuestRamMb = min(3072L, (totalRamMb * 0.50).toLong().coerceAtLeast(1024L))

        return SystemResourceInfo(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            totalInternalStorageGb = totalStorageGb,
            freeInternalStorageGb = freeStorageGb,
            maxSafeGuestRamMb = maxSafeGuestRamMb
        )
    }
}
