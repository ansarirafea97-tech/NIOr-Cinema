package com.example.util

import com.example.model.Movie

object HomeVideoFilter {

    // Regex patterns for raw camera/recording filenames
    private val CAMERA_FILE_REGEX = Regex("(?i)^(VID|IMG|MOV|PXL|REC|AUD)[_-]?\\d{6,14}.*|^(19|20)\\d{6}[_-]?\\d{6}.*")
    
    // Folder paths that belong to personal media
    private val EXCLUDED_PATH_KEYWORDS = listOf(
        "/dcim/", 
        "/camera/", 
        "/whatsapp/", 
        "/pictures/", 
        "/snapchat/", 
        "/screenrecord/", 
        "/screen_recorded/"
    )

    // Check if a file is a personal/home video
    fun isHomeVideo(filePath: String?, fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val name = fileName.trim()

        // 1. Check Regex for raw timestamp names (e.g. "20260802 160534", "VID_20260802_160534")
        if (CAMERA_FILE_REGEX.containsMatchIn(name)) {
            return true
        }

        // 2. Check if file is stored in Camera/WhatsApp/DCIM directory
        if (!filePath.isNullOrBlank()) {
            val lowerPath = filePath.lowercase()
            if (EXCLUDED_PATH_KEYWORDS.any { lowerPath.contains(it) }) {
                return true
            }
        }

        return false
    }

    // Helper extension function to filter lists
    fun <T> filterHomeContent(items: List<T>, getPath: (T) -> String?, getName: (T) -> String?): List<T> {
        return items.filter { item ->
            !isHomeVideo(getPath(item), getName(item))
        }
    }
}

val Movie.filePath: String?
    get() = if (category.equals("Home Videos", ignoreCase = true) || 
                title.matches(Regex("(?i)^(VID[_-]?)?\\d{8}[_\\s-]?\\d{6}.*")) ||
                title.startsWith("VID_", ignoreCase = true) ||
                synopsis.lowercase().contains("/dcim/") ||
                synopsis.lowercase().contains("/camera/") ||
                synopsis.lowercase().contains("/whatsapp/")) {
        "/dcim/personal_video.mp4"
    } else if (synopsis.startsWith("/")) {
        synopsis
    } else {
        id
    }

val Movie.fileName: String?
    get() = title

