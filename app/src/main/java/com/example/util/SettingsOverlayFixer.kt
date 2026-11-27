package com.example.util

/**
 * Helper object ensuring the Settings Popup renders as a standalone floating overlay on top of 
 * the player screen without changing parent view bounds or squeezing PlayerView.
 */
object SettingsOverlayFixer {

    /**
     * Ensures the Settings Popup renders as a standalone floating overlay on top of 
     * the player screen without changing parent view bounds or squeezing PlayerView.
     */
    fun showFloatingSettingsPanel(
        anchorView: android.view.View,
        contentView: android.view.View,
        context: android.content.Context
    ): android.widget.PopupWindow {
        
        val density = context.resources.displayMetrics.density
        val popupWidth = (260 * density).toInt() // Compact iOS width (260dp)

        val popupWindow = android.widget.PopupWindow(
            contentView,
            popupWidth,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Focusable so back press or outside touch closes it
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
            elevation = 20f
            // Transparent background so card shadow/rounded corners display cleanly
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        // Show attached to top-right/center-right of the player without squeezing root view
        if (anchorView.isAttachedToWindow) {
            popupWindow.showAsDropDown(anchorView, -popupWidth + anchorView.width, 10)
        } else {
            popupWindow.showAtLocation(anchorView.rootView, android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL, 30, 0)
        }

        return popupWindow
    }
}
