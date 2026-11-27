package com.example.util

object AbsolutePlayerLayoutFixer {

    /**
     * Shows Settings Panel as a non-intrusive Floating Overlay.
     * Prevents any modification or resizing of the underlying PlayerView.
     */
    fun openSettingsOverlay(
        context: android.content.Context,
        anchorView: android.view.View,
        settingsContentView: android.view.View
    ): android.widget.PopupWindow {
        
        val density = context.resources.displayMetrics.density
        val popupWidthPx = (260 * density).toInt() // Compact iOS width (260dp)

        val scrollableContentView = ScrollableSettingsUIHelper.makeSettingsViewScrollable(context, settingsContentView)

        val popup = android.widget.PopupWindow(
            scrollableContentView,
            popupWidthPx,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Allows closing on back key or touch outside
        ).apply {
            isTouchable = true
            isOutsideTouchable = true
            isFocusable = true
            elevation = 24f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        // Show as floating dropdown attached to settings button (top-right overlay)
        if (anchorView.isAttachedToWindow) {
            popup.showAsDropDown(anchorView, -popupWidthPx + anchorView.width, 12)
        } else {
            popup.showAtLocation(anchorView.rootView, android.view.Gravity.END or android.view.Gravity.TOP, 40, 100)
        }

        return popup
    }
}
