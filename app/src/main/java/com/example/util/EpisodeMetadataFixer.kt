package com.example.util

object EpisodeMetadataFixer {

    // 1. Get Proper Episode Title (Prevents show title from being displayed as episode name)
    fun getCleanEpisodeTitle(tmdbEpisodeName: String?, episodeNumber: Int, showTitle: String? = null, isFromTmdbApi: Boolean = false): String {
        val trimmed = tmdbEpisodeName?.trim() ?: ""
        if (trimmed.isBlank() || trimmed.equals("Ocean", ignoreCase = true)) {
            return "Episode $episodeNumber"
        }

        // If the title comes from raw filename fallback and not verified TMDB API, ensure it doesn't just duplicate the show name
        if (!isFromTmdbApi && !showTitle.isNullOrBlank()) {
            val normShow = showTitle.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), "").trim()
            val normEp = trimmed.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), "").trim()
            if (normShow.isNotBlank() && (normEp == normShow || normEp.startsWith("$normShow s") || normEp.startsWith("$normShow season"))) {
                return "Episode $episodeNumber"
            }
        }

        return trimmed
    }

    // 2. Format Subtitle String (e.g. "S1 • E1 - Glorious Purpose")
    fun formatEpisodeSubtitle(seasonNumber: Int, episodeNumber: Int, tmdbEpisodeName: String?, showTitle: String? = null, isFromTmdbApi: Boolean = false): String {
        val cleanName = getCleanEpisodeTitle(tmdbEpisodeName, episodeNumber, showTitle, isFromTmdbApi)
        return "S$seasonNumber • E$episodeNumber - $cleanName"
    }

    // 3. Filter/Clean Camera File Names (e.g., "20260802 160534" or "VID 2026...")
    fun sanitizeMediaTitle(rawTitle: String): String {
        val raw = rawTitle.trim()
        
        // Check if filename is just raw date numbers like "20260802 160534"
        val isNumericDate = raw.matches(Regex("^(VID[_-]?)?\\d{8}[_-]?\\d{6}.*"))
        if (isNumericDate) {
            return "Personal Recording"
        }

        // Clean common media extensions/separators
        return raw.replace(Regex("(?i)\\.(mkv|mp4|avi|mov)\$"), "")
            .replace("_", " ")
            .trim()
    }
}
