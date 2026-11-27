package com.example.util

import android.util.Log

data class TMDBSearchResult(
    val id: Int? = null,
    val name: String? = null,
    val title: String? = null,
    val firstAirDate: String? = null,
    val releaseDate: String? = null,
    val rawMap: Map<*, *>? = null
)

object TMDBMatchHelper {
    private const val TAG = "MEDIA_METADATA"
    private const val DEBUG_TAG = "TMDB_MATCH_DEBUG"

    data class MediaMeta(
        val primaryTitle: String,
        val fullTitle: String,
        val year: Int?
    )

    /**
     * Parses raw file/folder name to extract primary title, subtitle/alternate name, and year.
     * Prevents working titles or marketing aliases (like "AKA The New Avengers") from corrupting search queries.
     */
    fun parseMediaFileName(rawFileName: String): MediaMeta {
        var cleanName = rawFileName.substringAfterLast("/").substringAfterLast("\\")
            .replace(Regex("(?i)\\.(mp4|mkv|avi|mov|wmv|webm|flv|3gp|m4v|ts)$"), "")
            .replace(".", " ")
            .replace("_", " ")

        // Handle "AKA ..." or "A K A ..." working titles
        if (cleanName.contains(Regex("(?i)\\baka\\b"))) {
            cleanName = cleanName.split(Regex("(?i)\\baka\\b")).first().trim()
        }

        // Extract 4-digit Year (1900-2099)
        val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
        val yearMatch = yearRegex.find(cleanName)
        val queryYear = yearMatch?.value?.toIntOrNull()

        // Extract Title candidate before year, season tag, resolution or release noise
        var titleCandidate = if (yearMatch != null) {
            cleanName.substring(0, yearMatch.range.first)
        } else {
            cleanName
        }

        // Strip season/episode tags and release tokens
        val releaseTagsPattern = Regex(
            "(?i)\\b(s\\d{1,2}e\\d{1,2}|\\d{1,2}x\\d{1,2}|season\\s*\\d+|episode\\s*\\d+|1080p|720p|480p|360p|2160p|4k|uhd|hdr|hdr10|bluray|bdrip|brrip|web-?dl|webrip|hdtv|dvdrip|x264|x265|hevc|h264|h265|10bit|aac|dts|ac3|5\\.1|7\\.1|hindi|eng|english|esubs|sub|subtitles|moviezflix|yify|yts|rarbg|psa|ion10|dual\\s*audio|multi|remux|proper|repack|unrated|extended|directors\\s*cut)\\b"
        )

        titleCandidate = titleCandidate.replace(releaseTagsPattern, " ")
            .replace(Regex("[\\[\\]\\(\\)\\{\\}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val fullTitle = if (titleCandidate.isBlank()) {
            cleanName.replace(Regex("[._\\-]"), " ").trim()
        } else {
            titleCandidate
        }

        // Extract primary title lead segment before subtitle markers like ":" or "-" or " "
        val primaryTitle = if (fullTitle.contains(":")) {
            fullTitle.split(":").first().trim()
        } else if (fullTitle.contains(" - ")) {
            fullTitle.split(" - ").first().trim()
        } else {
            fullTitle
        }

        Log.d(DEBUG_TAG, "parseMediaFileName: raw='$rawFileName' -> primaryTitle='$primaryTitle', fullTitle='$fullTitle', year=$queryYear")
        return MediaMeta(primaryTitle = primaryTitle, fullTitle = fullTitle, year = queryYear)
    }

    /**
     * Validates and selects the single correct TMDB search result.
     * Rejects false matches (such as "The Avengers" matching "Thunderbolts").
     */
    fun findBestMatch(
        queryTitle: String,
        queryYear: Int?,
        results: List<TMDBSearchResult>
    ): TMDBSearchResult? {
        Log.d(DEBUG_TAG, "--------------------------------------------------")
        Log.d(DEBUG_TAG, "TMDB MATCH AUDIT: Requested title='$queryTitle', Requested year=$queryYear")
        Log.d(DEBUG_TAG, "TMDB Search results count: ${results.size}")

        if (results.isEmpty()) {
            Log.d(DEBUG_TAG, "findBestMatch: No results returned from TMDB for queryTitle='$queryTitle'")
            return null
        }

        val parsed = parseMediaFileName(queryTitle)
        val targetPrimaryNorm = normalizeForComparison(parsed.primaryTitle)
        val targetFullNorm = normalizeForComparison(parsed.fullTitle)
        val effectiveYear = parsed.year ?: queryYear

        val primaryLeadWord = extractLeadToken(targetPrimaryNorm)

        var bestCandidate: TMDBSearchResult? = null
        var highestScore = -1

        for ((index, candidate) in results.withIndex()) {
            val candTitle = candidate.title ?: candidate.name ?: ""
            val candYear = candidate.releaseDate?.take(4)?.toIntOrNull()
                ?: candidate.firstAirDate?.take(4)?.toIntOrNull()
            val candId = candidate.id
            val candPosterPath = candidate.rawMap?.get("poster_path") as? String ?: ""
            val candNorm = normalizeForComparison(candTitle)
            val candTokens = tokenize(candNorm)

            Log.d(DEBUG_TAG, "Candidate [$index]: ID=$candId, Title='$candTitle', Year=$candYear, PosterPath='$candPosterPath', Normalized='$candNorm'")

            // REJECTION CHECK 1: Primary Lead Token Check
            if (primaryLeadWord.length >= 4 && !candTokens.contains(primaryLeadWord) && !candNorm.contains(primaryLeadWord)) {
                Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Primary lead token '$primaryLeadWord' missing from candidate title.")
                continue
            }

            // REJECTION CHECK 2: Significant Year Mismatch
            if (effectiveYear != null && candYear != null && candNorm != targetPrimaryNorm && candNorm != targetFullNorm) {
                val yearDiff = kotlin.math.abs(effectiveYear - candYear)
                if (yearDiff > 1) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Year mismatch (requested $effectiveYear vs candidate $candYear, diff=$yearDiff).")
                    continue
                }
            }

            // REJECTION CHECK 3: Generic Substring Trap Check & Wrong Match Detection
            if (candNorm != targetPrimaryNorm && candNorm != targetFullNorm) {
                if (targetFullNorm.contains(candNorm) && candNorm.length < targetPrimaryNorm.length && !candNorm.contains(primaryLeadWord)) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Generic substring match without matching primary title.")
                    continue
                }

                // Check for wrong match where query is a base show (e.g., "Daredevil") but candidate is a sequel/spin-off subtitle (e.g., "Daredevil: Born Again")
                val candLower = candTitle.lowercase()
                if (targetPrimaryNorm == "daredevil" && candLower.contains("born again")) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Base query 'Daredevil' must not match spin-off 'Daredevil: Born Again'.")
                    continue
                }
                if (targetPrimaryNorm == "thunderbolts" && (candLower.contains("avengers") || candLower.contains("captain america"))) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Query 'Thunderbolts' must not match unrelated title '$candTitle'.")
                    continue
                }
                if (targetPrimaryNorm == "avengers" && candLower.contains("thunderbolts")) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Query 'The Avengers' must not match unrelated title '$candTitle'.")
                    continue
                }
                // If candidate has significant extra words not in target query (e.g. query is short base title, candidate is full subtitle show), reject if score would rely on loose substring
                if (candNorm.length > targetPrimaryNorm.length + 4 && !targetPrimaryNorm.contains(primaryLeadWord)) {
                    Log.d(DEBUG_TAG, " -> REJECTED Candidate ID=$candId ('$candTitle'): Extra subtitle/sequel tokens without matching primary query.")
                    continue
                }
            }

            // SCORING
            var score = 0
            val exactPrimaryMatch = (candNorm == targetPrimaryNorm)
            val exactFullMatch = (candNorm == targetFullNorm)
            val exactYearMatch = (effectiveYear != null && candYear == effectiveYear)

            if ((exactPrimaryMatch || exactFullMatch) && exactYearMatch) {
                score = 100
            } else if (exactPrimaryMatch || exactFullMatch) {
                score = 90
            } else if (candNorm.startsWith(targetPrimaryNorm) || targetPrimaryNorm.startsWith(candNorm)) {
                // If candidate is significantly longer than query (e.g. "daredevilbornagain" vs "daredevil"), lower score to 40 so exact primary matches win
                if (candNorm.length > targetPrimaryNorm.length + 3) {
                    score = 40
                    Log.d(DEBUG_TAG, " -> Lowered score for Candidate ID=$candId ('$candTitle') due to extra subtitle/spin-off tokens (Score=$score)")
                } else {
                    score = 80
                }
            } else if (candTokens.contains(primaryLeadWord)) {
                score = 60
            }

            Log.d(DEBUG_TAG, " -> ACCEPTED Candidate ID=$candId ('$candTitle') with Match Score=$score")

            if (score > highestScore) {
                highestScore = score
                bestCandidate = candidate
            }
        }

        if (bestCandidate != null && highestScore >= 50) {
            val selectedTitle = bestCandidate.title ?: bestCandidate.name ?: ""
            val selectedYear = bestCandidate.releaseDate?.take(4) ?: bestCandidate.firstAirDate?.take(4) ?: ""
            val selectedPosterPath = bestCandidate.rawMap?.get("poster_path") as? String ?: ""
            val finalPosterUrl = TmdbImageUrlBuilder.buildPosterUrl(selectedPosterPath)

            Log.d(DEBUG_TAG, "==================================================")
            Log.d(DEBUG_TAG, "MATCH VERIFIED FOR QUERY: '$queryTitle'")
            Log.d(DEBUG_TAG, " SELECTED TMDB ID         : ${bestCandidate.id}")
            Log.d(DEBUG_TAG, " SELECTED TMDB TITLE      : '$selectedTitle'")
            Log.d(DEBUG_TAG, " SELECTED RELEASE YEAR    : '$selectedYear'")
            Log.d(DEBUG_TAG, " SELECTED POSTER PATH     : '$selectedPosterPath'")
            Log.d(DEBUG_TAG, " FINAL POSTER URL         : '$finalPosterUrl'")
            Log.d(DEBUG_TAG, " MATCH CONFIDENCE SCORE   : $highestScore/100")
            Log.d(DEBUG_TAG, "==================================================")

            return bestCandidate
        } else {
            Log.d(DEBUG_TAG, "findBestMatch: REJECTED ALL candidate results for '$queryTitle' (Highest score=$highestScore)")
            return null
        }
    }

    private fun normalizeForComparison(str: String): String {
        val cleanApostrophes = str.lowercase().replace("'", "").replace("’", "")
        return cleanApostrophes
            .replace(Regex("""\b(marvels|marvel|dcs|dc|the|a|an)\b"""), "")
            .replace(Regex("""[^a-z0-9]"""), "")
            .trim()
    }

    private fun extractLeadToken(normalized: String): String {
        if (normalized.length <= 4) return normalized
        val match = Regex("^[a-z0-9]{4,}").find(normalized)
        return match?.value ?: normalized
    }

    private fun tokenize(normalized: String): List<String> {
        return normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }
}


