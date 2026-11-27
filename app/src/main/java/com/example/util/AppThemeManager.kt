package com.example.util

object AppThemeManager {

    enum class AppTheme(val id: String, val primaryColorHex: String, val secondaryColorHex: String) {
        ORANGE("Orange", "#FF9500", "#FF8200"),
        CYBERPUNK("Cyberpunk", "#00F0FF", "#FF007F"),
        FROST("Frost", "#81E6D9", "#319795"),
        FOREST("Forest", "#4ADE80", "#059669"),
        FLARE("Flare", "#FBBF24", "#EF4444")
    }

    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME = "selected_theme_id"

    // Save Selected Theme
    fun setSelectedTheme(context: android.content.Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    // Get Current Selected Theme
    fun getSelectedTheme(context: android.content.Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_THEME, AppTheme.ORANGE.id)
        return AppTheme.values().find { it.id.equals(savedId, ignoreCase = true) } ?: AppTheme.ORANGE
    }

    // Apply Accent Color Dynamically to Bottom Navigation Bar
    fun applyThemeToBottomNav(context: android.content.Context, bottomNavView: android.view.View) {
        val currentTheme = getSelectedTheme(context)
        val activeColor = android.graphics.Color.parseColor(currentTheme.primaryColorHex)
        val inactiveColor = android.graphics.Color.parseColor("#8E8E93")

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(activeColor, inactiveColor)
        val colorStateList = android.content.res.ColorStateList(states, colors)

        try {
            val cls = bottomNavView.javaClass
            if (cls.name.contains("BottomNavigationView") || cls.name.contains("NavigationBarView")) {
                cls.getMethod("setItemIconTintList", android.content.res.ColorStateList::class.java).invoke(bottomNavView, colorStateList)
                cls.getMethod("setItemTextColor", android.content.res.ColorStateList::class.java).invoke(bottomNavView, colorStateList)
                cls.getMethod("setItemRippleColor", android.content.res.ColorStateList::class.java).invoke(bottomNavView, android.content.res.ColorStateList.valueOf(activeColor))
            }
        } catch (e: Exception) {
            // Ignore if method not present
        }

        // Custom Bottom Navigation Bar View Handling
        bottomNavView.findViewWithTag<android.view.View>("active_indicator")?.let { indicator ->
            indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
        }
    }
}
