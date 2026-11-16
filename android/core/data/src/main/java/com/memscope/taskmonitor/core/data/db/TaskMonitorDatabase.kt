package com.memscope.taskmonitor.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room del monitor de tareas para almacenar picos de RAM y series temporales.
 */
@Database(
    entities = [MemoryHistoryEntity::class, MemorySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaskMonitorDatabase : RoomDatabase() {
    abstract fun memoryHistoryDao(): MemoryHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: TaskMonitorDatabase? = null

        fun getInstance(context: Context): TaskMonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskMonitorDatabase::class.java,
                    "memscope_monitor.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
