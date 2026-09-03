package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * The keyword guard editor.
 *
 * The old WhatsApp guard was five phrases compiled into the app. Here the same
 * five are ordinary rows the user can read, edit or delete, sitting next to the
 * Shorts, Reels and adult guards and any words of their own.
 *
 * The point of a content guard is that it is surgical: it keeps the app and
 * removes the surface, so nobody has to choose between "no messenger" and "the
 * messenger's infinite video tab".
 */
class KeywordGuardActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Keyword guard"

    override fun screenSubtitle(): String =
        "Words that make FocusLock back out of a screen, inside apps you still want."

    override fun buildContent(column: LinearLayout) {
        if (!SetupChecks.isContentGuardEnabled(this)) {
            column.addView(buildPermissionCard())
        }

        column.addView(buildMasterCard())
        column.addView(sectionLabel("Built-in guards"))
        column.addView(buildGroupCard(KeywordRules.GROUP_WHATSAPP, Capabilities.WHATSAPP_GUARD, "WhatsApp"))
        column.addView(buildGroupCard(KeywordRules.GROUP_SHORTS, Capabilities.SHORTS_BLOCK, "Shorts"))
        column.addView(buildGroupCard(KeywordRules.GROUP_REELS, Capabilities.REELS_BLOCK, "Reels"))
        column.addView(buildGroupCard(KeywordRules.GROUP_ADULT, Capabilities.ADULT_BLOCK, "Adult content"))

        column.addView(sectionLabel("Telegram"))
        column.addView(buildTelegramCard())

        column.addView(sectionLabel("Your words"))
        column.addView(buildUserRules())

        column.addView(sectionLabel("Exceptions"))
        column.addView(buildExceptions())
    }

    private fun buildPermissionCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "The guard is not switched on"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Reading the screen needs an accessibility service. FocusLock only ever reads text; " +
                    "it never types, taps or sends anything anywhere."
            )
        )
        card.addView(FocusUi.spacer(this, 12))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Open Accessibility settings") {
                LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    FocusDialog.toast(this, "That page is not available on this phone.")
                }
            }
        )
    }

    private fun buildMasterCard(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Content guard",
                "The master switch. With this off, none of the guards below run.",
                CapabilityRegistry.isEnabled(this, Capabilities.CONTENT_GUARD)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.CONTENT_GUARD, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "My own keywords",
                "Runs the words you add below.",
                CapabilityRegistry.isEnabled(this, Capabilities.KEYWORD_BLOCK)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.KEYWORD_BLOCK, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
    }

    private fun buildGroupCard(group: String, capabilityId: String, label: String): View = card { card ->
        val rules = KeywordRules.rulesInGroup(this, group)
        val enabled = CapabilityRegistry.isEnabled(this, capabilityId)

        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                label,
                rules.size.toString() + " phrase" + (if (rules.size == 1) "" else "s") + " watched",
                enabled
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, capabilityId, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        if (!enabled) return@card

        rules.forEach { rule ->
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    rule.phrase,
                    describeScope(rule) + " - " + rule.action.label.lowercase(),
                    trailing = FocusUi.smallButton(this, tokens, "Edit") { editRule(rule) }
                ) { editRule(rule) }
            )
        }

        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.smallButton(this, tokens, "Add a phrase to " + label) {
                addRule(group)
            }
        )
    }

    private fun buildUserRules(): View = card { card ->
        val rules = KeywordRules.userRules(this)

        if (rules.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, Copy.emptyKeywords(this)))
        } else {
            rules.forEachIndexed { index, rule ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        rule.phrase,
                        describeScope(rule) + " - " + rule.action.label.lowercase(),
                        trailing = FocusUi.switchControl(this, tokens, rule.enabled) { value ->
                            KeywordRules.update(this, rule.copy(enabled = value))
                        }
                    ) { editRule(rule) }
                )
                if (index < rules.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.primaryButton(this, tokens, "Add a word or phrase") { addRule(null) })
    }

    private fun describeScope(rule: KeywordRule): String =
        if (rule.appliesEverywhere) {
            "Every app"
        } else if (rule.packages.size == 1) {
            AppCatalog.label(this, rule.packages.first())
        } else {
            rule.packages.size.toString() + " apps"
        }

    // ── Editing ───────────────────────────────────────────────────

    private fun addRule(group: String?) {
        FocusDialog.textInput(
            this,
            title = "Watch for a phrase",
            subtitle = "Case does not matter. Short, distinctive phrases work best: a common word " +
                "will fire on screens you did not mean.",
            hint = "For example: for you",
            confirmLabel = "Next"
        ) { phrase ->
            if (phrase.isBlank()) return@textInput
            val rule = KeywordRules.newRule(phrase = phrase, group = group)
            KeywordRules.add(this, rule)
            refresh()
            editRule(rule)
        }
    }

    private fun editRule(rule: KeywordRule) {
        var working = rule

        FocusDialog.custom(
            this,
            title = rule.phrase,
            subtitle = "Where it applies and what happens when it is seen.",
            confirmLabel = "Save",
            cancelLabel = "Cancel",
            onConfirm = {
                KeywordRules.update(this, working)
                refresh()
            }
        ) { body, dialogTokens ->
            body.addView(FocusUi.caption(this, dialogTokens, "WHERE"))
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    if (working.appliesEverywhere) "Every app" else describeScope(working),
                    "Tap to choose which apps this watches",
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    pickApps(
                        title = "Watch " + working.phrase + " in",
                        subtitle = "Leave everything unticked to watch every app.",
                        selected = working.packages
                    ) { selected ->
                        working = working.copy(packages = selected)
                        KeywordRules.update(this, working)
                    }
                }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "WHAT HAPPENS"))
            GuardAction.values().forEach { action ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (action == working.action) "Now" else "Set",
                    if (action == working.action) dialogTokens.accent else dialogTokens.textMuted
                )
                body.addView(
                    FocusUi.listRow(this, dialogTokens, action.label, action.blurb, trailing = marker) {
                        working = working.copy(action = action)
                        KeywordRules.update(this, working)
                        FocusDialog.toast(this, action.label)
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(
                FocusUi.dangerButton(this, dialogTokens, "Delete this phrase") {
                    KeywordRules.remove(this, working.id)
                    refresh()
                }
            )
        }
    }

    /**
     * Telegram, by allowlist.
     *
     * The rest of this screen names things to keep out. This one names the
     * handful to let in, because listing every channel worth avoiding is a
     * losing game and listing the three chats that matter is a minute's work.
     */
    private fun buildTelegramCard(): View = card { card ->
        val on = CapabilityRegistry.isEnabled(this, Capabilities.TELEGRAM_GUARD)
        val frozen = SessionLock.isFrozen(this)

        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Only my named chats",
                "Telegram stays open for the chats you list. Anything else backs out.",
                on,
                enabled = !frozen
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.TELEGRAM_GUARD, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        if (!on) return@card

        val allowed = TelegramGuard.allowedRaw(this)

        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                if (allowed.isEmpty()) {
                    "Nothing listed yet, so nothing is blocked. Add a chat and everything else " +
                        "in Telegram starts backing out."
                } else {
                    "Everything except these backs out."
                }
            )
        )

        allowed.forEach { title ->
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    title,
                    null,
                    trailing = FocusUi.smallButton(this, tokens, "Remove") {
                        if (!TelegramGuard.removeAllowed(this, title)) {
                            FocusDialog.toast(this, SessionLock.refusalMessage(this))
                        }
                        refresh()
                    }
                )
            )
        }

        card.addView(FocusUi.spacer(this, 10))

        // Offering the chat they were just in beats asking them to type a name
        // exactly, and it doubles as proof the guard can still read Telegram
        // at all after an update.
        val seen = TelegramGuard.lastSeenTitle(this)
        if (seen != null && allowed.none { it.equals(seen, ignoreCase = true) }) {
            card.addView(
                FocusUi.secondaryButton(this, tokens, "Allow \"" + seen + "\"") {
                    if (!TelegramGuard.addAllowed(this, seen)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            )
            card.addView(FocusUi.spacer(this, 8))
        }

        card.addView(
            FocusUi.secondaryButton(this, tokens, "Add a chat by name") {
                FocusDialog.textInput(
                    this,
                    title = "Allow a Telegram chat",
                    subtitle = "Type the chat's name as Telegram shows it at the top of the screen.",
                    hint = "Study group"
                ) { value ->
                    if (!TelegramGuard.addAllowed(this, value)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            }
        )

        if (seen == null) {
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.caption(
                    this,
                    tokens,
                    "FocusLock has not managed to read a chat title yet. Open a Telegram chat " +
                        "and come back — if this line stays, a Telegram update has probably " +
                        "moved things and this guard is not running."
                )
            )
        }
    }

    private fun buildExceptions(): View = card { card ->
        val exceptions = KeywordRules.exceptions(this)

        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Text that looks like a match but is not. The WhatsApp search bar mentions Meta AI, " +
                    "for instance, and should not trip the guard."
            )
        )
        card.addView(FocusUi.spacer(this, 8))

        if (exceptions.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, "No exceptions."))
        } else {
            exceptions.forEach { phrase ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        phrase,
                        null,
                        trailing = FocusUi.smallButton(this, tokens, "Remove") {
                            KeywordRules.setExceptions(this, exceptions - phrase)
                            refresh()
                        }
                    )
                )
            }
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.smallButton(this, tokens, "Add an exception") {
                FocusDialog.textInput(
                    this,
                    title = "Never treat this as a match",
                    subtitle = null,
                    hint = "Phrase"
                ) { phrase ->
                    if (phrase.isNotBlank()) {
                        KeywordRules.setExceptions(this, exceptions + phrase)
                        refresh()
                    }
                }
            }
        )
    }
}
