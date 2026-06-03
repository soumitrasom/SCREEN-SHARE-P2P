package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.network.TouchCommand

class RemoteInputAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No custom actions required on standard events
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Remote Input Service connected and active.")
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        private const val TAG = "RemoteInputService"
        
        @Volatile
        private var instance: RemoteInputAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /**
         * Dispatches a touch event to the system using the actual screen resolution ratios.
         */
        fun injectGesture(command: TouchCommand, densityDpi: Int, screenWidth: Int, screenHeight: Int): Boolean {
            val service = instance ?: return false

            val x = command.xRatio * screenWidth
            val y = command.yRatio * screenHeight

            val path = Path().apply {
                moveTo(x, y)
            }

            val gestureBuilder = GestureDescription.Builder()
            
            val duration = when (command.action) {
                0 -> 80L // Tap down
                1 -> 50L // Lift up / Release
                else -> 40L // Drag increments
            }

            try {
                // Construct and execute standard stroke
                val stroke = GestureDescription.StrokeDescription(path, 0, duration)
                gestureBuilder.addStroke(stroke)
                service.dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture injected successfully at X: $x, Y: $y")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.d(TAG, "Gesture injection canceled.")
                    }
                }, null)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch gesture input: ${e.message}")
                return false
            }
        }
    }
}
