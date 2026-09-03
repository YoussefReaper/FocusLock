package com.focuslock.mdm

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The Rules tab: everything the user owns.
 *
 * This is the screen the whole refactor exists for. The top half is the
 * editors — apps, sites, words, times, places, budgets. The bottom half is the
 * Capability Registry itself: one switch per behaviour, grouped, with a plain
 * sentence saying what it does and what it needs.
 *
 * Turning something off here changes the phone immediately. Nothing waits for a
 * restart, and nothing turns itself back on.
 */
class RulesTab(activity: MainActivity, tokens: UiPrefs.Tokens) : FocusTab(activity, tokens) {

    private lateinit var container: LinearLayout
    private val collapsed = HashMap<String, Boolean>()

    override fun build(): View {
        container = FocusUi.column(activity, tokens.density.contentPaddingDp)
        return FocusUi.scroll(activity, container)
    }

    override fun onShow() {
        render()
    }

    private fun render() {
        container.removeAllViews()
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(
            FocusUi.pageHeader(
                activity,
                tokens,
                "Rules",
                "Every one of these is yours to set. Nothing here is decided for you."
            )
        )

        add(buildSummary())

        add(FocusUi.sectionLabel(activity, tokens, "What to manage"))
        add(buildEditors())

        add(FocusUi.sectionLabel(activity, tokens, "Capabilities"))
        add(buildCapabilityIntro())
        Capabilities.grouped().forEach { entry -> add(buildCapabilityGroup(entry.first, entry.second)) }

        add(FocusUi.sectionLabel(activity, tokens, "Move your setup"))
        add(buildProfileCard())

        Motion.stagger(added, tokens)
    }

    // ── Summary ───────────────────────────────────────────────────

