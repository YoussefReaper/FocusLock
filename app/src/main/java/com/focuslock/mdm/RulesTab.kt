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
                activity.getString(R.string.rules_title),
                activity.getString(R.string.rules_subtitle)
            )
        )

        add(buildSummary())

        add(FocusUi.sectionLabel(activity, tokens, activity.getString(R.string.rules_section_what_to_manage)))
        add(buildEditors())

        add(FocusUi.sectionLabel(activity, tokens, activity.getString(R.string.rules_test_section)))
        add(buildTestCard())

        add(FocusUi.sectionLabel(activity, tokens, activity.getString(R.string.rules_section_capabilities)))
        add(buildCapabilityIntro())
        Capabilities.grouped().forEach { entry -> add(buildCapabilityGroup(entry.first, entry.second)) }

        add(FocusUi.sectionLabel(activity, tokens, activity.getString(R.string.rules_section_move_setup)))
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
                activity.getString(R.string.rules_stat_apps_blocked)
            )
        )
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                KeywordRules.activeRules(activity).size.toString(),
                activity.getString(R.string.rules_stat_words_watched)
            )
        )
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                (RuleStore.all(activity).size + ScheduleManager.getSchedules(activity).size).toString(),
                activity.getString(R.string.rules_stat_rules_and_windows)
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
                activity.getString(R.string.rules_editor_apps_title),
                activity.getString(
                    R.string.rules_editor_apps_subtitle,
                    AppRules.blockedPackages(activity).size,
                    AppRules.alwaysAllowed(activity).size
                ),
                Intent(activity, AppRulesActivity::class.java),
                R.drawable.ic_glyph_apps
            ),
            Editor(
                activity.getString(R.string.rules_editor_always_allowed_title),
                activity.getString(R.string.rules_editor_always_allowed_subtitle),
                Intent(activity, AlwaysAllowedActivity::class.java),
                R.drawable.ic_glyph_apps
            ),
            Editor(
                activity.getString(R.string.rules_editor_websites_title),
                activity.getString(
                    R.string.rules_editor_websites_subtitle,
                    AllowlistStore.getWebAllowlistUrls(activity).size
                ),
                Intent(activity, WebAllowlistEditorActivity::class.java),
                R.drawable.ic_glyph_guard
            ),
            Editor(
                activity.getString(R.string.rules_editor_keyword_guard_title),
                activity.getString(
                    R.string.rules_editor_keyword_guard_subtitle,
                    KeywordRules.userRules(activity).size
                ),
                Intent(activity, KeywordGuardActivity::class.java),
                R.drawable.ic_glyph_keywords
            ),
            Editor(
                activity.getString(R.string.rules_editor_schedules_title),
                activity.getString(
                    R.string.rules_editor_schedules_subtitle,
                    ScheduleManager.getSchedules(activity).size
                ),
                Intent(activity, ScheduleActivity::class.java),
                R.drawable.ic_glyph_schedules
            ),
            Editor(
                activity.getString(R.string.rules_editor_daily_limits_title),
                activity.getString(
                    R.string.rules_editor_daily_limits_subtitle,
                    AppLimits.allMinuteLimits(activity).size + AppLimits.allOpenLimits(activity).size
                ),
                Intent(activity, AppLimitsActivity::class.java),
                R.drawable.ic_glyph_limits
            ),
            Editor(
                activity.getString(R.string.rules_editor_bedtime_title),
                if (CapabilityRegistry.isEnabled(activity, Capabilities.BEDTIME_MODE)) {
                    Bedtime.formatWindow(activity)
                } else {
                    activity.getString(R.string.common_off)
                },
                Intent(activity, BedtimeActivity::class.java),
                R.drawable.ic_glyph_bedtime
            ),
            Editor(
                activity.getString(R.string.rules_editor_places_title),
                activity.getString(R.string.rules_editor_places_subtitle, PlaceRules.all(activity).size),
                Intent(activity, PlaceRulesActivity::class.java),
                R.drawable.ic_glyph_places
            ),
            Editor(
                activity.getString(R.string.rules_editor_custom_rules_title),
                activity.getString(R.string.rules_editor_custom_rules_subtitle, RuleStore.all(activity).size),
                Intent(activity, RuleEditorActivity::class.java),
                R.drawable.ic_glyph_rules
            ),
            Editor(
                activity.getString(R.string.rules_editor_tasks_title),
                if (EarnMode.isEnabled(activity)) {
                    activity.getString(
                        R.string.rules_editor_tasks_subtitle,
                        FocusTaskStore.open(activity).size,
                        EarnBudget.formatBalance(activity)
                    )
                } else {
                    activity.getString(R.string.rules_editor_tasks_off)
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

    // ── Test the block ────────────────────────────────────────────

    /**
     * A live, honest preview: opening an app while a test is running shows the
     * exact same intercept screen a real session would, computed by the same
     * [RuleEngine.decide] - not a mockup that can drift out of sync with what
     * the rules actually do. Nothing enforced ever engages for real (see
     * [TestMode]), and it can be ended from here or from the intercept screen
     * itself at any time.
     */
    private fun buildTestCard(): View {
        val card = FocusUi.card(activity, tokens)
        val active = TestMode.isActive(activity)
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                if (active) {
                    activity.getString(R.string.rules_test_active_body, TestMode.formatRemaining(activity))
                } else {
                    activity.getString(R.string.rules_test_inactive_body)
                }
            )
        )
        card.addView(FocusUi.spacer(activity, 10))

        when {
            active -> card.addView(
                FocusUi.dangerButton(activity, tokens, activity.getString(R.string.rules_test_end_button)) {
                    TestMode.end(activity)
                    render()
                }
            )
            !TestMode.canStart(activity) -> card.addView(
                FocusUi.caption(activity, tokens, activity.getString(R.string.rules_test_session_running))
            )
            else -> card.addView(
                FocusUi.primaryButton(activity, tokens, activity.getString(R.string.rules_test_start_button)) {
                    pickTestLength()
                }
            )
        }
        return card
    }

    private fun pickTestLength() {
        FocusDialog.singleChoice(
            activity,
            activity.getString(R.string.rules_test_length_title),
            activity.getString(R.string.rules_test_length_subtitle),
            listOf(
                FocusDialog.Choice("5", activity.getString(R.string.rules_test_minutes_5)),
                FocusDialog.Choice("10", activity.getString(R.string.rules_test_minutes_10)),
                FocusDialog.Choice("20", activity.getString(R.string.rules_test_minutes_20)),
                FocusDialog.Choice("30", activity.getString(R.string.rules_test_minutes_30))
            ),
            TestMode.DEFAULT_MINUTES.toString()
        ) { selected ->
            val minutes = selected.toIntOrNull() ?: TestMode.DEFAULT_MINUTES
            if (TestMode.start(activity, minutes)) {
                FocusDialog.toast(activity, activity.getString(R.string.rules_test_started_toast))
                render()
            } else {
                FocusDialog.toast(activity, activity.getString(R.string.rules_test_session_running_toast))
            }
        }
    }

    // ── Capabilities ──────────────────────────────────────────────

    private fun buildCapabilityIntro(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(FocusUi.secondary(activity, tokens, activity.getString(R.string.rules_capability_intro)))
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
            val link = FocusUi.smallButton(activity, tokens, activity.getString(R.string.rules_set_it_up)) {
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
            activity.getString(R.string.rules_perm_usage_access)
        spec.needsAccessibility && !SetupChecks.isContentGuardEnabled(activity) ->
            activity.getString(R.string.rules_perm_accessibility)
        spec.needsNotificationAccess && !SetupChecks.isNotificationAccessGranted(activity) ->
            activity.getString(R.string.rules_perm_notification_access)
        spec.needsDeviceOwner && !SetupChecks.isDeviceOwner(activity) ->
            activity.getString(R.string.rules_perm_device_owner)
        spec.needsLocation && !SetupChecks.hasLocationAccess(activity) ->
            activity.getString(R.string.rules_perm_location)
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
            FocusDialog.toast(activity, activity.getString(R.string.common_page_not_available))
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
                activity.getString(R.string.rules_profiles_title),
                activity.getString(R.string.rules_profiles_subtitle),
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, ProfilesActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                activity.getString(R.string.rules_reset_title),
                activity.getString(R.string.rules_reset_subtitle),
                trailing = FocusUi.chevron(activity, tokens)
            ) {
                FocusDialog.alert(
                    activity,
                    title = activity.getString(R.string.rules_reset_confirm_title),
                    message = activity.getString(R.string.rules_reset_confirm_message),
                    confirmLabel = activity.getString(R.string.rules_reset_confirm_button),
                    cancelLabel = activity.getString(R.string.common_cancel),
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
