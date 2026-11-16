package com.memscope.taskmonitor.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que guarda el resumen acumulado de consumo de RAM y picos históricos
 * de cada paquete mientras ha estado en ejecución.
 */
@Entity(tableName = "memory_history")
data class MemoryHistoryEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val peakPssKb: Long,
    val peakRssHwmKb: Long,
    val averagePssKb: Long,
    val lastMeasuredPssKb: Long,
    val sampleCount: Long,
    val activeSecondsAccumulated: Long,
    val peakTimestampMillis: Long,
    val lastSeenMillis: Long
)

/**
 * Entidad Room que almacena muestras puntuales de memoria en una serie temporal
 * para dibujar la curva de consumo mientras la app corría.
 */
@Entity(tableName = "memory_samples")
data class MemorySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val pssKb: Long,
    val rssKb: Long,
    val hwmKb: Long,
    val timestampMillis: Long
)
