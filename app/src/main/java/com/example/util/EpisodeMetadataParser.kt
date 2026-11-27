package com.example.util

object EpisodeMetadataParser {

    private val qualityCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // 1. Extract Real Resolution Quality from local media file
    fun getLocalVideoQuality(filePath: String?, context: android.content.Context? = null): String {
        if (filePath.isNullOrEmpty()) return ""
        qualityCache[filePath]?.let { return it }

        // Attempt 1: Extract real stream width/height directly from media file or content URI
        try {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                if (filePath.startsWith("content://") && context != null) {
                    retriever.setDataSource(context, android.net.Uri.parse(filePath))
                } else if (filePath.startsWith("file://")) {
                    retriever.setDataSource(android.net.Uri.parse(filePath).path)
                } else {
                    retriever.setDataSource(filePath)
                }
                val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val height = heightStr?.toIntOrNull() ?: 0
                val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val width = widthStr?.toIntOrNull() ?: 0

                val maxDim = maxOf(height, width)
                val minDim = minOf(height, width)

                val resolved = when {
                    maxDim >= 3840 || minDim >= 2160 -> "2160p"
                    maxDim >= 2560 || minDim >= 1440 -> "1440p"
                    maxDim >= 1920 || minDim >= 1080 -> "1080p"
                    maxDim >= 1280 || minDim >= 720 -> "720p"
                    maxDim >= 854 || minDim >= 480 -> "480p"
                    minDim > 0 -> "${minDim}p"
                    else -> ""
                }
                if (resolved.isNotBlank()) {
                    qualityCache[filePath] = resolved
                    return resolved
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Fallback: Infer quality badge from filename if retriever could not probe stream
        val badgeFromName = com.example.network.GeminiService.detectQualityBadge(filePath)
        if (badgeFromName.isNotBlank()) {
            qualityCache[filePath] = badgeFromName
            return badgeFromName
        }
        qualityCache[filePath] = ""
        return ""
    }

    // 2. Format Air Date (e.g., "2022-03-13" -> "13 Mar 2022")
    fun formatAirDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return ""
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val outputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
            val date = inputFormat.parse(rawDate)
            date?.let { outputFormat.format(it) } ?: rawDate
        } catch (e: Exception) {
            rawDate
        }
    }

    // 3. Format Runtime (e.g. 22 -> "22 min", 60 -> "1 hr", 177 -> "2 hr 57 min")
    fun formatRuntime(minutes: Int?): String {
        if (minutes == null || minutes <= 0) return ""
        val hours = minutes / 60
        val remainingMins = minutes % 60
        return when {
            hours == 0 -> "${remainingMins} min"
            remainingMins == 0 -> "${hours} hr"
            else -> "${hours} hr ${remainingMins} min"
        }
    }

    // 4. Format Genres list to comma-separated string
    fun formatGenres(genres: List<String>?): String {
        if (genres.isNullOrEmpty()) return ""
        return genres.joinToString(", ")
    }
}
