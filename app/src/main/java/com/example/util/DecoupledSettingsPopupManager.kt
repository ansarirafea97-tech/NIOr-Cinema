package com.example.util

object DecoupledSettingsPopupManager {

    private var currentPopup: android.widget.PopupWindow? = null

    /**
     * Shows settings panel as an independent floating popup layer.
     * GUARANTEE: Does NOT affect, shrink, or recalculate PlayerView bounds.
     */
    fun showSettings(
        context: android.content.Context,
        anchorView: android.view.View,
        settingsContentView: android.view.View
    ) {
        // Dismiss any active instance first
        dismiss()

        val density = context.resources.displayMetrics.density
        val popupWidthPx = (250 * density).toInt() // Compact 250dp width

        // Wrap inside NestedScrollView to fix vertical scroll bug
        val scrollContainer = androidx.core.widget.NestedScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            // Detach content from previous parent if needed
            (settingsContentView.parent as? android.view.ViewGroup)?.removeView(settingsContentView)
            addView(settingsContentView)
        }

        val popup = android.widget.PopupWindow(
            scrollContainer,
            popupWidthPx,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 24f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        currentPopup = popup

        // Display anchored to settings button without altering player layout
        if (anchorView.isAttachedToWindow) {
            popup.showAsDropDown(anchorView, -popupWidthPx + anchorView.width, 10)
        } else {
            popup.showAtLocation(anchorView.rootView, android.view.Gravity.END or android.view.Gravity.TOP, 40, 100)
        }
    }

    fun dismiss() {
        currentPopup?.dismiss()
        currentPopup = null
    }
}
