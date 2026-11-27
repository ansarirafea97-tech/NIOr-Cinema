package com.example.util

object SelectedEpisodeSpoilerManager {

    /**
     * Toggles reveal status ONLY for the currently selected episode ID.
     */
    fun toggleRevealForSelectedEpisode(
        context: android.content.Context,
        selectedEpisodeId: String?,
        onStateChanged: () -> Unit
    ) {
        EyeButtonFixManager.onEyeButtonClick(context, selectedEpisodeId, onStateChanged)
    }

    /**
     * Checks if a specific episode should be shown clear (unblurred).
     */
    fun shouldShowClearPoster(episodeId: String?, isWatched: Boolean): Boolean {
        return EyeButtonFixManager.isRevealed(episodeId, isWatched)
    }
}