    private fun buildSummary(): View {
        val row = FocusUi.row(activity)
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                AppRules.blockedPackages(activity).size.toString(),
                "Apps blocked"
            )
        )
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                KeywordRules.activeRules(activity).size.toString(),
                "Words watched"
            )
        )
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                (RuleStore.all(activity).size + ScheduleManager.getSchedules(activity).size).toString(),
                "Rules and windows"
            )
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = FocusUi.dp(activity, tokens.density.gapDp) }
        return row
    }

    // ── Editors ───────────────────────────────────────────────────

    private fun buildEditors(): View {
        val card = FocusUi.card(activity, tokens)

        val entries = listOf(
            Editor(
                "Apps",
                AppRules.blockedPackages(activity).size.toString() + " blocked, " +
                    AppRules.alwaysAllowed(activity).size + " always allowed",
                Intent(activity, AppRulesActivity::class.java),
                R.drawable.ic_glyph_apps
            ),
            Editor(
                "Always-allowed apps",
                "Essentials nothing can lock away",
                Intent(activity, AlwaysAllowedActivity::class.java),
                R.drawable.ic_glyph_apps
            ),
            Editor(
                "Websites",
                AllowlistStore.getWebAllowlistUrls(activity).size.toString() + " sites on the list",
                Intent(activity, WebAllowlistEditorActivity::class.java),
                R.drawable.ic_glyph_guard
            ),
            Editor(
                "Keyword guard",
                KeywordRules.userRules(activity).size.toString() + " of your own words",
                Intent(activity, KeywordGuardActivity::class.java),
                R.drawable.ic_glyph_keywords
            ),
            Editor(
                "Schedules",
                ScheduleManager.getSchedules(activity).size.toString() + " quiet windows",
                Intent(activity, ScheduleActivity::class.java),
                R.drawable.ic_glyph_schedules
            ),
            Editor(
                "Daily limits",
                (AppLimits.allMinuteLimits(activity).size + AppLimits.allOpenLimits(activity).size)
                    .toString() + " budgets set",
                Intent(activity, AppLimitsActivity::class.java),
                R.drawable.ic_glyph_limits
            ),
            Editor(
                "Bedtime",
                if (CapabilityRegistry.isEnabled(activity, Capabilities.BEDTIME_MODE)) {
                    Bedtime.formatWindow(activity)
                } else {
                    "Off"
                },
                Intent(activity, BedtimeActivity::class.java),
                R.drawable.ic_glyph_bedtime
            ),
            Editor(
                "Places and networks",
                PlaceRules.all(activity).size.toString() + " saved",
                Intent(activity, PlaceRulesActivity::class.java),
                R.drawable.ic_glyph_places
            ),
            Editor(
                "Custom rules",
                RuleStore.all(activity).size.toString() + " if-this-then-that rules",
                Intent(activity, RuleEditorActivity::class.java),
                R.drawable.ic_glyph_rules
            ),
            Editor(
                "Tasks and earning",
                if (EarnMode.isEnabled(activity)) {
                    FocusTaskStore.open(activity).size.toString() + " open · " +
                        EarnBudget.formatBalance(activity)
                } else {
                    "Earn mode is off"
                },
                Intent(activity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_TASKS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                R.drawable.ic_glyph_earn
            )
        )

        entries.forEachIndexed { index, editor ->
            card.addView(
                FocusUi.listRow(
                    activity,
                    tokens,
                    editor.title,
                    editor.subtitle,
                    trailing = FocusUi.chevron(activity, tokens),
                    leading = FocusUi.categoryIcon(activity, tokens, editor.icon)
                ) { activity.startActivity(editor.intent) }
            )
            if (index < entries.size - 1) card.addView(FocusUi.divider(activity, tokens))
        }
        return card
    }

    private data class Editor(val title: String, val subtitle: String, val intent: Intent, val icon: Int)

    // ── Capabilities ──────────────────────────────────────────────

    private fun buildCapabilityIntro(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "Every behaviour FocusLock has is one switch here. Turn any of them off and it stops " +
                    "immediately. If a switch needs a permission Android has not granted yet, it says so."
            )
        )
        return card
    }

    /**
     * Groups start collapsed except the first, so the list reads as seven
     * decisions rather than forty. The count in the header is the thing people
     * actually scan for.
     */
    private fun buildCapabilityGroup(group: CapabilityGroup, specs: List<CapabilitySpec>): View {
        val card = FocusUi.card(activity, tokens)
        val isCollapsed = collapsed[group.name] ?: (group != CapabilityGroup.MODES)
        val enabledCount = specs.count { CapabilityRegistry.isEnabled(activity, it.id) }

        val header = FocusUi.row(activity)
        header.isClickable = true
        header.isFocusable = true
        header.setOnClickListener {
            collapsed[group.name] = !isCollapsed
            render()
        }

        val titleColumn = FocusUi.column(activity)
        titleColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleColumn.addView(FocusUi.heading(activity, tokens, group.label))
        titleColumn.addView(FocusUi.caption(activity, tokens, group.blurb))
        header.addView(titleColumn)

        header.addView(
            FocusUi.pill(
                activity,
                tokens,
                enabledCount.toString() + "/" + specs.size,
                if (enabledCount > 0) tokens.accent else tokens.textMuted
            )
        )
        val marker = FocusUi.chevron(activity, tokens)
        marker.text = if (isCollapsed) "›" else "⌄"
        marker.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = FocusUi.dp(activity, 10) }
        header.addView(marker)
        card.addView(header)

        if (isCollapsed) return card

        card.addView(FocusUi.spacer(activity, 8))
        specs.forEachIndexed { index, spec ->
            card.addView(buildCapabilityRow(spec))
            if (index < specs.size - 1) card.addView(FocusUi.divider(activity, tokens))
        }
        return card
    }

    private fun buildCapabilityRow(spec: CapabilitySpec): View {
        val enabled = CapabilityRegistry.isEnabled(activity, spec.id)
        val blocker = permissionBlocker(spec)

        val column = FocusUi.column(activity)

        val frozen = SessionLock.isFrozen(activity)

        val control = FocusUi.switchControl(activity, tokens, enabled) { value ->
            if (!CapabilityRegistry.setEnabled(activity, spec.id, value)) {
                FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                render()
                return@switchControl
            }
            if (!value && spec.weakenNote != null) {
                FocusDialog.weakenNotice(activity, spec)
            }
            if (value && permissionBlocker(spec) != null) {
                promptForPermission(spec)
            }
            render()
        }
        control.isEnabled = !frozen

        column.addView(
            FocusUi.listRow(activity, tokens, spec.label, spec.blurb, trailing = control) {
                if (frozen) {
                    FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                } else {
                    control.isChecked = !control.isChecked
                }
            }
        )

        if (frozen) {
            column.addView(FocusUi.caption(activity, tokens, Copy.rulesFrozenHint(activity)))
        }

        if (enabled && blocker != null) {
            val warning = FocusUi.caption(activity, tokens, blocker)
            warning.setTextColor(tokens.warning)
            warning.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = FocusUi.dp(activity, 8) }
            warning.isClickable = true
            warning.setOnClickListener { promptForPermission(spec) }
            column.addView(warning)
        }

        if (enabled && spec.detailScreen != null) {
            val link = FocusUi.smallButton(activity, tokens, "Set it up") {
                openDetail(spec.detailScreen)
            }
            link.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                FocusUi.dp(activity, tokens.density.quickButtonHeightDp)
            ).apply { bottomMargin = FocusUi.dp(activity, 8) }
            column.addView(link)
        }

        return column
    }

    /** The honest line when a switch is on but Android has not granted the thing it needs. */
    private fun permissionBlocker(spec: CapabilitySpec): String? = when {
        spec.needsUsageAccess && !SetupChecks.hasUsageAccess(activity) ->
            "Needs usage access before it can do anything. Tap to grant."
        spec.needsAccessibility && !SetupChecks.isContentGuardEnabled(activity) ->
            "Needs the content guard turned on in Accessibility. Tap to grant."
        spec.needsNotificationAccess && !SetupChecks.isNotificationAccessGranted(activity) ->
            "Needs notification access. Tap to grant."
        spec.needsDeviceOwner && !SetupChecks.isDeviceOwner(activity) ->
            "Needs Device Owner, which is set from a computer. Tap to see how."
        spec.needsLocation && !SetupChecks.hasLocationAccess(activity) ->
            "Needs location permission. Tap to grant."
        else -> null
    }

    private fun promptForPermission(spec: CapabilitySpec) {
        val intent = when {
            spec.needsUsageAccess && !SetupChecks.hasUsageAccess(activity) ->
                Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            spec.needsAccessibility && !SetupChecks.isContentGuardEnabled(activity) ->
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            spec.needsNotificationAccess && !SetupChecks.isNotificationAccessGranted(activity) ->
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            spec.needsDeviceOwner && !SetupChecks.isDeviceOwner(activity) ->
                Intent(activity, DeviceOwnerHelpActivity::class.java)
            spec.needsLocation && !SetupChecks.hasLocationAccess(activity) -> {
                activity.requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_LOCATION
                )
                null
            }
            else -> null
        } ?: return

        LockManager.allowSettingsUntil(activity, System.currentTimeMillis() + 2 * 60 * 1000)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            FocusDialog.toast(activity, "That page is not available on this phone.")
        }
    }

    private fun openDetail(screen: String) {
        // Tasks is a tab, not a screen, so it switches rather than pushes.
        if (screen == Screens.TASKS) {
            activity.selectTab(MainActivity.TAB_TASKS)
            return
        }
        val intent = when (screen) {
            Screens.APP_RULES -> Intent(activity, AppRulesActivity::class.java)
            Screens.WEB_RULES -> Intent(activity, WebAllowlistEditorActivity::class.java)
            Screens.KEYWORDS -> Intent(activity, KeywordGuardActivity::class.java)
            Screens.LIMITS -> Intent(activity, AppLimitsActivity::class.java)
            Screens.SCHEDULES -> Intent(activity, ScheduleActivity::class.java)
            Screens.BEDTIME -> Intent(activity, BedtimeActivity::class.java)
            Screens.PLACES -> Intent(activity, PlaceRulesActivity::class.java)
            Screens.RULES_LIST -> Intent(activity, RuleEditorActivity::class.java)
            Screens.ALWAYS_ALLOWED -> Intent(activity, AlwaysAllowedActivity::class.java)
            Screens.PROFILES -> Intent(activity, ProfilesActivity::class.java)
            Screens.ANALYTICS -> Intent(activity, AnalyticsActivity::class.java)
            else -> null
        } ?: return
        activity.startActivity(intent)
    }

    // ── Profiles ──────────────────────────────────────────────────

    private fun buildProfileCard(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Profiles",
                "Save this whole setup, switch between setups, or move one to another phone.",
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, ProfilesActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Reset every capability",
                "Puts all the switches back to their starting positions. Your lists are untouched.",
                trailing = FocusUi.chevron(activity, tokens)
            ) {
                FocusDialog.alert(
                    activity,
                    title = "Reset the switches?",
                    message = "Your apps, sites, words and schedules stay exactly as they are. " +
                        "Only the capability switches go back to defaults.",
                    confirmLabel = "Reset",
                    cancelLabel = "Cancel",
                    onConfirm = {
                        if (!CapabilityRegistry.resetToDefaults(activity)) {
                            FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                        }
                        render()
                    }
                )
            }
        )
        return card
    }

    companion object {
        private const val REQUEST_LOCATION = 4711
    }
}
