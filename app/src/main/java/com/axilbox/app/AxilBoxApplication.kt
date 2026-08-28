package com.axilbox.app

import android.app.Application
import com.axilbox.app.data.AxilBoxDatabase
import com.axilbox.app.data.InstanceRepository
import com.axilbox.app.data.InstanceRepositoryImpl
import com.axilbox.app.util.SystemResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AxilBoxApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy {
        AxilBoxDatabase.getDatabase(this, applicationScope)
    }

    val repository: InstanceRepository by lazy {
        InstanceRepositoryImpl(database.instanceDao())
    }

    val systemResourceProvider by lazy {
        SystemResourceProvider(this)
    }
}
