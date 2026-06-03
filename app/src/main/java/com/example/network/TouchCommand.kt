package com.example.network

data class TouchCommand(
    val action: Int, // 0: DOWN, 1: UP, 2: MOVE
    val xRatio: Float, // Relative X coordinate (0.0 to 1.0)
    val yRatio: Float, // Relative Y coordinate (0.0 to 1.0)
    val pointerId: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPayload(): String {
        return "TOUCH:$action:$xRatio:$yRatio:$pointerId\n"
    }

    companion object {
        fun parse(payload: String): TouchCommand? {
            if (!payload.startsWith("TOUCH:")) return null
            return try {
                val parts = payload.trim().split(":")
                if (parts.size >= 5) {
                    TouchCommand(
                        action = parts[1].toInt(),
                        xRatio = parts[2].toFloat(),
                        yRatio = parts[3].toFloat(),
                        pointerId = parts[4].toInt()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
