package com.example.util

object InfuseUIHelper {

    // 1. Format raw date string to "13 Mar 2022"
    fun formatDate(rawDate: String?): String {
        if (rawDate.isNullOrEmpty()) return ""
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
            val outputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
            val date = inputFormat.parse(rawDate)
            if (date != null) outputFormat.format(date) else rawDate
        } catch (e: Exception) {
            rawDate ?: ""
        }
    }

    // 2. Format position in MS to "HH:MM:SS" (e.g., "00:10:45")
    fun formatPlaybackTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.ENGLISH, "%02d:%02d:%02d", 0, minutes, seconds)
        }
    }

    // 3. Calculate playback percentage (0 to 100)
    fun calculateProgressPercentage(currentPositionMs: Long, totalDurationMs: Long): Int {
        if (totalDurationMs <= 0) return 0
        return ((currentPositionMs.toDouble() / totalDurationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}
