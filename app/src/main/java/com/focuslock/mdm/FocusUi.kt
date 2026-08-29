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
            tokens.radiusDp,
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

    private fun text(
        context: Context,
        tokens: UiPrefs.Tokens,
        value: CharSequence,
        sizeSp: Float,
        color: Int,
        style: Int = Typeface.NORMAL,
        letterSpacing: Float = 0f
    ): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(sizeSp))
        view.setTextColor(color)
        view.typeface = if (style == Typeface.NORMAL) {
            tokens.typeface
        } else {
            Typeface.create(tokens.typeface, style)
        }
        if (letterSpacing != 0f) view.letterSpacing = letterSpacing
        view.setLineSpacing(dpf(context, 3), 1f)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return view
    }

    fun display(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 34f, tokens.textPrimary, Typeface.BOLD, -0.01f)

    fun title(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 22f, tokens.textPrimary, Typeface.BOLD)

    fun heading(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 16f, tokens.textPrimary, Typeface.BOLD)

    fun body(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 15f, tokens.textPrimary)

    fun secondary(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 13.5f, tokens.textSecondary)

    fun caption(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView =
        text(context, tokens, value, 12f, tokens.textMuted)

    /** Small all-caps label that opens a group of cards. */
    fun sectionLabel(context: Context, tokens: UiPrefs.Tokens, value: String): TextView {
        val view = text(
            context,
            tokens,
            value.uppercase(),
            11.5f,
            tokens.textMuted,
            Typeface.BOLD,
            0.09f
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
        onClick: () -> Unit
    ): TextView {
        val view = TextView(context)
        view.text = label
        view.gravity = Gravity.CENTER
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))
        view.setTextColor(textColor)
        view.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        view.isAllCaps = false
        view.isClickable = true
        view.isFocusable = true

        val shape = roundedShape(context, fill, minOf(tokens.radiusDp, 20), strokeColor)
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

    fun primaryButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(context, tokens, label, tokens.accent, tokens.onAccent, null, onClick)

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
        onClick
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
        tokens.textSecondary,
        tokens.divider,
        onClick
    )

    fun dangerButton(
        context: Context,
        tokens: UiPrefs.Tokens,
        label: CharSequence,
        onClick: () -> Unit
    ): TextView = baseButton(
        context,
        tokens,
        label,
        UiPrefs.withAlpha(tokens.danger, 32),
        tokens.danger,
        UiPrefs.withAlpha(tokens.danger, 110),
        onClick
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
            onClick
        )
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13f))
        val horizontal = dp(context, 14)
        view.setPadding(horizontal, 0, horizontal, 0)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(context, tokens.density.quickButtonHeightDp)
        ).apply { rightMargin = dp(context, 8) }
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
        val padV = dp(context, 12)
        val padH = dp(context, 2)
        rowView.setPadding(padH, padV, padH, padV)
        rowView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowView.minimumHeight = dp(context, 52)

        if (leading != null) {
            rowView.addView(leading)
            rowView.addView(spacerH(context, 14))
        }

        val textColumn = LinearLayout(context)
        textColumn.orientation = LinearLayout.VERTICAL
        textColumn.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        val titleView = body(context, tokens, title)
        titleView.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        textColumn.addView(titleView)
        if (!subtitle.isNullOrBlank()) {
            val subtitleView = secondary(context, tokens, subtitle)
            subtitleView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 3) }
            textColumn.addView(subtitleView)
        }
        rowView.addView(textColumn)

        if (trailing != null) {
            rowView.addView(spacerH(context, 12))
            rowView.addView(trailing)
        }

        if (onClick != null) {
            val shape = roundedShape(context, UiPrefs.withAlpha(tokens.surface, 0), 12)
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
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(22f))
        view.setTextColor(tokens.textMuted)
        view.typeface = tokens.typeface
        return view
    }

    fun valueLabel(context: Context, tokens: UiPrefs.Tokens, value: CharSequence): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13.5f))
        view.setTextColor(tokens.accent)
        view.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
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
            intArrayOf(tokens.accent, UiPrefs.blend(tokens.textMuted, tokens.surface, 0.3f))
        )
        control.trackTintList = ColorStateList(
            states,
            intArrayOf(UiPrefs.withAlpha(tokens.accent, 110), tokens.track)
        )
    }

    fun toggleRow(
        context: Context,
        tokens: UiPrefs.Tokens,
        title: CharSequence,
        subtitle: CharSequence?,
        checked: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val control = switchControl(context, tokens, checked, onChange)
        control.isEnabled = enabled
        val rowView = listRow(context, tokens, title, subtitle, trailing = control) {
            if (enabled) control.isChecked = !control.isChecked
        }
        rowView.alpha = if (enabled) 1f else 0.5f
        return rowView
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
        view.setTextColor(if (selected) tokens.onAccent else tokens.textSecondary)
        view.typeface = Typeface.create(tokens.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        val horizontal = dp(context, 16)
        val vertical = dp(context, 9)
        view.setPadding(horizontal, vertical, horizontal, vertical)

        val fill = if (selected) tokens.accent else tokens.surfaceAlt
        val stroke = if (selected) null else UiPrefs.blend(tokens.divider, tokens.surfaceAlt, 0.1f)
        view.background = withRipple(context, roundedShape(context, fill, 20, stroke), tokens)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(context, 8) }
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

    fun pill(context: Context, tokens: UiPrefs.Tokens, label: CharSequence, color: Int): TextView {
        val view = TextView(context)
        view.text = label
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(11.5f))
        view.setTextColor(color)
        view.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        val horizontal = dp(context, 10)
        val vertical = dp(context, 5)
        view.setPadding(horizontal, vertical, horizontal, vertical)
        view.background = roundedShape(context, UiPrefs.withAlpha(color, 34), 999)
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
            tokens.radiusDp,
            UiPrefs.blend(tokens.divider, tokens.surface, 0.2f)
        )

        val valueView = text(context, tokens, value, 20f, tokens.textPrimary, Typeface.BOLD)
        valueView.gravity = Gravity.CENTER
        val labelView = text(context, tokens, label, 11.5f, tokens.textMuted, Typeface.NORMAL, 0.04f)
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

    fun progressRing(
        context: Context,
        tokens: UiPrefs.Tokens,
        sizeDp: Int = 210
    ): CircularProgressIndicator {
        val ring = CircularProgressIndicator(context)
        ring.isIndeterminate = false
        ring.max = 100
        ring.trackThickness = dp(context, 10)
        ring.indicatorSize = dp(context, sizeDp)
        ring.setIndicatorColor(tokens.accent)
        ring.trackColor = tokens.track
        ring.trackCornerRadius = dp(context, 5)
        return ring
    }

    fun emptyState(context: Context, tokens: UiPrefs.Tokens, message: CharSequence): TextView {
        val view = secondary(context, tokens, message)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 12), dp(context, 26), dp(context, 12), dp(context, 26))
        return view
    }

    fun appIcon(context: Context, tokens: UiPrefs.Tokens, packageName: String, sizeDp: Int = 38): View {
        val drawable = AppCatalog.icon(context, packageName)
        val size = dp(context, sizeDp)
        if (drawable == null) {
            val fallback = TextView(context)
            val label = AppCatalog.label(context, packageName)
            fallback.text = if (label.isNotEmpty()) label.substring(0, 1).uppercase() else "?"
            fallback.gravity = Gravity.CENTER
            fallback.setTextColor(tokens.textSecondary)
            fallback.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
            fallback.background = roundedShape(context, tokens.surfaceAlt, sizeDp / 3)
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
        field.typeface = tokens.typeface
        field.background = roundedShape(
            context,
            tokens.input,
            minOf(tokens.radiusDp, 14),
            UiPrefs.blend(tokens.divider, tokens.input, 0.2f)
        )
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
            back.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
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
