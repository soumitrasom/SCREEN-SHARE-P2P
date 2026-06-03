package com.example.network

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

class CastServer(
    private val port: Int = 18080,
    private val onTouchReceived: (TouchCommand) -> Unit
) {
    private val tag = "CastServer"

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null
    private val networkDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Stopped)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs

    private val _kbps = MutableStateFlow(0f)
    val kbps: StateFlow<Float> = _kbps

    sealed class ConnectionStatus {
        object Stopped : ConnectionStatus()
        object Listening : ConnectionStatus()
        class Connected(val clientIp: String) : ConnectionStatus()
        class Error(val message: String) : ConnectionStatus()
    }

    private var frameCount = 0
    private var bytesSentInSecond = 0L
    private var lastStatsTime = 0L
    private var isStreaming = false

    fun start() {
        if (serverJob?.isActive == true) return
        _connectionState.value = ConnectionStatus.Listening
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.d(tag, "Server started on port $port, listening...")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        Log.d(tag, "Client connected: ${socket.inetAddress.hostAddress}")
                        handleClient(socket)
                    } catch (e: Exception) {
                        if (e is SocketException) Log.d(tag, "Server socket closed.")
                        else Log.e(tag, "Error accepting client: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error: ${e.message}")
                _connectionState.value = ConnectionStatus.Error(e.message ?: "Server initiation error")
            }
        }
    }

    fun stop() {
        isStreaming = false
        serverJob?.cancel()
        serverJob = null
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        _connectionState.value = ConnectionStatus.Stopped
        _fps.value = 0
        _latencyMs.value = 0
        _kbps.value = 0f
    }

    private fun handleClient(socket: Socket) {
        clientSocket = socket
        val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
        _connectionState.value = ConnectionStatus.Connected(clientIp)
        isStreaming = true
        frameCount = 0
        bytesSentInSecond = 0L
        lastStatsTime = System.currentTimeMillis()

        // 1. Reader thread for remote controls & latencies
        CoroutineScope(networkDispatcher).launch {
            try {
                val inputStream = socket.getInputStream()
                val reader = inputStream.bufferedReader()
                while (isStreaming && socket.isConnected && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("TOUCH:")) {
                        TouchCommand.parse(line)?.let { command ->
                            withContext(Dispatchers.Main) {
                                onTouchReceived(command)
                            }
                        }
                    } else if (line.startsWith("PING:")) {
                        // Echo latency measurement
                        try {
                            val parts = line.split(":")
                            if (parts.size >= 2) {
                                val sendTime = parts[1].toLong()
                                val roundTrip = System.currentTimeMillis() - sendTime
                                _latencyMs.value = roundTrip.toInt().coerceAtLeast(1)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Reader error: ${e.message}")
            } finally {
                disconnectClient()
            }
        }
    }

    private val streamMutex = Mutex()
    private val mutex = Any()

    suspend fun sendFrame(bitmap: Bitmap) {
        val socket = clientSocket ?: return
        if (!isStreaming || socket.isClosed || !socket.isConnected) return

        withContext(networkDispatcher) {
            synchronized(mutex) {
                try {
                    val out = socket.getOutputStream()
                    val dos = DataOutputStream(out)

                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val frameBytes = baos.toByteArray()

                    // Frame header: Let's prepend with 'FRAME:' label, write the frame length, then payload
                    dos.writeBytes("FRAME\n")
                    dos.writeInt(frameBytes.size)
                    dos.write(frameBytes)
                    dos.flush()

                    // Track Statistics
                    val currentTime = System.currentTimeMillis()
                    frameCount++
                    bytesSentInSecond += (frameBytes.size + 10) // Approx header bytes

                    if (currentTime - lastStatsTime >= 1000) {
                        _fps.value = frameCount
                        _kbps.value = (bytesSentInSecond * 8f) / 1024f // Kilobits per second
                        frameCount = 0
                        bytesSentInSecond = 0L
                        lastStatsTime = currentTime
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error sending frame: ${e.message}")
                    disconnectClient()
                }
            }
        }
    }

    private fun disconnectClient() {
        isStreaming = false
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
        if (_connectionState.value is ConnectionStatus.Connected) {
            _connectionState.value = ConnectionStatus.Listening
        }
        _fps.value = 0
        _latencyMs.value = 0
        _kbps.value = 0f
    }
}
// Lightweight mock Mutex to avoid additional kotlinx-coroutines synchronization components imports
class Mutex {
    fun lock() {}
    fun unlock() {}
}
