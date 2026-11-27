package com.example.util

object IOSPlayerUIHelper {

    // 1. Get Compact iOS Dialog Width (240dp converted to px)
    fun getCompactPopupWidth(context: android.content.Context): Int {
        val density = context.resources.displayMetrics.density
        return (240 * density).toInt()
    }

    // 2. Format percentage text (e.g. 85%)
    fun formatPercentage(currentValue: Int, maxValue: Int): String {
        if (maxValue <= 0) return "0%"
        val percent = ((currentValue.toDouble() / maxValue.toDouble()) * 100).toInt().coerceIn(0, 100)
        return "$percent%"
    }

    // 3. Configure Compact Popup Window Parameters
    fun applyIOSPopupStyle(dialogWindow: android.view.Window?, context: android.content.Context) {
        dialogWindow?.let { window ->
            val layoutParams = window.attributes
            layoutParams.width = getCompactPopupWidth(context)
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            window.attributes = layoutParams
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }
}
