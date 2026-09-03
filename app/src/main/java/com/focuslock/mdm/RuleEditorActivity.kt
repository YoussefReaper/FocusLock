package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout
import java.util.Calendar

/**
 * Custom rules: target, condition, outcome.
 *
 * Deliberately first-match-wins, top to bottom, with explicit move up and move
 * down. Priority systems people cannot see are priority systems people get
 * wrong, and a blocker that behaves unpredictably is one that gets blamed and
 * then removed.
 */
class RuleEditorActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Custom rules"

    override fun screenSubtitle(): String =
        "The first rule that matches wins. Drag-free ordering, top to bottom."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggle())
        if (!CapabilityRegistry.isEnabled(this, Capabilities.RULE_ENGINE)) return

        column.addView(sectionLabel("Your rules"))
        column.addView(buildList())
        column.addView(sectionLabel("Start from an example"))
        column.addView(buildTemplates())
    }

    private fun buildToggle(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Custom rules",
                "With this off, the rules below are kept but none of them run.",
                CapabilityRegistry.isEnabled(this, Capabilities.RULE_ENGINE)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.RULE_ENGINE, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
    }

    private fun buildList(): View = card { card ->
        val rules = RuleStore.all(this)
        val frozen = SessionLock.isFrozen(this)

        if (rules.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, Copy.emptyRules(this)))
        } else {
            rules.forEachIndexed { index, rule ->
                val controls = FocusUi.row(this)
                controls.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // Reordering has no useful "refused" state to explain, so it
                // simply isn't offered while frozen rather than toasting on
                // every tap.
                if (index > 0 && !frozen) {
                    controls.addView(
                        FocusUi.smallButton(this, tokens, "↑") {
                            RuleStore.move(this, rule.id, -1)
                            refresh()
                        }
                    )
                }
                if (index < rules.size - 1 && !frozen) {
                    controls.addView(
                        FocusUi.smallButton(this, tokens, "↓") {
                            RuleStore.move(this, rule.id, 1)
                            refresh()
                        }
                    )
                }
                controls.addView(
                    FocusUi.switchControl(this, tokens, rule.enabled) { value ->
                        if (!RuleStore.update(this, rule.copy(enabled = value))) {
                            FocusDialog.toast(this, SessionLock.refusalMessage(this))
                        }
                        refresh()
                    }
                )

                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        (index + 1).toString() + ". " + describeRule(rule),
                        describeCondition(rule),
                        trailing = controls
                    ) { editRule(rule) }
                )
                if (index < rules.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.primaryButton(this, tokens, "New rule") { createRule() })
    }

    private fun describeRule(rule: Rule): String {
        if (rule.label.isNotBlank()) return rule.label
        val target = when (rule.targetType) {
            RuleTargetType.ALL -> "Everything"
            RuleTargetType.APP -> AppCatalog.label(this, rule.targetValue)
            RuleTargetType.CATEGORY -> AppCategory.fromId(rule.targetValue).label
        }
        return target + ": " + rule.action.label.lowercase()
    }

    private fun describeCondition(rule: Rule): String = when (rule.conditionType) {
        RuleConditionType.ALWAYS -> "Always"
        RuleConditionType.SESSION_ONLY -> "Only during a session"
        RuleConditionType.TIME ->
            "Between " + ScheduleManager.formatTime(rule.conditionStart) +
                " and " + ScheduleManager.formatTime(rule.conditionEnd)
        RuleConditionType.DAYS ->
            if (rule.conditionDays.isEmpty()) "Any day" else rule.conditionDays.joinToString { dayName(it) }
        RuleConditionType.PLACE ->
            "At " + (PlaceRules.all(this).firstOrNull { it.id == rule.conditionValue }?.label ?: "a place")
        RuleConditionType.WIFI -> "On " + rule.conditionValue
        RuleConditionType.USAGE_OVER -> "After " + rule.conditionNumber + " minutes today"
        RuleConditionType.OPENS_OVER -> "After " + rule.conditionNumber + " opens today"
    }

    private fun dayName(day: Int): String = when (day) {
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> "Sun"
    }

    // ── Creating ──────────────────────────────────────────────────

    private fun createRule() {
        if (SessionLock.isFrozen(this)) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        val rule = RuleStore.newRule(
            label = "",
            targetType = RuleTargetType.CATEGORY,
            targetValue = AppCategory.SOCIAL.id,
            conditionType = RuleConditionType.ALWAYS,
            action = RuleAction.BLOCK
        )
        RuleStore.add(this, rule)
        refresh()
        editRule(rule)
    }

    /**
     * Ready-made rules for the situations people actually describe. Each one is
     * added as an ordinary editable rule, never as hidden behaviour.
     */
    private fun buildTemplates(): View = card { card ->
        val templates = listOf(
            Template(
                "No social during school hours",
                RuleTargetType.CATEGORY,
                AppCategory.SOCIAL.id,
                RuleConditionType.TIME,
                RuleAction.BLOCK,
                8 * 60,
                15 * 60
            ),
            Template(
                "Pause video after 30 minutes",
                RuleTargetType.CATEGORY,
                AppCategory.VIDEO.id,
                RuleConditionType.USAGE_OVER,
                RuleAction.FRICTION,
                0,
                0,
                30
            ),
            Template(
                "Nothing but essentials after the tenth open",
                RuleTargetType.CATEGORY,
                AppCategory.SOCIAL.id,
                RuleConditionType.OPENS_OVER,
                RuleAction.ALLOW_TEMP,
                0,
                0,
                10
            ),
            Template(
                "Games only outside a session",
                RuleTargetType.CATEGORY,
                AppCategory.GAMES.id,
                RuleConditionType.SESSION_ONLY,
                RuleAction.BLOCK
            )
        )

        templates.forEach { template ->
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    template.label,
                    "Adds an editable rule",
                    trailing = FocusUi.smallButton(this, tokens, "Add") { applyTemplate(template) }
                ) { applyTemplate(template) }
            )
        }
    }

    private data class Template(
        val label: String,
        val targetType: RuleTargetType,
        val targetValue: String,
        val conditionType: RuleConditionType,
        val action: RuleAction,
        val start: Int = 0,
        val end: Int = 0,
        val number: Int = 0
    )

    private fun applyTemplate(template: Template) {
        if (SessionLock.isFrozen(this)) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        val rule = RuleStore.newRule(
            label = template.label,
            targetType = template.targetType,
            targetValue = template.targetValue,
            conditionType = template.conditionType,
            action = template.action
        ).copy(
            conditionStart = if (template.start > 0) template.start else 9 * 60,
            conditionEnd = if (template.end > 0) template.end else 17 * 60,
            conditionNumber = if (template.number > 0) template.number else 30
        )
        RuleStore.add(this, rule)
        refresh()
        FocusDialog.toast(this, "Rule added. Tap it to adjust.")
    }

    // ── Editing ───────────────────────────────────────────────────

    private fun editRule(rule: Rule) {
        if (SessionLock.isFrozen(this)) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        var working = rule

        FocusDialog.custom(
            this,
            title = if (rule.label.isBlank()) "Rule" else rule.label,
            subtitle = "A target, a condition, an outcome.",
            confirmLabel = "Save",
            cancelLabel = "Cancel",
            onConfirm = {
                RuleStore.update(this, working)
                refresh()
            }
        ) { body, dialogTokens ->

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Name",
                    working.label.ifBlank { "Unnamed" },
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    FocusDialog.textInput(
                        this,
                        "Name this rule",
                        "So you recognise it in the list.",
                        "Name",
                        working.label
                    ) { value ->
                        working = working.copy(label = value)
                        RuleStore.update(this, working)
                        refresh()
                    }
                }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "WHAT IT COVERS"))
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    describeTarget(working),
                    "Tap to change",
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) { pickTarget(working) { updated -> working = updated } }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "WHEN"))
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    working.conditionType.label,
                    describeCondition(working),
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) { pickCondition(working) { updated -> working = updated } }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "WHAT HAPPENS"))
            RuleAction.values().forEach { action ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (action == working.action) "Now" else "Set",
                    if (action == working.action) dialogTokens.accent else dialogTokens.textMuted
                )
                body.addView(
                    FocusUi.listRow(this, dialogTokens, action.label, action.blurb, trailing = marker) {
                        working = working.copy(action = action)
                        RuleStore.update(this, working)
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(
                FocusUi.dangerButton(this, dialogTokens, "Delete this rule") {
                    RuleStore.remove(this, working.id)
                    refresh()
                }
            )
        }
    }

    private fun describeTarget(rule: Rule): String = when (rule.targetType) {
        RuleTargetType.ALL -> "Every app"
        RuleTargetType.APP -> AppCatalog.label(this, rule.targetValue)
        RuleTargetType.CATEGORY -> AppCategory.fromId(rule.targetValue).label + " apps"
    }

    private fun pickTarget(rule: Rule, onChange: (Rule) -> Unit) {
        val choices = listOf(FocusDialog.Choice("all", "Every app", "Covers everything installed")) +
            AppCategory.ruleTargets.map { category ->
                FocusDialog.Choice("cat:" + category.id, category.label + " apps", category.blurb)
            } +
            AppCatalog.launchable(this).map { app ->
                FocusDialog.Choice("app:" + app.packageName, app.label, app.category.label, app.packageName)
            }

        FocusDialog.singleChoice(this, "What does this cover?", null, choices, null) { key ->
            val updated = when {
                key == "all" -> rule.copy(targetType = RuleTargetType.ALL, targetValue = "")
                key.startsWith("cat:") ->
                    rule.copy(targetType = RuleTargetType.CATEGORY, targetValue = key.removePrefix("cat:"))
                else ->
                    rule.copy(targetType = RuleTargetType.APP, targetValue = key.removePrefix("app:"))
            }
            RuleStore.update(this, updated)
            onChange(updated)
            refresh()
        }
    }

    private fun pickCondition(rule: Rule, onChange: (Rule) -> Unit) {
        val choices = RuleConditionType.values().map { type ->
            FocusDialog.Choice(type.id, type.label, type.blurb)
        }

        FocusDialog.singleChoice(this, "When does it apply?", null, choices, rule.conditionType.id) { key ->
            val type = RuleConditionType.fromId(key)
            var updated = rule.copy(conditionType = type)
            RuleStore.update(this, updated)
            onChange(updated)

            when (type) {
                RuleConditionType.TIME -> FocusDialog.timePicker(this, "Starts at", rule.conditionStart) { start ->
                    FocusDialog.timePicker(this, "Ends at", rule.conditionEnd) { end ->
                        updated = updated.copy(conditionStart = start, conditionEnd = end)
                        RuleStore.update(this, updated)
                        onChange(updated)
                        refresh()
                    }
                }
                RuleConditionType.USAGE_OVER, RuleConditionType.OPENS_OVER -> FocusDialog.textInput(
                    this,
                    if (type == RuleConditionType.USAGE_OVER) "After how many minutes?" else "After how many opens?",
                    null,
                    "Number",
                    rule.conditionNumber.toString(),
                    numeric = true
                ) { value ->
                    updated = updated.copy(conditionNumber = value.toIntOrNull() ?: 30)
                    RuleStore.update(this, updated)
                    onChange(updated)
                    refresh()
                }
                RuleConditionType.PLACE -> {
                    val places = PlaceRules.all(this)
                    if (places.isEmpty()) {
                        FocusDialog.info(
                            this,
                            "No places saved",
                            "Add a place in Places and networks first, then come back to this rule."
                        )
                    } else {
                        FocusDialog.singleChoice(
                            this,
                            "Which place?",
                            null,
                            places.map { FocusDialog.Choice(it.id, it.label) },
                            rule.conditionValue
                        ) { placeId ->
                            updated = updated.copy(conditionValue = placeId)
                            RuleStore.update(this, updated)
                            onChange(updated)
                            refresh()
                        }
                    }
                }
                RuleConditionType.WIFI -> FocusDialog.textInput(
                    this,
                    "Which network?",
                    PlaceRules.currentWifiSsid(this)?.let { "Currently on " + it },
                    "Wi-Fi name",
                    rule.conditionValue.ifBlank { PlaceRules.currentWifiSsid(this).orEmpty() }
                ) { value ->
                    updated = updated.copy(conditionValue = value)
                    RuleStore.update(this, updated)
                    onChange(updated)
                    refresh()
                }
                RuleConditionType.DAYS -> pickDays(updated) { result ->
                    updated = result
                    onChange(updated)
                    refresh()
                }
                else -> refresh()
            }
        }
    }

    private fun pickDays(rule: Rule, onChange: (Rule) -> Unit) {
        val days = listOf(
            Calendar.MONDAY to "Monday",
            Calendar.TUESDAY to "Tuesday",
            Calendar.WEDNESDAY to "Wednesday",
            Calendar.THURSDAY to "Thursday",
            Calendar.FRIDAY to "Friday",
            Calendar.SATURDAY to "Saturday",
            Calendar.SUNDAY to "Sunday"
        )
        FocusDialog.multiChoice(
            this,
            "Which days?",
            "Leave everything off for every day.",
            days.map { FocusDialog.Choice(it.first.toString(), it.second) },
            rule.conditionDays.map { it.toString() }.toSet()
        ) { selected ->
            val updated = rule.copy(conditionDays = selected.mapNotNull { it.toIntOrNull() }.toSet())
            RuleStore.update(this, updated)
            onChange(updated)
        }
    }
}
