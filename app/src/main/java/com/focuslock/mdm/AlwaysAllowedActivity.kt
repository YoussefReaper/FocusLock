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

    override fun screenTitle(): String = getString(R.string.always_allowed_title)

    override fun screenSubtitle(): String = getString(R.string.always_allowed_subtitle)

    override fun buildContent(column: LinearLayout) {
        val enabled = CapabilityRegistry.isEnabled(this, Capabilities.ALWAYS_ALLOWED)

        column.addView(buildStateCard(enabled))
        if (!enabled) return

        column.addView(sectionLabel(getString(R.string.always_allowed_section_your_list)))
        column.addView(buildList())
        column.addView(sectionLabel(getString(R.string.always_allowed_section_suggestions)))
        column.addView(buildSuggestions())
    }

    private fun buildStateCard(enabled: Boolean): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.always_allowed_honour_title),
                getString(R.string.always_allowed_honour_subtitle),
                enabled
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.ALWAYS_ALLOWED, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                } else if (!value) {
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
                card.addView(FocusUi.emptyState(this, tokens, getString(R.string.always_allowed_empty)))
            } else {
                current.forEachIndexed { index, packageName ->
                    card.addView(
                        FocusUi.listRow(
                            this,
                            tokens,
                            AppCatalog.label(this, packageName),
                            packageName,
                            trailing = FocusUi.smallButton(this, tokens, getString(R.string.common_remove)) {
                                if (!AppRules.setAlwaysAllowed(this, current - packageName)) {
                                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                                }
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
                FocusUi.primaryButton(this, tokens, getString(R.string.always_allowed_choose_apps)) {
                    pickApps(
                        title = getString(R.string.always_allowed_title),
                        subtitle = getString(R.string.always_allowed_pick_subtitle),
                        selected = AppRules.alwaysAllowedRaw(this),
                        includeSystem = true
                    ) { selected ->
                        // pickApps() already calls refresh() after this runs.
                        if (!AppRules.setAlwaysAllowed(this, selected)) {
                            FocusDialog.toast(this, SessionLock.refusalMessage(this))
                        }
                    }
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
                card.addView(FocusUi.secondary(this, tokens, getString(R.string.always_allowed_covered)))
                return@card
            }

            card.addView(FocusUi.secondary(this, tokens, getString(R.string.always_allowed_suggestions_intro)))
            card.addView(FocusUi.spacer(this, 8))

            suggestions.forEach { packageName ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        AppCatalog.label(this, packageName),
                        packageName,
                        trailing = FocusUi.smallButton(this, tokens, getString(R.string.common_add)) {
                            if (!AppRules.addAlwaysAllowed(this, packageName)) {
                                FocusDialog.toast(this, SessionLock.refusalMessage(this))
                            }
                            refresh()
                        },
                        leading = FocusUi.appIcon(this, tokens, packageName, 34)
                    )
                )
            }

            card.addView(FocusUi.spacer(this, 10))
            card.addView(
                FocusUi.secondaryButton(this, tokens, getString(R.string.always_allowed_add_all)) {
                    if (!AppRules.setAlwaysAllowed(this, current + suggestions)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            )
        }
    }
}
