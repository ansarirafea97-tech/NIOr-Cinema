package com.example.util

object ScrollableSettingsUIHelper {

    /**
     * Configures the settings panel view hierarchy to ensure it scrolls properly
     * inside floating dialogs or PopupWindows.
     */
    fun makeSettingsViewScrollable(
        context: android.content.Context,
        settingsContentView: android.view.View
    ): android.view.View {
        
        // If already a ScrollView or NestedScrollView, return directly
        if (settingsContentView is android.widget.ScrollView || 
            settingsContentView is androidx.core.widget.NestedScrollView) {
            return settingsContentView
        }

        // Calculate max screen height allowed for popup (80% of screen height)
        val displayMetrics = context.resources.displayMetrics
        val maxHeightPx = (displayMetrics.heightPixels * 0.80).toInt()

        // Create a dynamic ScrollView wrapper
        val scrollView = androidx.core.widget.NestedScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Remove from existing parent if attached
        (settingsContentView.parent as? android.view.ViewGroup)?.removeView(settingsContentView)

        // Restrict content height if it exceeds screen bounds
        settingsContentView.post {
            if (settingsContentView.height > maxHeightPx) {
                val params = settingsContentView.layoutParams
                params.height = maxHeightPx
                settingsContentView.layoutParams = params
            }
        }

        scrollView.addView(settingsContentView)
        return scrollView
    }
}
