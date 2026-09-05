package com.focuslock.mdm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * The component library.
 *
 * One rule holds the whole design system together: **a screen never names a
 * colour**. It asks for a component, passes the resolved [UiPrefs.Tokens], and
 * gets back something already wearing the user's theme, font, radius, density
 * and text scale. That is why changing the accent reaches the block screen and
 * the dialogs, not just the dashboard.
 *
 * Everything is built in code rather than XML for the same reason: an XML
 * attribute is a hardcoded value waiting to drift out of sync with the tokens.
 */
object FocusUi {

    // ── Units ─────────────────────────────────────────────────────

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    fun dpf(context: Context, value: Int): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        )

    // ── System bars ───────────────────────────────────────────────

    /**
     * Paints the status and navigation bars to match the screen, and flips their
     * icon contrast to suit.
     *
     * Contrast is decided from the actual background colour rather than the
     * theme's light flag, because a light theme with a dark custom background
     * would otherwise end up with white icons on white.
     */
    fun applySystemBars(window: android.view.Window, tokens: UiPrefs.Tokens, navColor: Int? = null) {
        window.statusBarColor = tokens.background
        window.navigationBarColor = navColor ?: tokens.background

        val light = isLightColor(tokens.background)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = isLightColor(navColor ?: tokens.background)
    }

    fun isLightColor(color: Int): Boolean {
        val luminance = (0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)) / 255.0
        return luminance > 0.55
    }

    // ── Shapes ────────────────────────────────────────────────────

    fun roundedShape(
        context: Context,
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpf(context, radiusDp)
        setColor(color)
        if (strokeColor != null) setStroke(dp(context, strokeWidthDp), strokeColor)
    }

    fun pillShape(context: Context, color: Int, heightDp: Int): GradientDrawable =
        roundedShape(context, color, heightDp / 2)

    /** [roundedShape] with independent per-corner radii - the dialog sheet's top-only rounding needs this. */
    fun cornersShape(
        context: Context,
        color: Int,
        topLeftDp: Int,
        topRightDp: Int,
        bottomRightDp: Int,
        bottomLeftDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val tl = dpf(context, topLeftDp)
        val tr = dpf(context, topRightDp)
        val br = dpf(context, bottomRightDp)
        val bl = dpf(context, bottomLeftDp)
        cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
        setColor(color)
        if (strokeColor != null) setStroke(dp(context, strokeWidthDp), strokeColor)
    }

    /**
     * The primary-button gradient - the one deliberate gradient fill in the
     * app, reserved for the single highest-emphasis action on a screen.
     * `startColor` (usually tokens.accent) is the light stop; `endColor` is
     * the deep stop, so this looks right for whichever of the 8 accents the
     * user picked, not just the default blue.
     */
    fun gradientShape(context: Context, startColor: Int, endColor: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(startColor, endColor)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(context, radiusDp)
        }

    /**
     * The deep gradient stop for a given accent. For the default blue this is
     * the design doc's exact #1D4ED8 pairing with #4C93FF; the other 7 accents
     * have no doc-specified deep tone, so theirs is approximated by darkening
     * 25% toward black.
     */
    fun gradientDeep(color: Int): Int =
        if (color == android.graphics.Color.parseColor("#4C93FF")) {
            android.graphics.Color.parseColor("#1D4ED8")
        } else {
            UiPrefs.blend(color, android.graphics.Color.BLACK, 0.25f)
        }

    /**
     * Press feedback that follows the accent instead of the platform default,
     * so a tap looks like the rest of the app rather than like stock Android.
     */
    fun withRipple(context: Context, content: Drawable, tokens: UiPrefs.Tokens): Drawable =
        RippleDrawable(
            ColorStateList.valueOf(UiPrefs.withAlpha(tokens.accent, 70)),
            content,
            null
        )

    // ── Containers ────────────────────────────────────────────────

    /**
     * The root of every screen: wallpaper or flat background, plus the bedtime
     * dim as a real overlay so it also darkens whatever the screen draws.
     */
    fun screenRoot(context: Context, tokens: UiPrefs.Tokens): FrameLayout {
        val root = FrameLayout(context)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (tokens.wallpaperRes != 0) {
            root.setBackgroundResource(tokens.wallpaperRes)
        } else {
            root.setBackgroundColor(tokens.background)
        }
        return root
    }

    fun dimOverlay(context: Context, tokens: UiPrefs.Tokens): View? {
        if (tokens.dimPercent <= 0) return null
        val view = View(context)
        view.setBackgroundColor(
            UiPrefs.withAlpha(android.graphics.Color.BLACK, tokens.dimPercent * 255 / 100)
        )
        view.isClickable = false
        view.isFocusable = false
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        return view
    }

    fun column(context: Context, paddingDp: Int = 0): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (paddingDp > 0) {
            val padding = dp(context, paddingDp)
            layout.setPadding(padding, padding, padding, padding)
        }
        return layout
    }

    fun row(context: Context): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.CENTER_VERTICAL
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return layout
    }

    fun scroll(context: Context, content: View): NestedScrollView {
        val scroll = NestedScrollView(context)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scroll
    }

    fun horizontalScroll(context: Context, content: View): android.widget.HorizontalScrollView {
        val scroll = android.widget.HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scroll
    }

    fun spacer(context: Context, heightDp: Int): View {
        val view = View(context)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, heightDp)
        )
        return view
    }

    fun divider(context: Context, tokens: UiPrefs.Tokens, marginTopDp: Int = 0): View {
        val view = View(context)
        view.setBackgroundColor(tokens.divider)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 1)
        ).apply { topMargin = dp(context, marginTopDp) }
        return view
    }

    // ── Cards ─────────────────────────────────────────────────────

    /**
     * A hairline stroke rather than a shadow: elevation shadows disappear on
     * dark surfaces, and a 1px border reads as a real edge in every theme.
     */
    fun card(
        context: Context,
        tokens: UiPrefs.Tokens,
        elevated: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val padding = dp(context, tokens.density.cardPaddingDp)
        layout.setPadding(padding, padding, padding, padding)

        val fill = if (elevated) tokens.surfaceAlt else tokens.surface
        val shape = roundedShape(
            context,
            fill,
            tokens.cardRadiusDp,
            UiPrefs.blend(tokens.divider, fill, 0.2f)
        )

        if (onClick != null) {
            layout.background = withRipple(context, shape, tokens)
            layout.isClickable = true
            layout.isFocusable = true
            layout.setOnClickListener { onClick() }
        } else {
            layout.background = shape
        }

        layout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, tokens.density.gapDp) }
        return layout
    }

    // ── Type ──────────────────────────────────────────────────────

    /**
     * The one place that decides which face a piece of text gets: the
     * user's chosen prose font ([UiPrefs.Tokens.typeface]) for words, or the
     * fixed [UiPrefs.Tokens.monoTypeface] (always IBM Plex Mono) for anything
     * structural - numerals, timers, counts, chip/pill labels, overlines.
     * Weight is a real weight (400-800), not the old NORMAL/BOLD style pair,
     * so Figtree and Plex Mono's actual weight instances get used rather
     * than a synthetic bold.
     */
    fun applyFont(view: TextView, tokens: UiPrefs.Tokens, mono: Boolean = false, weight: Int = 400) {
        val base = if (mono) tokens.monoTypeface else tokens.typeface
        view.typeface = Typeface.create(base, weight, false)
    }

    private fun text(
        context: Context,
        tokens: UiPrefs.Tokens,
        value: CharSequence,
        sizeSp: Float,
        color: Int,
        weight: Int = 400,
        mono: Boolean = false,
        letterSpacing: Float = 0f
    ): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(sizeSp))
        view.setTextColor(color)
        applyFont(view, tokens, mono, weight)
        if (letterSpacing != 0f) view.letterSpacing = letterSpacing
        view.setLineSpacing(dpf(context, 3), 1f)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return view
    }

    // Sizes/weights below match the Components/Redesign design docs' type
    // scale exactly (2026-09 design system pass): display 34/40 700,
    // screenTitle 26/32 800, heading 19/25 700, rowTitle 15/20 600,
    // body 15/22 400, caption 13/18 500, overline 11/14 600 mono +9%,
    // numeral 46/46 500 mono.

    fun display(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 34f, tokens.textPrimary, 700, letterSpacing = -0.02f)

    fun title(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 26f, tokens.textPrimary, 800, letterSpacing = -0.02f)

    fun heading(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 19f, tokens.textPrimary, 700)

    fun body(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 15f, tokens.textPrimary, 400)

    fun secondary(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 15f, tokens.textSecondary, 400)

    /** The 15/20/600 title role [listRow] uses internally, exposed for a caller building its own row shape (e.g. a per-row card) instead of the shared row container. */
    fun rowTitle(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 15f, tokens.textPrimary, 600)

    fun caption(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 13f, tokens.textMuted, 500)

    /** Small all-caps label that opens a group of cards. Mono, per the "mono retreats to structure" rule. */
    fun sectionLabel(context: Context, tokens: UiPrefs.Tokens, value: String): TextView {
        val view = text(
            context,
            tokens,
            value.uppercase(),
            11f,
            tokens.textMuted,
            600,
            mono = true,
            letterSpacing = 0.09f
        )
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, tokens.density.gapDp + 6)
            bottomMargin = dp(context, 8)
        }
        return view
    }

    // ── Buttons ───────────────────────────────────────────────────

    private fun baseButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        fill: Int,
        textColor: Int,
        strokeColor: Int?,
        weight: Int = 600,
        gradient: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val view = TextView(context)
        view.text = label
        view.gravity = Gravity.CENTER
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(16f))
        view.setTextColor(textColor)
        applyFont(view, tokens, weight = weight)
        view.isAllCaps = false
        view.isClickable = true
        view.isFocusable = true

        val shape = if (gradient) {
            gradientShape(context, fill, gradientDeep(fill), tokens.buttonRadiusDp)
        } else {
            roundedShape(context, fill, tokens.buttonRadiusDp, strokeColor)
        }
        view.background = withRipple(context, shape, tokens)
        view.setOnClickListener {
            Motion.tap(view, tokens)
            onClick()
        }
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, tokens.density.buttonHeightDp)
        )
        return view
    }

    /**
     * The one gradient fill in the app, reserved for a screen's single
     * highest-emphasis action - #4C93FF -> #1D4ED8 on the default accent,
     * derived the same way for whichever of the 8 accents the user picked.
     */
    fun primaryButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(context, tokens, label, tokens.accent, tokens.onAccent, null, gradient = true, onClick = onClick)

    /** 500 weight, per the doc's secondary-button spec ("Set it up") - one step down from primary's 600. */
    fun secondaryButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(
        context,
        tokens,
        label,
        tokens.surfaceAlt,
        tokens.textPrimary,
        UiPrefs.blend(tokens.divider, tokens.surfaceAlt, 0.2f),
        weight = 500,
        onClick = onClick
    )

    fun ghostButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(
        context,
        tokens,
        label,
        UiPrefs.withAlpha(tokens.surface, 0),
        tokens.accent,
        null,
        weight = 500,
        onClick = onClick
    )

    /**
     * Danger red never fills a button - a border only, so a destructive
     * action never gets one accidental tap away. See FocusUi's own doc
     * comment on this: "a filled red button invites a mis-tap."
     */
    fun dangerButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(
        context,
        tokens,
        label,
        UiPrefs.withAlpha(tokens.danger, 0),
        tokens.danger,
        UiPrefs.withAlpha(tokens.danger, 102),
        weight = 600,
        onClick = onClick
    )

    fun smallButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView {
        val view = baseButton(
            context,
            tokens,
            label,
            tokens.surfaceAlt,
            tokens.textPrimary,
            UiPrefs.blend(tokens.divider, tokens.surfaceAlt, 0.2f),
            weight = 500,
            onClick = onClick
        )
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13f))
        val horizontal = dp(context, 14)
        view.setPadding(horizontal, 0, horizontal, 0)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(context, tokens.density.quickButtonHeightDp)
        ).apply { marginEnd = dp(context, 8) }
        return view
    }

    // ── Rows ──────────────────────────────────────────────────────

    /**
     * The workhorse: a title, an optional explanation, and a control on the
     * right. Every settings screen is made of these, which is why they all
     * behave the same.
     */
    fun listRow(
        context: Context,
        tokens: UiPrefs.Tokens,
        title: CharSequence,
        subtitle: CharSequence? = null,
        trailing: View? = null,
        leading: View? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val rowView = LinearLayout(context)
        rowView.orientation = LinearLayout.HORIZONTAL
        rowView.gravity = Gravity.CENTER_VERTICAL
        val padV = dp(context, 13)
        val padH = dp(context, 2)
        rowView.setPadding(padH, padV, padH, padV)
        rowView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowView.minimumHeight = dp(context, if (tokens.density.id == "compact") 48 else 56)

        if (leading != null) {
            rowView.addView(leading)
            rowView.addView(spacerH(context, 13))
        }

        val textColumn = LinearLayout(context)
        textColumn.orientation = LinearLayout.VERTICAL
        textColumn.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        val titleView = text(context, tokens, title, 15f, tokens.textPrimary, 600)
        textColumn.addView(titleView)
        if (!subtitle.isNullOrBlank()) {
            val subtitleView = text(context, tokens, subtitle, 13f, tokens.textMuted, 400)
            subtitleView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 2) }
            textColumn.addView(subtitleView)
        }
        rowView.addView(textColumn)

        if (trailing != null) {
            rowView.addView(spacerH(context, 13))
            rowView.addView(trailing)
        }

        if (onClick != null) {
            val shape = roundedShape(context, UiPrefs.withAlpha(tokens.surface, 0), tokens.rowRadiusDp)
            rowView.background = withRipple(context, shape, tokens)
            rowView.isClickable = true
            rowView.isFocusable = true
            rowView.setOnClickListener { onClick() }
        }
        return rowView
    }

    fun spacerH(context: Context, widthDp: Int): View {
        val view = View(context)
        view.layoutParams = LinearLayout.LayoutParams(dp(context, widthDp), 1)
        return view
    }

    fun chevron(context: Context, tokens: UiPrefs.Tokens): TextView {
        val view = TextView(context)
        view.text = "›"
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(18f))
        view.setTextColor(tokens.textMuted)
        applyFont(view, tokens, weight = 600)
        return view
    }

    /** Mono, accent-colored - a value being reported, not a word. Matches numeral/overline roles. */
    fun valueLabel(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))
        view.setTextColor(tokens.accent)
        applyFont(view, tokens, mono = true, weight = 500)
        return view
    }

    // ── Toggle ────────────────────────────────────────────────────

    fun switchControl(
        context: Context,
        tokens: UiPrefs.Tokens,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): SwitchCompat {
        val control = SwitchCompat(context)
        control.isChecked = checked
        applySwitchTint(control, tokens)
        control.setOnCheckedChangeListener { _, value ->
            applySwitchTint(control, tokens)
            onChange(value)
        }
        return control
    }

    private fun applySwitchTint(control: SwitchCompat, tokens: UiPrefs.Tokens) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        control.thumbTintList = ColorStateList(
            states,
            intArrayOf(tokens.background, UiPrefs.blend(tokens.textMuted, tokens.surface, 0.3f))
        )
        control.trackTintList = ColorStateList(
            states,
            intArrayOf(tokens.accent, tokens.track)
        )
    }

    /**
     * The workhorse toggle. Two states beyond plain on/off/disabled, both
     * opt-in via parameters rather than auto-detected, since a row has no
     * way to know on its own *why* it's locked:
     *
     * [frozen] - a session's rules are held still (SessionLock.isFrozen).
     * The row dims to 55% and gets a small padlock glyph next to the title,
     * but the switch keeps showing its real value underneath - the point is
     * "this is true, and you cannot change it right now," not "this is off."
     *
     * [missingPermissionHint] - the switch is on, but the permission it
     * depends on isn't granted, so it isn't actually doing anything. Gets a
     * warning-coloured ring on the switch itself plus a consequence line
     * naming what's missing, instead of silently pretending to work.
     */
    fun toggleRow(
        context: Context,
        tokens: UiPrefs.Tokens,
        title: CharSequence,
        subtitle: CharSequence?,
        checked: Boolean,
        enabled: Boolean = true,
        frozen: Boolean = false,
        missingPermissionHint: String? = null,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val control = switchControl(context, tokens, checked, onChange)
        control.isEnabled = enabled
        if (missingPermissionHint != null) {
            control.background = roundedShape(
                context,
                android.graphics.Color.TRANSPARENT,
                999,
                tokens.warning,
                strokeWidthDp = 2
            )
            val pad = dp(context, 3)
            control.setPadding(pad, pad, pad, pad)
        }

        val titleRow = row(context)
        titleRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val titleView = text(context, tokens, title, 15f, tokens.textPrimary, 600)
        titleView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleRow.addView(titleView)
        if (frozen) {
            titleRow.addView(spacerH(context, 6))
            titleRow.addView(kioskGlyph(context, tokens, sizeDp = 14))
        }

        val subtitleColumn = column(context)
        subtitleColumn.addView(titleRow)
        if (!subtitle.isNullOrBlank()) {
            val subtitleView = text(context, tokens, subtitle, 13f, tokens.textMuted, 400)
            subtitleView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 2) }
            subtitleColumn.addView(subtitleView)
        }
        if (missingPermissionHint != null) {
            val warnRow = row(context)
            warnRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) }
            val warnLabel = text(context, tokens, missingPermissionHint, 12.5f, tokens.warning, 500, mono = true)
            warnRow.addView(warnLabel)
            subtitleColumn.addView(warnRow)
        }

        val rowView = listRow(context, tokens, "", null, trailing = control, leading = null) {
            if (enabled) control.isChecked = !control.isChecked
        }
        // listRow already built a plain title TextView from the empty string
        // above; replace its text column with the richer one built here so
        // the glyph/warning line can sit inline with the title instead of
        // needing listRow to know about either.
        (rowView.getChildAt(0) as? LinearLayout)?.let { existingColumn ->
            val index = rowView.indexOfChild(existingColumn)
            rowView.removeViewAt(index)
            subtitleColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            rowView.addView(subtitleColumn, index)
        }
        rowView.alpha = if (frozen) 0.55f else if (enabled) 1f else 0.5f
        return rowView
    }

    /** A small padlock, used only for the frozen-row indicator - not part of the main icon set. */
    private fun kioskGlyph(context: Context, tokens: UiPrefs.Tokens, sizeDp: Int): ImageView {
        val view = ImageView(context)
        view.setImageResource(R.drawable.ic_glyph_kiosk)
        view.setColorFilter(tokens.textSecondary)
        val size = dp(context, sizeDp)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    /**
     * One of the 16-icon category set (design system, 2026-09 pass), sized and tinted for use as
     * a `listRow` leading icon or a section-header glyph. Nothing is baked into the vector - tint
     * is always applied here, `tokens.accent` for an emphasised row, `tokens.textSecondary` (the
     * default) otherwise.
     */
    fun categoryIcon(
        context: Context,
        tokens: UiPrefs.Tokens,
        @DrawableRes resId: Int,
        tint: Int = tokens.textSecondary,
        sizeDp: Int = 24
    ): ImageView {
        val view = ImageView(context)
        view.setImageResource(resId)
        view.setColorFilter(tint)
        val size = dp(context, sizeDp)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    // ── Slider ────────────────────────────────────────────────────

    fun sliderRow(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: String,
        min: Int,
        max: Int,
        value: Int,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, dp(context, 10), 0, dp(context, 10))

        val header = row(context)
        val labelView = body(context, tokens, label)
        labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val valueView = valueLabel(context, tokens, format(value))
        header.addView(labelView)
        header.addView(valueView)
        container.addView(header)

        val seek = SeekBar(context)
        seek.max = max - min
        seek.progress = (value - min).coerceIn(0, max - min)
        seek.progressTintList = ColorStateList.valueOf(tokens.accent)
        seek.thumbTintList = ColorStateList.valueOf(tokens.accent)
        seek.progressBackgroundTintList = ColorStateList.valueOf(tokens.track)
        seek.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 4) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val next = min + progress
                valueView.text = format(next)
                if (fromUser) onChange(next)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        container.addView(seek)
        return container
    }

    // ── Chips ─────────────────────────────────────────────────────

    /**
     * Selected fills `surfaceAlt`, not the accent - a deliberate correction
     * from the old accent-filled chip. Chips are usually a row of filter
     * options (All / Blocked / Paused / ...), and an accent-filled one reads
     * as "this is the important choice" rather than "this is what's showing
     * right now" - surfaceAlt plus the bolder text says the latter.
     */
    fun chip(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        val view = TextView(context)
        view.text = label
        view.isAllCaps = false
        view.gravity = Gravity.CENTER
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13f))
        view.setTextColor(if (selected) tokens.textPrimary else tokens.textSecondary)
        applyFont(view, tokens, weight = if (selected) 600 else 500)
        val horizontal = dp(context, 13)
        val vertical = dp(context, 8)
        view.setPadding(horizontal, vertical, horizontal, vertical)

        val fill = if (selected) tokens.surfaceAlt else UiPrefs.withAlpha(tokens.surface, 0)
        val stroke = if (selected) null else tokens.divider
        view.background = withRipple(context, roundedShape(context, fill, tokens.chipRadiusDp, stroke), tokens)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(context, 6) }
        return view
    }

    /** A single-choice chip strip that scrolls sideways rather than wrapping. */
    fun chipStrip(
        context: Context,
        tokens: UiPrefs.Tokens,
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ): View {
        val strip = row(context)
        strip.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        labels.forEachIndexed { index, label ->
            strip.addView(chip(context, tokens, label, index == selectedIndex) { onSelect(index) })
        }
        val scroll = horizontalScroll(context, strip)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 4) }
        return scroll
    }

    /** Mono, 14%-tint fill of its own status colour - never a solid fill. */
    fun pill(context: Context, tokens: UiPrefs.Tokens, label: CharSequence, color: Int): TextView {
        val view = TextView(context)
        view.text = label
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(11.5f))
        view.setTextColor(color)
        applyFont(view, tokens, mono = true, weight = 600)
        val horizontal = dp(context, 10)
        val vertical = dp(context, 5)
        view.setPadding(horizontal, vertical, horizontal, vertical)
        view.background = roundedShape(context, UiPrefs.withAlpha(color, 36), tokens.chipRadiusDp)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return view
    }

    // ── Data display ──────────────────────────────────────────────

    fun statTile(
        context: Context,
        tokens: UiPrefs.Tokens,
        value: CharSequence,
        label: CharSequence,
        weight: Float = 1f
    ): LinearLayout {
        val tile = LinearLayout(context)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        val padding = dp(context, 14)
        tile.setPadding(padding, padding, padding, padding)
        tile.background = roundedShape(
            context,
            tokens.surface,
            (tokens.cardRadiusDp - 2).coerceAtLeast(4), // "r18" against a card default of 20
            UiPrefs.blend(tokens.divider, tokens.surface, 0.2f)
        )

        // Mono numeral - a count being reported, not a word.
        val valueView = text(context, tokens, value, 24f, tokens.textPrimary, 500, mono = true)
        valueView.gravity = Gravity.CENTER
        val labelView = text(context, tokens, label, 13f, tokens.textMuted, 400)
        labelView.gravity = Gravity.CENTER
        labelView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 4) }

        tile.addView(valueView)
        tile.addView(labelView)
        tile.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            .apply { marginEnd = dp(context, 8) }
        return tile
    }

    /** A labelled proportion bar: used for category breakdowns and budgets. */
    fun meter(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        value: CharSequence,
        fraction: Float,
        color: Int
    ): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, dp(context, 8), 0, dp(context, 8))

        val header = row(context)
        val labelView = secondary(context, tokens, label)
        labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val valueView = body(context, tokens, value)
        valueView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        header.addView(labelView)
        header.addView(valueView)
        container.addView(header)

        val trackView = FrameLayout(context)
        trackView.background = roundedShape(context, tokens.track, 999)
        trackView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 8)
        ).apply { topMargin = dp(context, 6) }

        val fill = View(context)
        fill.background = roundedShape(context, color, 999)
        val safeFraction = fraction.coerceIn(0f, 1f)
        trackView.addView(
            fill,
            FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        trackView.post {
            val width = (trackView.width * safeFraction).toInt().coerceAtLeast(if (safeFraction > 0f) dp(context, 6) else 0)
            fill.layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT)
            fill.requestLayout()
        }
        container.addView(trackView)
        return container
    }

    /**
     * Sizes 96/152/236 (an unrecognised value falls back to 152), stroke
     * 8/8/10 to match - and always sweeps clockwise from 12 o'clock, in
     * every locale: this is a session's own remaining time, not text, so it
     * never mirrors for RTL.
     */
    fun progressRing(
        context: Context,
        tokens: UiPrefs.Tokens,
        sizeDp: Int = 152
    ): CircularProgressIndicator {
        val strokeDp = if (sizeDp >= 200) 10 else 8
        val ring = CircularProgressIndicator(context)
        ring.isIndeterminate = false
        ring.max = 100
        ring.trackThickness = dp(context, strokeDp)
        ring.indicatorSize = dp(context, sizeDp)
        ring.setIndicatorColor(tokens.accent)
        ring.trackColor = tokens.track
        ring.trackCornerRadius = dp(context, strokeDp / 2)
        return ring
    }

    /** Dashed border, body text at textMuted - not the smaller/dimmer `secondary()` treatment. */
    fun emptyState(context: Context, tokens: UiPrefs.Tokens, message: CharSequence): TextView {
        val view = text(context, tokens, message, 15f, tokens.textMuted, 400)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 28))
        val dashed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(context, tokens.cardRadiusDp)
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(dp(context, 1), tokens.divider, dpf(context, 4), dpf(context, 3))
        }
        view.background = dashed
        return view
    }

    fun appIcon(context: Context, tokens: UiPrefs.Tokens, packageName: String, sizeDp: Int = 36): View {
        val drawable = AppCatalog.icon(context, packageName)
        val size = dp(context, sizeDp)
        // radius = size x 0.31, per the doc's appIcon spec.
        val radiusDp = (sizeDp * 0.31f).toInt().coerceAtLeast(4)
        if (drawable == null) {
            val fallback = TextView(context)
            val label = AppCatalog.label(context, packageName)
            fallback.text = if (label.isNotEmpty()) label.substring(0, 1).uppercase() else "?"
            fallback.gravity = Gravity.CENTER
            fallback.setTextColor(tokens.textSecondary)
            applyFont(fallback, tokens, weight = 600)
            fallback.background = roundedShape(context, tokens.surfaceAlt, radiusDp)
            fallback.layoutParams = LinearLayout.LayoutParams(size, size)
            return fallback
        }
        val view = ImageView(context)
        view.setImageDrawable(drawable)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    // ── Input ─────────────────────────────────────────────────────

    fun input(
        context: Context,
        tokens: UiPrefs.Tokens,
        hint: String,
        value: String = "",
        multiline: Boolean = false,
        numeric: Boolean = false
    ): EditText {
        val field = EditText(context)
        field.hint = hint
        field.setText(value)
        field.setTextColor(tokens.textPrimary)
        field.setHintTextColor(tokens.textMuted)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))
        // Numeric fields use mono - a value being typed in, same rule as
        // valueLabel/statTile. Text fields keep the user's prose font.
        applyFont(field, tokens, mono = numeric, weight = if (numeric) 500 else 400)
        fun fieldShape(focused: Boolean) = roundedShape(
            context,
            tokens.input,
            tokens.rowRadiusDp,
            if (focused) tokens.accent else UiPrefs.blend(tokens.divider, tokens.input, 0.2f),
            strokeWidthDp = if (focused) 2 else 1 // nearest whole dp to the doc's 1.5dp focus ring
        )
        field.background = fieldShape(false)
        field.setOnFocusChangeListener { _, hasFocus -> field.background = fieldShape(hasFocus) }
        val padding = dp(context, 14)
        field.setPadding(padding, padding, padding, padding)
        field.inputType = when {
            numeric -> InputType.TYPE_CLASS_NUMBER
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiline) {
            field.gravity = Gravity.TOP or Gravity.START
            field.minLines = 4
        } else {
            field.maxLines = 1
        }
        field.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 10) }
        return field
    }

    // ── Page header ───────────────────────────────────────────────

    /**
     * Consistent top of every sub-screen: a back affordance with a real label,
     * because a bare arrow is one of the icons people most often misread.
     */
    fun pageHeader(
        context: Context,
        tokens: UiPrefs.Tokens,
        titleText: String,
        subtitle: String? = null,
        onBack: (() -> Unit)? = null
    ): LinearLayout {
        val container = column(context)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, tokens.density.gapDp) }

        if (onBack != null) {
            val back = TextView(context)
            back.text = "‹  Back"
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(14f))
            back.setTextColor(tokens.accent)
            applyFont(back, tokens, weight = 600)
            back.isClickable = true
            back.isFocusable = true
            val padding = dp(context, 8)
            back.setPadding(0, padding, padding, padding)
            back.setOnClickListener { onBack() }
            container.addView(back)
        }

        container.addView(title(context, tokens, titleText))
        if (!subtitle.isNullOrBlank()) {
            val subtitleView = secondary(context, tokens, subtitle)
            subtitleView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 6) }
            container.addView(subtitleView)
        }
        return container
    }
}
