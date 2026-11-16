package com.memscope.taskmonitor.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de Room para consultar, actualizar y limpiar el historial de picos y muestras de RAM.
 * Todas las consultas de escritura son suspend para ejecutarse fuera del hilo principal.
 */
@Dao
interface MemoryHistoryDao {

    @Query("SELECT * FROM memory_history ORDER BY peakPssKb DESC")
    fun observeAllHistory(): Flow<List<MemoryHistoryEntity>>

    @Query("SELECT * FROM memory_history")
    suspend fun getAllHistoryOnce(): List<MemoryHistoryEntity>

    @Query("SELECT * FROM memory_history WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): MemoryHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(items: List<MemoryHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSamples(samples: List<MemorySampleEntity>)

    @Query(
        "SELECT * FROM memory_samples WHERE packageName = :packageName " +
            "ORDER BY timestampMillis DESC LIMIT :limit"
    )
    fun observeSamplesForPackage(packageName: String, limit: Int = 60): Flow<List<MemorySampleEntity>>

    @Query("DELETE FROM memory_samples WHERE timestampMillis < :olderThanMillis")
    suspend fun pruneSamplesOlderThan(olderThanMillis: Long)

    @Query("DELETE FROM memory_history")
    suspend fun clearHistory()

    @Query("DELETE FROM memory_samples")
    suspend fun clearSamples()
}
