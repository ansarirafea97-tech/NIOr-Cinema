package com.example.util

object ShowDetailActionsHelper {

    // 1. Toggle Anti-Spoiler Blur State for specific selected episode
    fun toggleSpoilerReveal(
        context: android.content.Context,
        selectedEpisodeId: String? = null,
        onStateChanged: (Boolean) -> Unit
    ) {
        SelectedEpisodeSpoilerManager.toggleRevealForSelectedEpisode(context, selectedEpisodeId) {
            val isClear = SelectedEpisodeSpoilerManager.shouldShowClearPoster(selectedEpisodeId, false)
            onStateChanged(isClear)
        }
    }

    fun isSpoilerRevealed(episodeId: String? = null, isWatched: Boolean = false): Boolean {
        return SelectedEpisodeSpoilerManager.shouldShowClearPoster(episodeId, isWatched)
    }

    // 2. Safely Delete Physical Video File from Storage & Gallery
    fun deleteVideoFileWithConfirmation(
        context: android.content.Context,
        filePath: String?,
        onSuccess: () -> Unit
    ) {
        // Show Confirmation Dialog
        android.app.AlertDialog.Builder(context)
            .setTitle("Delete Video")
            .setMessage("Are you sure you want to remove this item from your library?")
            .setPositiveButton("Delete") { dialog, _ ->
                if (!filePath.isNullOrEmpty()) {
                    try {
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            file.delete()
                            // Scan media store to remove from Android Gallery immediately
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(file.absolutePath),
                                null,
                                null
                            )
                        }
                    } catch (e: Exception) {
                        // Ignore physical deletion failure
                    }
                }
                android.widget.Toast.makeText(context, "Item removed from library successfully", android.widget.Toast.LENGTH_SHORT).show()
                onSuccess()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
