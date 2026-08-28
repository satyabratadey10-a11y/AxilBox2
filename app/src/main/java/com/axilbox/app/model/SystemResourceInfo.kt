package com.axilbox.app.model

data class SystemResourceInfo(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalInternalStorageGb: Long,
    val freeInternalStorageGb: Long,
    val maxSafeGuestRamMb: Long
) {
    val usedRamMb: Long get() = totalRamMb - availableRamMb
    val ramUsagePercent: Float get() = if (totalRamMb > 0) (usedRamMb.toFloat() / totalRamMb.toFloat()) else 0f
}
