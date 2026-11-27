package com.example.util

import android.util.Log

enum class TmdbImageType(val defaultSize: String) {
    POSTER("w780"),
    BACKDROP("w1280"),
    CAST_PROFILE("w185"),
    EPISODE_STILL("w500")
}

object TmdbImageUrlBuilder {
    private const val TAG = "MEDIA_METADATA"
    private const val BASE_CDN_URL = "https://image.tmdb.org/t/p/"

    /**
     * Centralized builder for all TMDB image path formatting.
     * Handles relative paths (e.g., "/abc.jpg"), full URLs, empty/null values safely.
     */
    fun buildUrl(
        pathOrUrl: String?,
        imageType: TmdbImageType = TmdbImageType.POSTER,
        sizeOverride: String? = null
    ): String {
        if (pathOrUrl.isNullOrBlank()) return ""
        
        val trimmed = pathOrUrl.trim()
        if (trimmed == "/" || trimmed.equals("null", ignoreCase = true)) return ""

        val size = sizeOverride ?: imageType.defaultSize

        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                // Ensure HTTPS for mobile data and normalize legacy/broken CDN domains
                val secureUrl = if (trimmed.startsWith("http://")) "https://" + trimmed.removePrefix("http://") else trimmed
                secureUrl.replace("image.tmdb.org/t3/p/", "image.tmdb.org/t/p/")
                    .replace("//t/p/", "/t/p/")
            }
            trimmed.startsWith("data:image") -> trimmed
            else -> {
                val cleanPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
                "$BASE_CDN_URL$size$cleanPath"
            }
        }

        Log.d(TAG, "TmdbImageUrlBuilder: input='$pathOrUrl', type=$imageType, size=$size -> output='$finalUrl'")
        return finalUrl
    }

    /**
     * Convenience method for poster formatting (HD w780).
     */
    fun buildPosterUrl(pathOrUrl: String?, size: String = "w780"): String {
        return buildUrl(pathOrUrl, TmdbImageType.POSTER, size)
    }

    /**
     * Convenience method for backdrop formatting (HD w1280).
     */
    fun buildBackdropUrl(pathOrUrl: String?, size: String = "w1280"): String {
        return buildUrl(pathOrUrl, TmdbImageType.BACKDROP, size)
    }

    /**
     * Convenience method for cast profile formatting.
     */
    fun buildCastProfileUrl(pathOrUrl: String?, size: String = "w185"): String {
        return buildUrl(pathOrUrl, TmdbImageType.CAST_PROFILE, size)
    }

    /**
     * Convenience method for episode still formatting.
     */
    fun buildEpisodeStillUrl(pathOrUrl: String?, size: String = "w500"): String {
        return buildUrl(pathOrUrl, TmdbImageType.EPISODE_STILL, size)
    }
}
