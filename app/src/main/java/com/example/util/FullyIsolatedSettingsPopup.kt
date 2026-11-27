package com.example.util

object FullyIsolatedSettingsPopup {

    private var activePopup: android.widget.PopupWindow? = null

    fun showSettingsMenu(
        context: android.content.Context,
        anchorView: android.view.View,
        settingsContentView: android.view.View
    ) {
        activePopup?.dismiss()

        val density = context.resources.displayMetrics.density
        val popupWidthPx = (260 * density).toInt()

        // Wrap inside NestedScrollView for vertical scrolling support
        val scrollContainer = androidx.core.widget.NestedScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
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

        activePopup = popup

        // Render purely as an independent floating popup layer on top of video
        if (anchorView.isAttachedToWindow) {
            popup.showAsDropDown(anchorView, -popupWidthPx + anchorView.width, 10)
        } else {
            popup.showAtLocation(anchorView.rootView, android.view.Gravity.END or android.view.Gravity.TOP, 40, 100)
        }
    }

    fun dismiss() {
        activePopup?.dismiss()
        activePopup = null
    }
}
