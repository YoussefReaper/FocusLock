package com.focuslock.mdm

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The full appearance controls.
 *
 * A phone someone is asking to live inside for weeks should look like something
 * they chose. Every control here writes a token that the whole app reads, so
 * there is no screen, dialog or block message that ignores it — which was the
 * single biggest visual problem with the old build.
 */
class PersonalizationActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Appearance"

    override fun screenSubtitle(): String =
        "Every screen, dialog and block message follows these."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildPreview())

        column.addView(sectionLabel("Theme"))
        column.addView(buildThemeCard())

        column.addView(sectionLabel("Background"))
        column.addView(buildBackgroundCard())

        column.addView(sectionLabel("Type and spacing"))
        column.addView(buildTypeCard())

        column.addView(sectionLabel("Accessibility"))
        column.addView(buildAccessibilityCard())

        column.addView(sectionLabel("What the dashboard shows"))
        column.addView(buildSectionsCard())

        column.addView(FocusUi.spacer(this, 8))
        column.addView(
            FocusUi.dangerButton(this, tokens, "Reset appearance to defaults") {
                FocusDialog.alert(
                    this,
                    "Reset appearance?",
                    "Only the look changes. Your rules and switches are untouched.",
                    confirmLabel = "Reset",
                    cancelLabel = "Cancel",
                    onConfirm = {
                        UiPrefs.resetToDefaults(this)
                        refresh()
                    }
                )
            }
        )
    }

    /** A live sample of the real components, so nothing has to be imagined. */
    private fun buildPreview(): View = card { card ->
        card.addView(FocusUi.caption(this, tokens, "PREVIEW"))
        card.addView(FocusUi.spacer(this, 10))
        card.addView(FocusUi.title(this, tokens, "Good evening"))
        card.addView(FocusUi.spacer(this, 4))
        card.addView(FocusUi.secondary(this, tokens, "Four days in a row"))
        card.addView(FocusUi.spacer(this, 12))

        val row = FocusUi.row(this)
        row.addView(FocusUi.statTile(this, tokens, "2h 14m", "Screen time"))
        row.addView(FocusUi.statTile(this, tokens, "37", "Opens"))
        card.addView(row)

        card.addView(FocusUi.spacer(this, 10))
        card.addView(FocusUi.primaryButton(this, tokens, "Primary action") { })
        card.addView(FocusUi.spacer(this, 8))
        card.addView(FocusUi.secondaryButton(this, tokens, "Secondary action") { })
    }

    private fun buildThemeCard(): View = card { card ->
        val current = UiPrefs.getTheme(this).id
        UiPrefs.themes.forEach { theme ->
            val swatchRow = FocusUi.row(this)
            swatchRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            listOf(theme.background, theme.card, theme.accent, theme.textPrimary).forEach { color ->
                val dot = View(this)
                val size = FocusUi.dp(this, 16)
                dot.background = FocusUi.roundedShape(this, color, 8, tokens.divider)
                dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = FocusUi.dp(this@PersonalizationActivity, 4)
                }
                swatchRow.addView(dot)
            }

            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    theme.label,
                    if (theme.isLight) "Light" else "Dark",
                    trailing = swatchRow,
                    leading = selectionMark(theme.id == current)
                ) {
                    UiPrefs.setThemeId(this, theme.id)
                    refresh()
                }
            )
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(FocusUi.caption(this, tokens, "ACCENT"))
        card.addView(buildAccentStrip())
    }

    private fun selectionMark(selected: Boolean): View {
        val dot = View(this)
        val size = FocusUi.dp(this, 10)
        dot.background = FocusUi.roundedShape(
            this,
            if (selected) tokens.accent else android.graphics.Color.TRANSPARENT,
            5,
            if (selected) null else tokens.divider
        )
        dot.layoutParams = LinearLayout.LayoutParams(size, size)
        return dot
    }

    private fun buildAccentStrip(): View {
        val strip = FocusUi.row(this)
        strip.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val current = UiPrefs.getAccent(this).id
        UiPrefs.accents.forEach { accent ->
            val swatch = View(this)
            val size = FocusUi.dp(this, 36)
            swatch.background = FocusUi.roundedShape(
                this,
                accent.color,
                18,
                if (accent.id == current) tokens.textPrimary else null,
                2
            )
            swatch.contentDescription = accent.label
            swatch.isClickable = true
            swatch.isFocusable = true
            swatch.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = FocusUi.dp(this@PersonalizationActivity, 10)
            }
            swatch.setOnClickListener {
                UiPrefs.setAccentId(this, accent.id)
                refresh()
            }
            strip.addView(swatch)
        }
        return FocusUi.horizontalScroll(this, strip)
    }

    private fun buildBackgroundCard(): View = card { card ->
        card.addView(FocusUi.caption(this, tokens, "WALLPAPER"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                UiPrefs.wallpapers.map { it.label },
                UiPrefs.wallpapers.indexOfFirst { it.id == UiPrefs.getWallpaper(this).id }
            ) { index ->
                UiPrefs.setWallpaperId(this, UiPrefs.wallpapers[index].id)
                refresh()
            }
        )

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.caption(this, tokens, "FLAT COLOUR"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                UiPrefs.backgrounds.map { it.label },
                UiPrefs.backgrounds.indexOfFirst { it.id == UiPrefs.getBackground(this).id }
            ) { index ->
                UiPrefs.setBackgroundId(this, UiPrefs.backgrounds[index].id)
                refresh()
            }
        )

        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                "A wallpaper sits above the flat colour. Choose None to see the colour."
            )
        )
    }

    private fun buildTypeCard(): View = card { card ->
        card.addView(FocusUi.caption(this, tokens, "TYPEFACE"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                UiPrefs.fonts.map { it.label },
                UiPrefs.fonts.indexOfFirst { it.id == UiPrefs.getFont(this).id }
            ) { index ->
                UiPrefs.setFontId(this, UiPrefs.fonts[index].id)
                refresh()
            }
        )

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.caption(this, tokens, "SPACING"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                UiPrefs.densities.map { it.label },
                UiPrefs.densities.indexOfFirst { it.id == UiPrefs.getDensity(this).id }
            ) { index ->
                UiPrefs.setDensityId(this, UiPrefs.densities[index].id)
                refresh()
            }
        )

        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "Text size",
                80,
                140,
                (UiPrefs.getTextScale(this) * 100).toInt(),
                { it.toString() + "%" }
            ) { value -> UiPrefs.setTextScale(this, value / 100f) }
        )
        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "Corner rounding",
                0,
                32,
                UiPrefs.getCardRadiusDp(this),
                { it.toString() + "dp" }
            ) { value -> UiPrefs.setCardRadiusDp(this, value) }
        )
        card.addView(FocusUi.smallButton(this, tokens, "Apply") { refresh() })
    }

    private fun buildAccessibilityCard(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Reduce motion",
                "No fades, no staggered entrances, no breathing animation.",
                UiPrefs.reducedMotion(this)
            ) { value ->
                UiPrefs.setReducedMotion(this, value)
                refresh()
            }
        )
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Higher contrast",
                "Pushes text and dividers further from the background.",
                UiPrefs.highContrast(this)
            ) { value ->
                UiPrefs.setHighContrast(this, value)
                refresh()
            }
        )
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                "Every theme here already clears the usual contrast bar for body text. " +
                    "This pushes further for glare, tired eyes, or a cracked screen."
            )
        )
    }

    private fun buildSectionsCard(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(this, tokens, "Session controls", null, UiPrefs.showKiosk(this)) { value ->
                UiPrefs.setShowKiosk(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "Quick settings row", null, UiPrefs.showQuickSettings(this)) { value ->
                UiPrefs.setShowQuickSettings(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "Today's numbers", null, UiPrefs.showStats(this)) { value ->
                UiPrefs.setShowStats(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "App grid in Library", null, UiPrefs.showAllowedApps(this)) { value ->
                UiPrefs.setShowAllowedApps(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "Safe browser card", null, UiPrefs.showWebButton(this)) { value ->
                UiPrefs.setShowWebButton(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "Video library card", null, UiPrefs.showVideoButton(this)) { value ->
                UiPrefs.setShowVideoButton(this, value)
            }
        )
        card.addView(
            FocusUi.toggleRow(this, tokens, "Schedule shortcuts", null, UiPrefs.showSchedule(this)) { value ->
                UiPrefs.setShowSchedule(this, value)
            }
        )

        val note = FocusUi.caption(
            this,
            tokens,
            "Hiding a card only hides it. Nothing stops running because you stopped looking at it."
        )
        note.gravity = Gravity.START
        card.addView(FocusUi.spacer(this, 8))
        card.addView(note)
    }
}
