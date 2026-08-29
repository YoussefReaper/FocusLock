package com.focuslock.mdm

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.DrawableRes

/**
 * The design tokens, and the user's choices about them.
 *
 * Every colour, radius, spacing step and typeface in FocusLock comes from here.
 * No screen defines a colour of its own, which is what makes a theme change
 * reach dialogs, the block screen and the browser rather than just the
 * dashboard.
 */
object UiPrefs {

    data class UiTheme(
        val id: String,
        val label: String,
        val isLight: Boolean,
        val background: Int,
        val card: Int,
        val cardAlt: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accent: Int,
        val track: Int,
        val divider: Int,
        val input: Int
    )

    data class UiFont(val id: String, val label: String, val typeface: Typeface)

    data class UiDensity(
        val id: String,
        val label: String,
        val contentPaddingDp: Int,
        val cardPaddingDp: Int,
        val buttonHeightDp: Int,
        val quickButtonHeightDp: Int,
        val browseHeightDp: Int,
        val gapDp: Int
    )

    data class UiWallpaper(val id: String, val label: String, @DrawableRes val drawableRes: Int)

    data class UiAccent(val id: String, val label: String, val color: Int)

    data class UiBackground(val id: String, val label: String, val color: Int)

    /**
     * A fully resolved token set: theme, accent, background, font, density and
     * the bedtime override, collapsed into the numbers a view actually needs.
     */
    data class Tokens(
        val isLight: Boolean,
        val background: Int,
        val surface: Int,
        val surfaceAlt: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textMuted: Int,
        val accent: Int,
        val onAccent: Int,
        val accentSoft: Int,
        val divider: Int,
        val input: Int,
        val track: Int,
        val danger: Int,
        val success: Int,
        val warning: Int,
        val typeface: Typeface,
        val radiusDp: Int,
        val textScale: Float,
        val density: UiDensity,
        val reducedMotion: Boolean,
        @DrawableRes val wallpaperRes: Int,
        val dimPercent: Int
    ) {
        fun scaled(sp: Float): Float = sp * textScale
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    private const val DEFAULT_THEME_ID = "obsidian"
    private const val DEFAULT_FONT_ID = "system"
    private const val DEFAULT_DENSITY_ID = "comfortable"
    private const val DEFAULT_WALLPAPER_ID = "none"
    private const val DEFAULT_ACCENT_ID = "accent_blue"
    private const val DEFAULT_BACKGROUND_ID = "bg_default"
    private const val DEFAULT_CARD_RADIUS_DP = 18
    private const val DEFAULT_TEXT_SCALE = 1.0f

    // ── Palettes ──────────────────────────────────────────────────
    //
    // Every pair below clears 4.5:1 for body text and 3:1 for large text on its
    // own surfaces. The light themes exist because "focus app" does not have to
    // mean "black rectangle", and because a bright room is a real use case.

    val themes: List<UiTheme> = listOf(
        UiTheme(
            id = "obsidian",
            label = "Obsidian",
            isLight = false,
            background = Color.parseColor("#0B0B0D"),
            card = Color.parseColor("#16171A"),
            cardAlt = Color.parseColor("#1E2024"),
            textPrimary = Color.parseColor("#F5F6F7"),
            textSecondary = Color.parseColor("#9AA0A8"),
            accent = Color.parseColor("#3B82F6"),
            track = Color.parseColor("#22242A"),
            divider = Color.parseColor("#232529"),
            input = Color.parseColor("#1A1C20")
        ),
        UiTheme(
            id = "sage",
            label = "Sage",
            isLight = false,
            background = Color.parseColor("#0C1411"),
            card = Color.parseColor("#17211C"),
            cardAlt = Color.parseColor("#1F2C25"),
            textPrimary = Color.parseColor("#EAF5EE"),
            textSecondary = Color.parseColor("#94AC9D"),
            accent = Color.parseColor("#4ADE80"),
            track = Color.parseColor("#22302A"),
            divider = Color.parseColor("#243129"),
            input = Color.parseColor("#182219")
        ),
        UiTheme(
            id = "sand",
            label = "Sand",
            isLight = false,
            background = Color.parseColor("#15110B"),
            card = Color.parseColor("#211A11"),
            cardAlt = Color.parseColor("#2B2217"),
            textPrimary = Color.parseColor("#F8EEDC"),
            textSecondary = Color.parseColor("#BFAC90"),
            accent = Color.parseColor("#F5A524"),
            track = Color.parseColor("#2D2418"),
            divider = Color.parseColor("#2C2318"),
            input = Color.parseColor("#221A11")
        ),
        UiTheme(
            id = "dawn",
            label = "Dawn",
            isLight = false,
            background = Color.parseColor("#0A0F16"),
            card = Color.parseColor("#141D28"),
            cardAlt = Color.parseColor("#1C2836"),
            textPrimary = Color.parseColor("#E9F2FF"),
            textSecondary = Color.parseColor("#8A9AB0"),
            accent = Color.parseColor("#FF8C42"),
            track = Color.parseColor("#1E2A38"),
            divider = Color.parseColor("#1F2B39"),
            input = Color.parseColor("#16202C")
        ),
        UiTheme(
            id = "nocturne",
            label = "Nocturne",
            isLight = false,
            background = Color.parseColor("#000000"),
            card = Color.parseColor("#0D0D10"),
            cardAlt = Color.parseColor("#141419"),
            textPrimary = Color.parseColor("#D8D8DC"),
            textSecondary = Color.parseColor("#7A7A82"),
            accent = Color.parseColor("#8B7CF6"),
            track = Color.parseColor("#17171C"),
            divider = Color.parseColor("#17171C"),
            input = Color.parseColor("#101014")
        ),
        UiTheme(
            id = "paper",
            label = "Paper",
            isLight = true,
            background = Color.parseColor("#FAF9F7"),
            card = Color.parseColor("#FFFFFF"),
            cardAlt = Color.parseColor("#F1EFEB"),
            textPrimary = Color.parseColor("#17181A"),
            textSecondary = Color.parseColor("#5C6068"),
            accent = Color.parseColor("#2563EB"),
            track = Color.parseColor("#E5E3DE"),
            divider = Color.parseColor("#E7E4DF"),
            input = Color.parseColor("#F3F1ED")
        ),
        UiTheme(
            id = "mist",
            label = "Mist",
            isLight = true,
            background = Color.parseColor("#F3F6F9"),
            card = Color.parseColor("#FFFFFF"),
            cardAlt = Color.parseColor("#E8EDF3"),
            textPrimary = Color.parseColor("#111826"),
            textSecondary = Color.parseColor("#54607A"),
            accent = Color.parseColor("#0F766E"),
            track = Color.parseColor("#DDE4EC"),
            divider = Color.parseColor("#DFE5EC"),
            input = Color.parseColor("#EDF1F6")
        )
    )

    val fonts: List<UiFont> = listOf(
        UiFont("system", "System", Typeface.DEFAULT),
        UiFont("sans", "Sans", Typeface.SANS_SERIF),
        UiFont("serif", "Serif", Typeface.SERIF),
        UiFont("mono", "Mono", Typeface.MONOSPACE),
        UiFont("light", "Light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
        UiFont("condensed", "Condensed", Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    )

    val densities: List<UiDensity> = listOf(
        UiDensity(
            id = "comfortable",
            label = "Comfortable",
            contentPaddingDp = 20,
            cardPaddingDp = 18,
            buttonHeightDp = 52,
            quickButtonHeightDp = 44,
            browseHeightDp = 60,
            gapDp = 12
        ),
        UiDensity(
            id = "compact",
            label = "Compact",
            contentPaddingDp = 14,
            cardPaddingDp = 13,
            buttonHeightDp = 44,
            quickButtonHeightDp = 38,
            browseHeightDp = 50,
            gapDp = 8
        ),
        UiDensity(
            id = "roomy",
            label = "Roomy",
            contentPaddingDp = 26,
            cardPaddingDp = 22,
            buttonHeightDp = 58,
            quickButtonHeightDp = 50,
            browseHeightDp = 68,
            gapDp = 16
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
        UiAccent("accent_gold", "Gold", Color.parseColor("#F59E0B")),
        UiAccent("accent_violet", "Violet", Color.parseColor("#8B5CF6")),
        UiAccent("accent_slate", "Slate", Color.parseColor("#64748B"))
    )

    val backgrounds: List<UiBackground> = listOf(
        UiBackground("bg_default", "Theme default", Color.TRANSPARENT),
        UiBackground("bg_midnight", "Midnight", Color.parseColor("#08080C")),
        UiBackground("bg_ink", "Ink", Color.parseColor("#0D1219")),
        UiBackground("bg_emerald", "Emerald", Color.parseColor("#0B1A15")),
        UiBackground("bg_sand", "Sand", Color.parseColor("#181209")),
        UiBackground("bg_pure_black", "True black", Color.parseColor("#000000"))
    )

    // ── Getters and setters ───────────────────────────────────────

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

    fun getCardRadiusDp(context: Context): Int =
        prefs(context).getInt(Constants.KEY_UI_CARD_RADIUS_DP, DEFAULT_CARD_RADIUS_DP)

    fun setCardRadiusDp(context: Context, value: Int) {
        prefs(context).edit().putInt(Constants.KEY_UI_CARD_RADIUS_DP, value).apply()
    }

    fun getTextScale(context: Context): Float =
        prefs(context).getFloat(Constants.KEY_UI_TEXT_SCALE, DEFAULT_TEXT_SCALE)

    fun setTextScale(context: Context, value: Float) {
        prefs(context).edit().putFloat(Constants.KEY_UI_TEXT_SCALE, value).apply()
    }

    /** Honours the person, not just the OS: this is a FocusLock-level choice. */
    fun reducedMotion(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_REDUCED_MOTION, false)

    fun setReducedMotion(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_REDUCED_MOTION, value).apply()
    }

    fun highContrast(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_HIGH_CONTRAST, value).apply()
    }

    // ── Section visibility ────────────────────────────────────────

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

    fun showStats(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_UI_SHOW_STATS, true)

    fun setShowStats(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_UI_SHOW_STATS, value).apply()
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
            .putBoolean(Constants.KEY_UI_REDUCED_MOTION, false)
            .putBoolean(Constants.KEY_UI_HIGH_CONTRAST, false)
            .putBoolean(Constants.KEY_UI_SHOW_KIOSK, true)
            .putBoolean(Constants.KEY_UI_SHOW_QUICK, true)
            .putBoolean(Constants.KEY_UI_SHOW_ALLOWED_APPS, true)
            .putBoolean(Constants.KEY_UI_SHOW_WEB_BUTTON, true)
            .putBoolean(Constants.KEY_UI_SHOW_VIDEO, true)
            .putBoolean(Constants.KEY_UI_SHOW_EDIT_BUTTONS, true)
            .putBoolean(Constants.KEY_UI_SHOW_SCHEDULE, true)
            .putBoolean(Constants.KEY_UI_SHOW_STATS, true)
            .apply()
    }

    // ── Resolution ────────────────────────────────────────────────

    /**
     * The one call every screen makes. Combines the user's choices with the
     * bedtime override, so night automatically means the quiet palette without
     * the user losing their daytime theme.
     */
    fun resolve(context: Context): Tokens {
        val bedtimeNow = Bedtime.isActive(context) && Bedtime.forcesDarkTheme(context)
        val base = if (bedtimeNow) {
            themes.firstOrNull { it.id == "nocturne" } ?: getTheme(context)
        } else {
            getTheme(context)
        }

        val accentChoice = getAccent(context).color
        val backgroundChoice = getBackground(context).color
        val background = if (backgroundChoice == Color.TRANSPARENT) base.background else backgroundChoice
        val highContrast = highContrast(context)

        val textPrimary = if (highContrast) {
            if (base.isLight) Color.parseColor("#000000") else Color.parseColor("#FFFFFF")
        } else {
            base.textPrimary
        }
        val textSecondary = if (highContrast) {
            blend(textPrimary, background, 0.25f)
        } else {
            base.textSecondary
        }

        return Tokens(
            isLight = base.isLight,
            background = background,
            surface = base.card,
            surfaceAlt = base.cardAlt,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = blend(textSecondary, background, 0.35f),
            accent = accentChoice,
            onAccent = readableOn(accentChoice),
            accentSoft = blend(accentChoice, background, 0.82f),
            divider = if (highContrast) blend(textPrimary, background, 0.6f) else base.divider,
            input = base.input,
            track = base.track,
            danger = if (base.isLight) Color.parseColor("#DC2626") else Color.parseColor("#F87171"),
            success = if (base.isLight) Color.parseColor("#15803D") else Color.parseColor("#4ADE80"),
            warning = if (base.isLight) Color.parseColor("#B45309") else Color.parseColor("#FBBF24"),
            typeface = getFont(context).typeface,
            radiusDp = getCardRadiusDp(context).coerceIn(0, 32),
            textScale = getTextScale(context).coerceIn(0.8f, 1.4f),
            density = getDensity(context),
            reducedMotion = reducedMotion(context),
            wallpaperRes = getWallpaper(context).drawableRes,
            dimPercent = if (Bedtime.isActive(context)) Bedtime.dimPercent(context) else 0
        )
    }

    /** Black or white, whichever is actually readable on the given colour. */
    fun readableOn(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (luminance > 0.6) Color.parseColor("#101114") else Color.parseColor("#FFFFFF")
    }

    fun blend(color: Int, towards: Int, amount: Float): Int {
        val ratio = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * (1 - ratio) + Color.red(towards) * ratio).toInt(),
            (Color.green(color) * (1 - ratio) + Color.green(towards) * ratio).toInt(),
            (Color.blue(color) * (1 - ratio) + Color.blue(towards) * ratio).toInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
