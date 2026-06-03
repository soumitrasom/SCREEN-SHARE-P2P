package com.example.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {
    val allConnections: Flow<List<DeviceConnection>> = sessionDao.getAllConnections()
    val allSessions: Flow<List<StreamSession>> = sessionDao.getAllSessions()

    suspend fun insertConnection(connection: DeviceConnection) {
        sessionDao.insertConnection(connection)
    }

    suspend fun deleteConnectionByIp(ip: String) {
        sessionDao.deleteConnectionByIp(ip)
    }

    suspend fun insertSession(session: StreamSession) {
        sessionDao.insertSession(session)
    }

    suspend fun clearHistory() {
        sessionDao.clearConnectionHistory()
        sessionDao.clearSessionLogs()
    }
}
