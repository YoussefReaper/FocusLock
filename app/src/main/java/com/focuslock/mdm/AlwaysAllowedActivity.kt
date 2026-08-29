package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * The list nothing can override.
 *
 * This is the safety valve that makes every other rule acceptable to live with:
 * calls, maps, the clock and anything else the user names stay open through
 * kiosk sessions, schedule windows, bedtime and place rules alike. A blocker
 * that can cut off an ambulance call is a blocker nobody keeps installed.
 */
class AlwaysAllowedActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Always allowed"

    override fun screenSubtitle(): String =
        "Apps that stay reachable no matter what else is running."

    override fun buildContent(column: LinearLayout) {
        val enabled = CapabilityRegistry.isEnabled(this, Capabilities.ALWAYS_ALLOWED)

        column.addView(buildStateCard(enabled))
        if (!enabled) return

        column.addView(sectionLabel("Your list"))
        column.addView(buildList())
        column.addView(sectionLabel("Suggestions"))
        column.addView(buildSuggestions())
    }

    private fun buildStateCard(enabled: Boolean): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Honour this list",
                "When off, a session can lock away everything, including the phone app.",
                enabled
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.ALWAYS_ALLOWED, value)
                if (!value) {
                    Capabilities.spec(Capabilities.ALWAYS_ALLOWED)?.let { FocusDialog.weakenNotice(this, it) }
                }
                refresh()
            }
        )
    }

    private fun buildList(): View {
        val current = AppRules.alwaysAllowedRaw(this).toList().sortedBy { AppCatalog.label(this, it) }

        return card { card ->
            if (current.isEmpty()) {
                card.addView(
                    FocusUi.emptyState(
                        this,
                        tokens,
                        "Nothing here yet. Most people start with their dialler, messages and maps."
                    )
                )
            } else {
                current.forEachIndexed { index, packageName ->
                    card.addView(
                        FocusUi.listRow(
                            this,
                            tokens,
                            AppCatalog.label(this, packageName),
                            packageName,
                            trailing = FocusUi.smallButton(this, tokens, "Remove") {
                                AppRules.setAlwaysAllowed(this, current - packageName)
                                refresh()
                            },
                            leading = FocusUi.appIcon(this, tokens, packageName, 34)
                        )
                    )
                    if (index < current.size - 1) card.addView(FocusUi.divider(this, tokens))
                }
            }

            card.addView(FocusUi.spacer(this, 12))
            card.addView(
                FocusUi.primaryButton(this, tokens, "Choose apps") {
                    pickApps(
                        title = "Always allowed",
                        subtitle = "These stay open through every session, window and bedtime.",
                        selected = AppRules.alwaysAllowedRaw(this),
                        includeSystem = true
                    ) { selected -> AppRules.setAlwaysAllowed(this, selected) }
                }
            )
        }
    }

    /**
     * Detected from the phone's own defaults rather than a hardcoded list, so
     * it proposes the dialler this person actually uses.
     */
    private fun buildSuggestions(): View {
        val current = AppRules.alwaysAllowedRaw(this)
        val suggestions = AppCatalog.detectEssentials(this)
            .filterNot { it in current }
            .filter { AppCatalog.isInstalled(this, it) }

        return card { card ->
            if (suggestions.isEmpty()) {
                card.addView(
                    FocusUi.secondary(this, tokens, "Your essentials are already covered.")
                )
                return@card
            }

            card.addView(
                FocusUi.secondary(
                    this,
                    tokens,
                    "These look like essentials on this phone. Add any you want protected."
                )
            )
            card.addView(FocusUi.spacer(this, 8))

            suggestions.forEach { packageName ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        AppCatalog.label(this, packageName),
                        packageName,
                        trailing = FocusUi.smallButton(this, tokens, "Add") {
                            AppRules.addAlwaysAllowed(this, packageName)
                            refresh()
                        },
                        leading = FocusUi.appIcon(this, tokens, packageName, 34)
                    )
                )
            }

            card.addView(FocusUi.spacer(this, 10))
            card.addView(
                FocusUi.secondaryButton(this, tokens, "Add all of them") {
                    AppRules.setAlwaysAllowed(this, current + suggestions)
                    refresh()
                }
            )
        }
    }
}
