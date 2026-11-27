package com.example.util

object EyeButtonFixManager {

    // Set of episode IDs manually revealed by user
    private val revealedEpisodeIds = mutableSetOf<String>()

    /**
     * Toggles reveal status for the given episode ID and triggers UI update.
     */
    fun onEyeButtonClick(
        context: android.content.Context,
        selectedEpisodeId: String?,
        onRefreshUI: () -> Unit
    ) {
        if (selectedEpisodeId.isNullOrEmpty()) {
            android.widget.Toast.makeText(context, "Please select an episode first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (revealedEpisodeIds.contains(selectedEpisodeId)) {
            revealedEpisodeIds.remove(selectedEpisodeId)
            android.widget.Toast.makeText(context, "Poster Blurred", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            revealedEpisodeIds.add(selectedEpisodeId)
            android.widget.Toast.makeText(context, "Poster Revealed", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Force UI update
        onRefreshUI()
    }

    /**
     * Determines if a specific episode thumbnail or description should be shown clear.
     */
    fun isRevealed(episodeId: String?, isWatched: Boolean): Boolean {
        if (isWatched) return true // Watched episodes are always clear
        if (!episodeId.isNullOrEmpty() && revealedEpisodeIds.contains(episodeId)) return true // Explicitly revealed
        return false // Default: Blurred
    }
}
