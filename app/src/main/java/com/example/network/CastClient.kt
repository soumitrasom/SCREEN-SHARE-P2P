package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class CastClient(
    private val host: String,
    private val port: Int = 18080,
    private val onFrameReceived: (Bitmap) -> Unit
) {
    private val tag = "CastClient"

    private var socket: Socket? = null
    private var clientJob: Job? = null
    private var pingJob: Job? = null
    private val networkDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _kbps = MutableStateFlow(0f)
    val kbps: StateFlow<Float> = _kbps

    sealed class ConnectionStatus {
        object Disconnected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        object Connected : ConnectionStatus()
        class Error(val message: String) : ConnectionStatus()
    }

    private var frameCount = 0
    private var bytesReceivedInSecond = 0L
    private var lastStatsTime = 0L
    private var isRunning = false

    fun connect() {
        if (clientJob?.isActive == true) return
        _connectionState.value = ConnectionStatus.Connecting
        isRunning = true

        clientJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val newSocket = Socket()
                socket = newSocket
                newSocket.connect(InetSocketAddress(host, port), 5000)
                _connectionState.value = ConnectionStatus.Connected
                Log.d(tag, "Successfully connected to $host:$port")

                val inputStream = newSocket.getInputStream()
                val dis = DataInputStream(inputStream)

                frameCount = 0
                bytesReceivedInSecond = 0L
                lastStatsTime = System.currentTimeMillis()

                // Start background ping loop for roundtrip latency calculation
                startPingLoop()

                while (isRunning && !newSocket.isClosed && newSocket.isConnected) {
                    val line = dis.readLine() ?: break
                    if (line == "FRAME") {
                        val size = dis.readInt()
                        if (size <= 0 || size > 25 * 1024 * 1024) continue // Reject corrupt sizes
                        
                        val buffer = ByteArray(size)
                        dis.readFully(buffer) // Ensures complete package read of target buffer size!

                        bytesReceivedInSecond += (size + 15) // Size of payload + overhead

                        val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                onFrameReceived(bitmap)
                            }
                            frameCount++
                        }

                        // Calculate stats
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastStatsTime >= 1000) {
                            _fps.value = frameCount
                            _kbps.value = (bytesReceivedInSecond * 8f) / 1024f
                            frameCount = 0
                            bytesReceivedInSecond = 0L
                            lastStatsTime = currentTime
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Client connection error: ${e.message}")
                if (isRunning) {
                    _connectionState.value = ConnectionStatus.Error(e.message ?: "Connection lost")
                }
            } finally {
                disconnect()
            }
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = CoroutineScope(networkDispatcher).launch {
            val sock = socket
            while (isRunning && sock != null && !sock.isClosed && sock.isConnected) {
                try {
                    val out = sock.getOutputStream()
                    val payload = "PING:${System.currentTimeMillis()}\n"
                    out.write(payload.toByteArray())
                    out.flush()
                } catch (e: Exception) {
                    Log.e(tag, "Ping transmit error: ${e.message}")
                    break
                }
                delay(500) // Send ping query twice every second
            }
        }
    }

    fun sendTouchCommand(command: TouchCommand) {
        val sock = socket ?: return
        if (!isRunning || sock.isClosed || !sock.isConnected) return

        CoroutineScope(networkDispatcher).launch {
            try {
                val out = sock.getOutputStream()
                out.write(command.toPayload().toByteArray())
                out.flush()
            } catch (e: Exception) {
                Log.e(tag, "Error sending remote touch: ${e.message}")
            }
        }
    }

    fun disconnect() {
        isRunning = false
        pingJob?.cancel()
        pingJob = null
        clientJob?.cancel()
        clientJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        _connectionState.value = ConnectionStatus.Disconnected
        _fps.value = 0
        _kbps.value = 0f
    }
}
