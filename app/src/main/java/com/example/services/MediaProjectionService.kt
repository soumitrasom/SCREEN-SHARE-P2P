package com.example.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.example.network.CastServer
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.Serializable

class MediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isStreaming = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopProjection()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. We MUST retrieve the MediaProjection token FIRST on Android 14+ (SDK 34+)
        // so that the system registers our project_media token grant before startForeground is called!
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Now start the foreground service (the system validates and sees we already hold the mediaProjection token)
        startForegroundServiceNotification()

        // 3. Initiate virtual display projection layout
        startProjection()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Projection Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val stopIntent = Intent(this, MediaProjectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Cast Flow Active")
            .setContentText("Your mobile screen is currently being mirrored wirelessly.")
            .setSmallIcon(android.R.drawable.presence_video_busy)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection() {
        val proj = mediaProjection ?: run {
            stopSelf()
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        val width = metrics.widthPixels / 2 // Downscale for bandwidth and lag reduction
        val height = metrics.heightPixels / 2
        val dpi = metrics.densityDpi

        // Instantiates safe image read buffer
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        try {
            virtualDisplay = proj.createVirtualDisplay(
                "ScreenMirrorDisplay",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            isStreaming = true
            startFrameCastingLoop()
            Log.d(TAG, "Screen projection stream initiated successfully at $width x $height")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual display: ${e.message}")
            stopSelf()
        }
    }

    private fun startFrameCastingLoop() {
        // High performance loop capturing system views of Virtual Display
        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isStreaming) return@setOnImageAvailableListener
            try {
                reader.acquireLatestImage()?.use { image ->
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * image.width

                    // Translate image pixels to low latency buffer bitmap
                    val bitmap = Bitmap.createBitmap(
                        image.width + rowPadding / pixelStride,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Feed this generated bitmap directly into the active socket sender
                    activeServerInstance?.let { server ->
                        // Execute off-thread dispatch
                        Handler(Looper.getMainLooper()).post {
                            try {
                                val handlerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                handlerScope.launch {
                                    server.sendFrame(bitmap)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in image processor loop: ${e.message}")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun stopProjection() {
        isStreaming = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "Media projection services clean halted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection()
    }

    companion object {
        private const val TAG = "ProjectionService"
        private const val CHANNEL_ID = "screencast_channel"
        private const val NOTIFICATION_ID = 23940

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "com.example.services.ACTION_STOP"

        @Volatile
        var activeServerInstance: CastServer? = null
    }
}
