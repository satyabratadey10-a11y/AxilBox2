package com.axilbox.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.axilbox.app.model.InstanceStatus
import com.axilbox.app.model.OsType
import com.axilbox.app.model.VirtualInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromOsType(value: OsType): String = value.name

    @TypeConverter
    fun toOsType(value: String): OsType = try {
        OsType.valueOf(value)
    } catch (e: Exception) {
        OsType.CUSTOM_RAW
    }

    @TypeConverter
    fun fromInstanceStatus(value: InstanceStatus): String = value.name

    @TypeConverter
    fun toInstanceStatus(value: String): InstanceStatus = try {
        InstanceStatus.valueOf(value)
    } catch (e: Exception) {
        InstanceStatus.STOPPED
    }
}

@Database(
    entities = [VirtualInstance::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AxilBoxDatabase : RoomDatabase() {

    abstract fun instanceDao(): InstanceDao

    companion object {
        @Volatile
        private var INSTANCE: AxilBoxDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AxilBoxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AxilBoxDatabase::class.java,
                    "axilbox_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        populateInitialTemplates(database.instanceDao())
                    }
                }
            }

            suspend fun populateInitialTemplates(dao: InstanceDao) {
                if (dao.getInstanceCount() == 0) {
                    val defaultAosp = VirtualInstance(
                        name = "AOSP-14-ARM64-Dev",
                        osType = OsType.AOSP_ARM64,
                        vCpuCount = 2,
                        ramMb = 2048,
                        storageGb = 16,
                        extraCmdline = OsType.AOSP_ARM64.defaultCmdline,
                        displayOrientation = "PORTRAIT",
                        serialConsoleLogging = true,
                        status = InstanceStatus.STOPPED
                    )
                    val defaultDebian = VirtualInstance(
                        name = "Debian-ARM64-Sandbox",
                        osType = OsType.DEBIAN_ARM64,
                        vCpuCount = 2,
                        ramMb = 1024,
                        storageGb = 8,
                        extraCmdline = OsType.DEBIAN_ARM64.defaultCmdline,
                        displayOrientation = "LANDSCAPE",
                        serialConsoleLogging = true,
                        status = InstanceStatus.STOPPED
                    )
                    dao.insertInstance(defaultAosp)
                    dao.insertInstance(defaultDebian)
                }
            }
        }
    }
}
