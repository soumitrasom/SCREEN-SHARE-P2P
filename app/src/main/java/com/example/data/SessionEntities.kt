package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "device_connections")
data class DeviceConnection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ipAddress: String,
    val deviceName: String,
    val connectionType: String, // "WIFI" or "BLUETOOTH"
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "stream_sessions")
data class StreamSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val durationSeconds: Int,
    val averageFps: Float,
    val averageLatencyMs: Int,
    val maxRefreshRate: Int,
    val bytesTransferred: Long,
    val connectionType: String, // "WIFI" or "BLUETOOTH"
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
