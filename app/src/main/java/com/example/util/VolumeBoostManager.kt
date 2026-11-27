package com.example.util

object VolumeBoostManager {

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var currentGainMb: Int = 0

    /**
     * Enables DSP Volume Boost for the active ExoPlayer / AudioTrack session.
     * @param audioSessionId The active audio session ID from ExoPlayer (player.audioSessionId)
     * @param gainMb Boost level in millibels (1000 mB = +10dB boost, 1500 mB = +15dB boost)
     */
    fun enableVolumeBoost(audioSessionId: Int, gainMb: Int = 1200) {
        try {
            if (audioSessionId != android.media.AudioAttributes.CONTENT_TYPE_UNKNOWN && audioSessionId != 0) {
                val existing = loudnessEnhancer
                if (existing != null && currentSessionId == audioSessionId) {
                    if (currentGainMb != gainMb) {
                        existing.setTargetGain(gainMb)
                        currentGainMb = gainMb
                    }
                    if (!existing.enabled) {
                        existing.enabled = true
                    }
                    return
                }

                disableVolumeBoost() // Clear any existing instance
                currentSessionId = audioSessionId
                currentGainMb = gainMb
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(gainMb) // Applies real DSP gain boost
                    enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Disables Volume Boost and releases audio DSP resources.
     */
    fun disableVolumeBoost() {
        try {
            loudnessEnhancer?.apply {
                enabled = false
                release()
            }
            loudnessEnhancer = null
            currentSessionId = 0
            currentGainMb = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Toggle Volume Boost state
     */
    fun toggleVolumeBoost(audioSessionId: Int, isEnabled: Boolean) {
        if (isEnabled) {
            enableVolumeBoost(audioSessionId, 1200) // ~12dB Boost
        } else {
            disableVolumeBoost()
        }
    }
}
