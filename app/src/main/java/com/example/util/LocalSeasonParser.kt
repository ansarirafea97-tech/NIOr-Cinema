package com.example.util

object LocalSeasonParser {

    // Regex patterns for Season Detection
    private val SEASON_S_PATTERN = Regex("(?i)\\bS(\\d{1,2})E\\d{1,2}\\b")      // S02E01, S2E1
    private val SEASON_WORD_PATTERN = Regex("(?i)\\bSeason[._\\s]?(\\d{1,2})\\b") // Season 2, Season.02
    private val SEASON_X_PATTERN = Regex("(?i)\\b(\\d{1,2})x\\d{1,2}\\b")        // 2x01, 02x05

    fun parseSeasonNumberOrNull(filePath: String, fileName: String): Int? {
        // 1. Check Filename for S02 / Season 2 / 2x01
        SEASON_S_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON_WORD_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON_X_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        // 2. Check Parent Folder Path if filename lacks season info
        SEASON_WORD_PATTERN.find(filePath)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON_S_PATTERN.find(filePath)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        return null
    }

    fun parseSeasonNumber(filePath: String, fileName: String): Int {
        return parseSeasonNumberOrNull(filePath, fileName) ?: 1
    }
}
