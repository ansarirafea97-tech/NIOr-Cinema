package com.example.util

import com.example.model.Movie

data class SeasonEpisodeInfo(
    val season: Int?,
    val episode: Int?
)

object EpisodeUtils {

    /**
     * Extracts seasonNumber and episodeNumber from a file name or movie title using regex.
     * Matches formats:
     * - S01E05, S1E2, s01.e05, s1_e2
     * - 01x05, 1x2
     * - Season 1 Episode 5, Season 01 Ep 05
     */
    fun extractSeasonAndEpisode(title: String, filePath: String = ""): SeasonEpisodeInfo {
        val matchSE = Regex("""(?i)\b[sS](\d{1,2})[\s._-]*[eE](\d{1,2})\b""").find(title)
            ?: Regex("""(?i)\b(\d{1,2})x(\d{1,2})\b""").find(title)
            ?: Regex("""(?i)\bseason\s*(\d{1,2})[\s._-]*episode\s*(\d{1,2})\b""").find(title)

        if (matchSE != null) {
            val s = matchSE.groupValues[1].toIntOrNull()
            val e = matchSE.groupValues[2].toIntOrNull()
            return SeasonEpisodeInfo(s, e)
        }

        val matchS = Regex("""(?i)\b[sS](\d{1,2})\b""").find(title)
            ?: Regex("""(?i)\bseason\s*(\d{1,2})\b""").find(title)
        var s = matchS?.groupValues?.get(1)?.toIntOrNull()

        if (s == null) {
            s = LocalSeasonParser.parseSeasonNumberOrNull(filePath, title)
        }

        val matchE = Regex("""(?i)\b[eE](\d{1,2})\b""").find(title)
            ?: Regex("""(?i)\bep(?:isode)?\s*(\d{1,2})\b""").find(title)
        val e = matchE?.groupValues?.get(1)?.toIntOrNull()

        return SeasonEpisodeInfo(s, e)
    }

    /**
     * Sorts episodes chronologically:
     * 1. If season numbers differ: sort Season 1 before Season 2 (a.season - b.season)
     * 2. If season numbers match: sort Episode 1 before Episode 2 (a.episode - b.episode)
     * 3. Fallback: If no SxxExx pattern is detected in the filename, sort the files naturally by name in ascending order.
     */
    fun sortEpisodesChronologically(episodes: List<Movie>): List<Movie> {
        return episodes.sortedWith { a, b ->
            val seA = extractSeasonAndEpisode(a.title, a.filePath ?: "")
            val seB = extractSeasonAndEpisode(b.title, b.filePath ?: "")

            val hasA = seA.season != null || seA.episode != null
            val hasB = seB.season != null || seB.episode != null

            if (hasA && hasB) {
                val seasonA = seA.season ?: 1
                val seasonB = seB.season ?: 1
                if (seasonA != seasonB) {
                    return@sortedWith seasonA.compareTo(seasonB)
                }

                val epA = seA.episode ?: 0
                val epB = seB.episode ?: 0
                if (epA != epB) {
                    return@sortedWith epA.compareTo(epB)
                }
            } else if (hasA) {
                return@sortedWith -1
            } else if (hasB) {
                return@sortedWith 1
            }

            // Natural fallback sorting by title in ascending order
            a.title.compareTo(b.title, ignoreCase = true)
        }
    }

    /**
     * Parses clean episode title from title/filename by stripping quality, codec, and release tags.
     */
    fun cleanEpisodeTitle(title: String): String {
        var clean = title
            .substringBeforeLast(".")
            .replace(Regex("""(?i)\b[sS]\d{1,2}[\s._-]*[eE]\d{1,2}\b"""), "")
            .replace(Regex("""(?i)\b\d{1,2}x\d{1,2}\b"""), "")
            .replace(Regex("""(?i)\bseason\s*\d+\b"""), "")
            .replace(Regex("""(?i)\bepisode\s*\d+\b"""), "")
            .replace(Regex("""(?i)\b(1080p|720p|480p|2160p|4k|x264|x265|hevc|h264|h265|web-?dl|webrip|bluray|hdtv|aac|dts|remux|mkv|mp4|avi|flv|wmv|nf|dsnp|amzn|atvp)\b"""), "")
            .replace(Regex("""(?i)-?\b(moviezflix|netflix|hulu|disney|prime|hbo)\b"""), "")
            .replace(Regex("""[._\-]+"""), " ")
            .trim()

        clean = clean.replace(Regex("""^[\d\s.\-]+"""), "").trim()
        return clean
    }

    /**
     * Normalizes a show title to support robust grouping/matching across variations (e.g. "Daredevil" vs "Marvel's Daredevil").
     */
    fun getNormalizedShowTitle(title: String): String {
        val cleaned = com.example.network.GeminiService.cleanShowTitle(title)
        val cleanApostrophes = cleaned.lowercase().replace("'", "").replace("’", "")
        var normalized = cleanApostrophes
            .replace(Regex("""^(marvels|marvel|dcs|dc|the|a|an)\b"""), "")
            .trim()
        
        normalized = normalized.replace(Regex("""[^a-z0-9]"""), "")
        return if (normalized.isBlank()) cleaned.lowercase().replace(Regex("""[^a-z0-9]"""), "") else normalized
    }
}
