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
        /**
         * Numerals, timers, counts, state-chip labels and section overlines
         * use this - always IBM Plex Mono, regardless of [typeface]. That is
         * deliberate: it is what keeps the app's technical honesty legible
         * (a countdown reads as a countdown) without the whole app wearing
         * the "engineer-built" look full-mono body text gave it before.
         * Pass a weight (400/500/600) to FocusUi.applyFont(mono = true, ...)
         * to pick the matching instance out of this family.
         */
        val monoTypeface: Typeface,
        val radiusDp: Int,
        val textScale: Float,
        val density: UiDensity,
        val reducedMotion: Boolean,
        @DrawableRes val wallpaperRes: Int,
        val dimPercent: Int
    ) {
        fun scaled(sp: Float): Float = sp * textScale

        // Radius roles. The user's one slider (radiusDp, default 20 = the
        // card role below) offsets all four together, proportionally, so
        // "make corners rounder" still means one dial rather than four.
        val chipRadiusDp: Int get() = (radiusDp * 9 / 20).coerceIn(2, 18)
        val rowRadiusDp: Int get() = (radiusDp * 15 / 20).coerceIn(4, 26)
        // Not one of the doc's four named roles, but every button mockup in
        // it consistently uses r16 - close to but distinct from row(15).
        // Derived the same proportional way as the other roles so it still
        // answers to the one radius slider.
        val buttonRadiusDp: Int get() = (radiusDp * 16 / 20).coerceIn(4, 28)
        val cardRadiusDp: Int get() = radiusDp
        val heroRadiusDp: Int get() = (radiusDp * 24 / 20).coerceIn(6, 40)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    private const val DEFAULT_THEME_ID = "obsidian"
    private const val DEFAULT_FONT_ID = "figtree"
    private const val DEFAULT_DENSITY_ID = "comfortable"
    private const val DEFAULT_WALLPAPER_ID = "none"
    private const val DEFAULT_ACCENT_ID = "accent_blue"
    private const val DEFAULT_BACKGROUND_ID = "bg_default"
    // Card radius default moved 18->20 to match the design system's card
    // role exactly; chip/row/hero are derived proportionally from whatever
    // this slider is set to - see chipRadiusDp/rowRadiusDp/heroRadiusDp.
    private const val DEFAULT_CARD_RADIUS_DP = 20
    private const val DEFAULT_TEXT_SCALE = 1.0f

    // ── Palettes ──────────────────────────────────────────────────
    //
    // Every pair below clears 4.5:1 for body text and 3:1 for large text on its
    // own surfaces. The light theme exists because "focus app" does not have to
    // mean "black rectangle", and because a bright room is a real use case.
    //
    // Cut from 7 themes to 3 (2026-09, design system pass): Sage, Sand, Dawn
    // and Mist were the same dark-or-light theme with a tinted background,
    // which the separate `backgrounds` list already does as its own token -
    // 7 themes x 8 accents x 6 fonts x 3 densities was over a thousand
    // combinations to keep contrast-safe for very little real difference.
    // Migration.kt maps anyone still on the old ids onto the nearest of
    // these three, keeping their look as close as a straight retint allows.
    // Nocturne is untouched - it already had a distinct, deliberately quiet
    // job (the auto theme at bedtime) and didn't need retuning.

    val themes: List<UiTheme> = listOf(
        UiTheme(
            id = "obsidian",
            label = "Obsidian",
            isLight = false,
            background = Color.parseColor("#0A0C10"),
            card = Color.parseColor("#12151B"),
            cardAlt = Color.parseColor("#1A1F27"),
            textPrimary = Color.parseColor("#EDF1F7"),
            textSecondary = Color.parseColor("#98A3B4"),
            accent = Color.parseColor("#1D4ED8"),
            track = Color.parseColor("#1E2532"),
            divider = Color.parseColor("#232935"),
            input = Color.parseColor("#1E2532")
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
            background = Color.parseColor("#F7F8FA"),
            card = Color.parseColor("#FFFFFF"),
            cardAlt = Color.parseColor("#EFF2F6"),
            textPrimary = Color.parseColor("#10141A"),
            textSecondary = Color.parseColor("#5A6472"),
            accent = Color.parseColor("#1D6FE8"),
            track = Color.parseColor("#EDF1F6"),
            divider = Color.parseColor("#E2E7EE"),
            input = Color.parseColor("#EDF1F6")
        )
    )

    /**
     * Cut from 6 to 3 (2026-09, design system pass): Sans, Serif, Light and
     * Condensed were all still just the one job - carry prose - and Figtree
     * does that job better than any of them while giving the app an actual
     * typographic identity instead of "whatever Android ships." System and
     * Mono stay because they are real, different choices: System for
     * "match my phone," Mono for someone who wants the engineer-built look
     * on purpose. See [resolveMonoTypeface] for the *structural* mono use
     * (numerals, timers, chip labels) that applies regardless of this choice.
     *
     * The `typeface` field on the Figtree entry is a placeholder only -
     * loading the real bundled font needs a Context, which this static list
     * does not have. Every real read goes through [resolve], which swaps in
     * the actual font-family Typeface for whichever id is selected. Nothing
     * outside this file reads `.typeface` off a `fonts` list entry directly
     * (confirmed: PersonalizationActivity/YouTab's font pickers only read
     * `.id`/`.label`), so this placeholder is never seen by a screen.
     */
    val fonts: List<UiFont> = listOf(
        UiFont("figtree", "Figtree", Typeface.DEFAULT),
        UiFont("system", "System", Typeface.DEFAULT),
        UiFont("mono", "Mono", Typeface.MONOSPACE)
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
        // Retinted to the brand blue (2026-09, design system pass): neon
        // blue on near-black read as a gaming utility. This is the same
        // #1D4ED8 the marketing site and app icon use, so the product looks
        // like one thing across every surface. The lighter #4C93FF gradient
        // stop lives only on the primary-button gradient (FocusUi.roundedGradientShape),
        // derived from this at the point buttons are drawn - not stored
        // here, so it stays correct if someone picks a different accent.
        UiAccent("accent_blue", "Electric Blue", Color.parseColor("#1D4ED8")),
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
            typeface = resolveTypeface(context, getFont(context).id),
            monoTypeface = resolveMonoTypeface(context),
            radiusDp = getCardRadiusDp(context).coerceIn(0, 32),
            textScale = getTextScale(context).coerceIn(0.8f, 1.4f),
            density = getDensity(context),
            reducedMotion = reducedMotion(context),
            wallpaperRes = getWallpaper(context).drawableRes,
            dimPercent = if (Bedtime.isActive(context)) Bedtime.dimPercent(context) else 0
        )
    }

    /**
     * The static system typefaces need no Context; the bundled Figtree
     * family does, which is why this can't live in the [fonts] list itself.
     * `ResourcesCompat.getFont` keeps its own cache keyed by resource id, so
     * calling this on every [resolve] (i.e. most screen builds) does not
     * mean re-reading the font file each time.
     */
    private fun resolveTypeface(context: Context, fontId: String): Typeface = when (fontId) {
        "figtree" -> androidx.core.content.res.ResourcesCompat.getFont(context, R.font.figtree_family)
            ?: Typeface.DEFAULT
        "mono" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }

    private fun resolveMonoTypeface(context: Context): Typeface =
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ibm_plex_mono_family)
            ?: Typeface.MONOSPACE

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
