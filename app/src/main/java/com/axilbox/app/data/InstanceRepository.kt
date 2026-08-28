package com.axilbox.app.data

import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.VirtualInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface InstanceRepository {
    fun getAllInstances(): Flow<List<VirtualInstance>>
    fun getInstanceByIdFlow(id: Long): Flow<VirtualInstance?>
    suspend fun getInstanceById(id: Long): VirtualInstance?
    suspend fun getInstanceByName(name: String): VirtualInstance?
    suspend fun insertInstance(instance: VirtualInstance): Long
    suspend fun updateInstance(instance: VirtualInstance)
    suspend fun deleteInstance(instance: VirtualInstance)
    suspend fun deleteInstanceById(id: Long)
    suspend fun updateStatus(id: Long, status: InstanceStatus)
    suspend fun markBooted(id: Long)
}

class InstanceRepositoryImpl(
    private val dao: InstanceDao
) : InstanceRepository {

    override fun getAllInstances(): Flow<List<VirtualInstance>> {
        return dao.getAllInstancesFlow().flowOn(Dispatchers.IO)
    }

    override fun getInstanceByIdFlow(id: Long): Flow<VirtualInstance?> {
        return dao.getInstanceByIdFlow(id).flowOn(Dispatchers.IO)
    }

    override suspend fun getInstanceById(id: Long): VirtualInstance? {
        return withContext(Dispatchers.IO) {
            dao.getInstanceById(id)
        }
    }

    override suspend fun getInstanceByName(name: String): VirtualInstance? {
        return withContext(Dispatchers.IO) {
            dao.getInstanceByName(name)
        }
    }

    override suspend fun insertInstance(instance: VirtualInstance): Long {
        return withContext(Dispatchers.IO) {
            dao.insertInstance(instance)
        }
    }

    override suspend fun updateInstance(instance: VirtualInstance) {
        withContext(Dispatchers.IO) {
            dao.updateInstance(instance)
        }
    }

    override suspend fun deleteInstance(instance: VirtualInstance) {
        withContext(Dispatchers.IO) {
            dao.deleteInstance(instance)
        }
    }

    override suspend fun deleteInstanceById(id: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteInstanceById(id)
        }
    }

    override suspend fun updateStatus(id: Long, status: InstanceStatus) {
        withContext(Dispatchers.IO) {
            dao.updateStatus(id, status)
        }
    }

    override suspend fun markBooted(id: Long) {
        withContext(Dispatchers.IO) {
            dao.updateBootStatus(id, InstanceStatus.RUNNING, System.currentTimeMillis())
        }
    }
}
