package com.example.util

object iOSResumeButtonHelper {

    /**
     * Formats milliseconds to HH:MM:SS or MM:SS (e.g. 645000ms -> "00:10:45")
     */
    fun formatTimestamp(ms: Long): String {
        if (ms <= 0) return "00:00:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "00:%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Updates the iOS Infuse Resume Button UI dynamically based on playback progress.
     */
    fun bindResumeButton(
        buttonContainer: android.view.ViewGroup,
        progressFillView: android.view.View,
        textView: android.widget.TextView,
        savedPositionMs: Long,
        totalDurationMs: Long,
        seasonNum: Int,
        episodeNum: Int
    ) {
        if (savedPositionMs <= 0L || totalDurationMs <= 0L) {
            // Unwatched / Starting State
            progressFillView.layoutParams = progressFillView.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
            buttonContainer.setBackgroundColor(android.graphics.Color.WHITE)
            textView.text = "Play S$seasonNum • E$episodeNum"
            textView.setTextColor(android.graphics.Color.BLACK)
        } else {
            // In-Progress Resume State
            val progressRatio = (savedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            
            buttonContainer.post {
                val totalWidth = buttonContainer.width
                val fillWidth = (totalWidth * progressRatio).toInt()
                
                val params = progressFillView.layoutParams
                params.width = fillWidth
                progressFillView.layoutParams = params
            }

            val timestampStr = formatTimestamp(savedPositionMs)
            textView.text = "Resume $timestampStr"
            textView.setTextColor(android.graphics.Color.WHITE)
        }
    }
}
