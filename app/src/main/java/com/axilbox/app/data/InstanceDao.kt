package com.axilbox.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.VirtualInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {

    @Query("SELECT * FROM virtual_instances ORDER BY created_at DESC")
    fun getAllInstancesFlow(): Flow<List<VirtualInstance>>

    @Query("SELECT * FROM virtual_instances ORDER BY created_at DESC")
    suspend fun getAllInstances(): List<VirtualInstance>

    @Query("SELECT * FROM virtual_instances WHERE id = :id")
    fun getInstanceByIdFlow(id: Long): Flow<VirtualInstance?>

    @Query("SELECT * FROM virtual_instances WHERE id = :id")
    suspend fun getInstanceById(id: Long): VirtualInstance?

    @Query("SELECT * FROM virtual_instances WHERE name = :name LIMIT 1")
    suspend fun getInstanceByName(name: String): VirtualInstance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstance(instance: VirtualInstance): Long

    @Update
    suspend fun updateInstance(instance: VirtualInstance)

    @Delete
    suspend fun deleteInstance(instance: VirtualInstance)

    @Query("DELETE FROM virtual_instances WHERE id = :id")
    suspend fun deleteInstanceById(id: Long)

    @Query("UPDATE virtual_instances SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: InstanceStatus)

    @Query("UPDATE virtual_instances SET last_booted_at = :timestamp, status = :status WHERE id = :id")
    suspend fun updateBootStatus(id: Long, status: InstanceStatus, timestamp: Long)

    @Query("SELECT COUNT(*) FROM virtual_instances")
    suspend fun getInstanceCount(): Int
}
