package com.focuslock.mdm

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.DrawableRes

object UiPrefs {

    data class UiTheme(
        val id: String,
        val label: String,
        val background: Int,
        val card: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accent: Int,
        val track: Int,
        val divider: Int,
        val input: Int
    )

    data class UiFont(
        val id: String,
        val label: String,
        val typeface: Typeface
    )

    data class UiDensity(
        val id: String,
        val label: String,
        val contentPaddingDp: Int,
        val cardPaddingDp: Int,
        val buttonHeightDp: Int,
        val quickButtonHeightDp: Int,
        val browseHeightDp: Int
    )

    data class UiWallpaper(
        val id: String,
        val label: String,
        @DrawableRes val drawableRes: Int
    )

    data class UiAccent(
        val id: String,
        val label: String,
        val color: Int
    )

    data class UiBackground(
        val id: String,
        val label: String,
        val color: Int
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    private const val DEFAULT_THEME_ID = "obsidian"
    private const val DEFAULT_FONT_ID = "system"
    private const val DEFAULT_DENSITY_ID = "comfortable"
    private const val DEFAULT_WALLPAPER_ID = "none"
    private const val DEFAULT_ACCENT_ID = "accent_blue"
    private const val DEFAULT_BACKGROUND_ID = "bg_default"
    private const val DEFAULT_CARD_RADIUS_DP = 16
    private const val DEFAULT_TEXT_SCALE = 1.0f

    val themes: List<UiTheme> = listOf(
        UiTheme(
            id = "obsidian",
            label = "Obsidian",
            background = Color.parseColor("#0F0F0F"),
            card = Color.parseColor("#1A1A1A"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#737373"),
            accent = Color.parseColor("#3B82F6"),
            track = Color.parseColor("#1A1A1A"),
            divider = Color.parseColor("#1E1E1E"),
            input = Color.parseColor("#1A1A1A")
        ),
        UiTheme(
            id = "sage",
            label = "Sage",
            background = Color.parseColor("#0E1512"),
            card = Color.parseColor("#1C2621"),
            textPrimary = Color.parseColor("#E9F5ED"),
            textSecondary = Color.parseColor("#8AA393"),
            accent = Color.parseColor("#4CAF50"),
            track = Color.parseColor("#1C2621"),
            divider = Color.parseColor("#26322B"),
            input = Color.parseColor("#1C2621")
        ),
        UiTheme(
            id = "sand",
            label = "Sand",
            background = Color.parseColor("#17130C"),
            card = Color.parseColor("#221B12"),
            textPrimary = Color.parseColor("#F7EAD7"),
            textSecondary = Color.parseColor("#B8A58A"),
            accent = Color.parseColor("#E6A23C"),
            track = Color.parseColor("#221B12"),
            divider = Color.parseColor("#2A2218"),
            input = Color.parseColor("#221B12")
        ),
        UiTheme(
            id = "dawn",
            label = "Dawn",
            background = Color.parseColor("#0C1017"),
            card = Color.parseColor("#16202B"),
            textPrimary = Color.parseColor("#E6F1FF"),
            textSecondary = Color.parseColor("#7C8BA1"),
            accent = Color.parseColor("#FF8C42"),
            track = Color.parseColor("#16202B"),
            divider = Color.parseColor("#1F2A36"),
            input = Color.parseColor("#16202B")
        )
    )

    val fonts: List<UiFont> = listOf(
        UiFont("system", "System", Typeface.DEFAULT),
        UiFont("sans", "Sans", Typeface.SANS_SERIF),
        UiFont("serif", "Serif", Typeface.SERIF),
        UiFont("mono", "Mono", Typeface.MONOSPACE)
    )

    val densities: List<UiDensity> = listOf(
        UiDensity(
            id = "comfortable",
            label = "Comfortable",
            contentPaddingDp = 24,
            cardPaddingDp = 16,
            buttonHeightDp = 48,
            quickButtonHeightDp = 42,
            browseHeightDp = 56
        ),
        UiDensity(
            id = "compact",
            label = "Compact",
            contentPaddingDp = 16,
            cardPaddingDp = 12,
            buttonHeightDp = 42,
            quickButtonHeightDp = 36,
            browseHeightDp = 48
        )
    )

    val wallpapers: List<UiWallpaper> = listOf(
        UiWallpaper("none", "None", 0),
        UiWallpaper("slate", "Slate", R.drawable.bg_wallpaper_slate),
        UiWallpaper("sunset", "Sunset", R.drawable.bg_wallpaper_sunset),
        UiWallpaper("ocean", "Ocean", R.drawable.bg_wallpaper_ocean)
    )

    val accents: List<UiAccent> = listOf(
        UiAccent("accent_blue", "Electric Blue", Color.parseColor("#3B82F6")),
        UiAccent("accent_green", "Lime Green", Color.parseColor("#22C55E")),
        UiAccent("accent_orange", "Sunset Orange", Color.parseColor("#F97316")),
        UiAccent("accent_pink", "Neon Pink", Color.parseColor("#EC4899")),
        UiAccent("accent_teal", "Teal", Color.parseColor("#14B8A6")),
        UiAccent("accent_gold", "Gold", Color.parseColor("#F59E0B"))
    )

    val backgrounds: List<UiBackground> = listOf(
        UiBackground("bg_default", "Theme Default", Color.TRANSPARENT),
        UiBackground("bg_midnight", "Midnight", Color.parseColor("#0B0B0F")),
        UiBackground("bg_ink", "Ink", Color.parseColor("#0F141B")),
        UiBackground("bg_emerald", "Emerald", Color.parseColor("#0D1B17")),
        UiBackground("bg_sand", "Sand", Color.parseColor("#1A140D"))
    )

    fun getTheme(context: Context): UiTheme {
        val id = prefs(context).getString(Constants.KEY_UI_THEME, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID
        return themes.firstOrNull { it.id == id } ?: themes.first()
    }

    fun setThemeId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_THEME, id).apply()
    }

    fun getFont(context: Context): UiFont {
        val id = prefs(context).getString(Constants.KEY_UI_FONT, DEFAULT_FONT_ID) ?: DEFAULT_FONT_ID
        return fonts.firstOrNull { it.id == id } ?: fonts.first()
    }

    fun setFontId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_FONT, id).apply()
    }

    fun getDensity(context: Context): UiDensity {
        val id = prefs(context).getString(Constants.KEY_UI_DENSITY, DEFAULT_DENSITY_ID) ?: DEFAULT_DENSITY_ID
        return densities.firstOrNull { it.id == id } ?: densities.first()
    }

    fun setDensityId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_DENSITY, id).apply()
    }

    fun getWallpaper(context: Context): UiWallpaper {
        val id = prefs(context).getString(Constants.KEY_UI_WALLPAPER, DEFAULT_WALLPAPER_ID) ?: DEFAULT_WALLPAPER_ID
        return wallpapers.firstOrNull { it.id == id } ?: wallpapers.first()
    }

    fun setWallpaperId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_WALLPAPER, id).apply()
    }

    fun getAccent(context: Context): UiAccent {
        val id = prefs(context).getString(Constants.KEY_UI_ACCENT, DEFAULT_ACCENT_ID) ?: DEFAULT_ACCENT_ID
        return accents.firstOrNull { it.id == id } ?: accents.first()
    }

    fun setAccentId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_ACCENT, id).apply()
    }

    fun getBackground(context: Context): UiBackground {
        val id = prefs(context).getString(Constants.KEY_UI_BACKGROUND, DEFAULT_BACKGROUND_ID) ?: DEFAULT_BACKGROUND_ID
        return backgrounds.firstOrNull { it.id == id } ?: backgrounds.first()
    }

    fun setBackgroundId(context: Context, id: String) {
        prefs(context).edit().putString(Constants.KEY_UI_BACKGROUND, id).apply()
    }

    fun getCardRadiusDp(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(Constants.KEY_UI_CARD_RADIUS_DP, DEFAULT_CARD_RADIUS_DP)
    }

    fun setCardRadiusDp(context: Context, value: Int) {
        prefs(context).edit().putInt(Constants.KEY_UI_CARD_RADIUS_DP, value).apply()
    }

    fun getTextScale(context: Context): Float {
        val prefs = prefs(context)
        return prefs.getFloat(Constants.KEY_UI_TEXT_SCALE, DEFAULT_TEXT_SCALE)
    }

    fun setTextScale(context: Context, value: Float) {
        prefs(context).edit().putFloat(Constants.KEY_UI_TEXT_SCALE, value).apply()
    }

    fun showKiosk(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_KIOSK, true)

    fun setShowKiosk(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_KIOSK, value).apply()
    }

    fun showQuickSettings(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_QUICK, true)

    fun setShowQuickSettings(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_QUICK, value).apply()
    }

    fun showAllowedApps(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_ALLOWED_APPS, true)

    fun setShowAllowedApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_ALLOWED_APPS, value).apply()
    }

    fun showWebButton(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_WEB_BUTTON, true)

    fun setShowWebButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_WEB_BUTTON, value).apply()
    }

    fun showVideoButton(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_VIDEO, true)

    fun setShowVideoButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_VIDEO, value).apply()
    }

    fun showEditButtons(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_EDIT_BUTTONS, true)

    fun setShowEditButtons(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_EDIT_BUTTONS, value).apply()
    }

    fun showSchedule(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_SCHEDULE, true)

    fun setShowSchedule(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_SCHEDULE, value).apply()
    }

    fun resetToDefaults(context: Context) {
        prefs(context).edit()
            .putString(Constants.KEY_UI_THEME, DEFAULT_THEME_ID)
            .putString(Constants.KEY_UI_FONT, DEFAULT_FONT_ID)
            .putString(Constants.KEY_UI_DENSITY, DEFAULT_DENSITY_ID)
            .putString(Constants.KEY_UI_WALLPAPER, DEFAULT_WALLPAPER_ID)
            .putString(Constants.KEY_UI_ACCENT, DEFAULT_ACCENT_ID)
            .putString(Constants.KEY_UI_BACKGROUND, DEFAULT_BACKGROUND_ID)
            .putInt(Constants.KEY_UI_CARD_RADIUS_DP, DEFAULT_CARD_RADIUS_DP)
            .putFloat(Constants.KEY_UI_TEXT_SCALE, DEFAULT_TEXT_SCALE)
            .putBoolean(Constants.KEY_UI_SHOW_KIOSK, true)
            .putBoolean(Constants.KEY_UI_SHOW_QUICK, true)
            .putBoolean(Constants.KEY_UI_SHOW_ALLOWED_APPS, true)
            .putBoolean(Constants.KEY_UI_SHOW_WEB_BUTTON, true)
            .putBoolean(Constants.KEY_UI_SHOW_VIDEO, true)
            .putBoolean(Constants.KEY_UI_SHOW_EDIT_BUTTONS, true)
            .putBoolean(Constants.KEY_UI_SHOW_SCHEDULE, true)
            .apply()
    }
}
