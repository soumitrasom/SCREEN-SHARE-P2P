package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM device_connections ORDER BY timestamp DESC LIMIT 30")
    fun getAllConnections(): Flow<List<DeviceConnection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: DeviceConnection)

    @Query("DELETE FROM device_connections WHERE ipAddress = :ip")
    suspend fun deleteConnectionByIp(ip: String)

    @Query("SELECT * FROM stream_sessions ORDER BY timestamp DESC LIMIT 50")
    fun getAllSessions(): Flow<List<StreamSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StreamSession)

    @Query("DELETE FROM device_connections")
    suspend fun clearConnectionHistory()

    @Query("DELETE FROM stream_sessions")
    suspend fun clearSessionLogs()
}
