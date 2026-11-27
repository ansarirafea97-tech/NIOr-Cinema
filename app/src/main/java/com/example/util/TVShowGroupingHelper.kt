package com.example.util

object TVShowGroupingHelper {

    data class UnifiedShow<T>(
        val showId: String,
        val showTitle: String,
        val seasonCount: Int,
        val allEpisodes: List<T>
    )

    /**
     * Groups individual media/episode files by TMDB Show ID (or normalized show title)
     * to prevent duplicate show cards on Home and Library screens.
     */
    fun <T> groupSeasonsUnderSingleShow(
        mediaList: List<T>,
        getShowId: (T) -> String,
        getShowTitle: (T) -> String,
        getSeasonNumber: (T) -> Int
    ): List<UnifiedShow<T>> {
        if (mediaList.isEmpty()) return emptyList()

        return mediaList.groupBy { item ->
            val id = getShowId(item).trim()
            if (id.isNotEmpty()) id else getShowTitle(item).lowercase().trim()
        }.map { (key, items) ->
            val firstItem = items.first()
            val totalSeasons = items.map { getSeasonNumber(it) }.distinct().size

            UnifiedShow(
                showId = key,
                showTitle = getShowTitle(firstItem),
                seasonCount = totalSeasons,
                allEpisodes = items
            )
        }
    }
}
