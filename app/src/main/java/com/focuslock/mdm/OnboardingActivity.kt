package com.focuslock.mdm

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * The setup quiz.
 *
 * Six taps, skippable at every step, and it never applies anything on its own:
 * the last screen shows the whole proposed setup and waits. That matters more
 * than it sounds — an app that configures itself from a quiz has taken the
 * decision away from the person, which is exactly the failure mode the
 * Capability Registry exists to prevent.
 *
 * Skipping is a first-class path, not a punishment: it lands on a sane default
 * setup that is no worse than the old hardcoded one.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var tokens: UiPrefs.Tokens
    private lateinit var host: FrameLayout

    private var step = 0
    private var rerun = false

    private val goals = LinkedHashSet<String>()
    private val pulls = LinkedHashSet<String>()
    private val hardTimes = LinkedHashSet<String>()
    private var strictness = 1
    private val essentials = LinkedHashSet<String>()
    private val chosenDistractions = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Migration.run(this)
        rerun = intent.getBooleanExtra(EXTRA_RERUN, false)

        essentials.addAll(AppCatalog.detectEssentials(this))
        chosenDistractions.addAll(UsageAnalytics.suggestedDistractions(this, 10))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) {
                    step -= 1
                    render()
                } else if (rerun) {
                    finish()
                }
            }
        })

        render()
    }

    // ── Shell ─────────────────────────────────────────────────────

    private fun render() {
        tokens = UiPrefs.resolve(this)
        FocusUi.applySystemBars(window, tokens)

        val root = FocusUi.screenRoot(this, tokens)
        host = FrameLayout(this)
        host.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val column = FocusUi.column(this, tokens.density.contentPaddingDp)
        column.addView(buildProgress())

        when (step) {
            0 -> buildWelcome(column)
            1 -> buildGoals(column)
            2 -> buildPulls(column)
            3 -> buildStrictness(column)
            4 -> buildHardTimes(column)
            5 -> buildEssentials(column)
            else -> buildReview(column)
        }

        val scroll = FocusUi.scroll(this, column)
        host.addView(scroll)
        root.addView(host)
        setContentView(root)

        Motion.fadeIn(column, tokens)
    }

    private fun buildProgress(): View {
        val container = FocusUi.column(this)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = FocusUi.dp(this@OnboardingActivity, 20) }

        val row = FocusUi.row(this)
        for (i in 0..TOTAL_STEPS) {
            val segment = View(this)
            segment.background = FocusUi.roundedShape(
                this,
                if (i <= step) tokens.accent else tokens.track,
                3
            )
            segment.layoutParams = LinearLayout.LayoutParams(0, FocusUi.dp(this, 4), 1f).apply {
                marginEnd = FocusUi.dp(this@OnboardingActivity, 5)
            }
            row.addView(segment)
        }
        container.addView(row)

        if (step in 1 until TOTAL_STEPS) {
            val skip = FocusUi.ghostButton(this, tokens, "Skip the questions") { skipToDefaults() }
            skip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                FocusUi.dp(this, tokens.density.quickButtonHeightDp)
            ).apply { topMargin = FocusUi.dp(this@OnboardingActivity, 12) }
            container.addView(skip)
        }
        return container
    }

    private fun next() {
        step += 1
        render()
    }

    // ── Steps ─────────────────────────────────────────────────────

    private fun buildWelcome(column: LinearLayout) {
        column.addView(FocusUi.spacer(this, 24))
        column.addView(FocusUi.display(this, tokens, "FocusLock"))
        column.addView(FocusUi.spacer(this, 14))
        column.addView(
            FocusUi.body(
                this,
                tokens,
                "This is a phone you lock yourself, not one someone else locks for you. " +
                    "Nothing here reports to anybody, and everything it does is a switch you can turn off."
            )
        )
        column.addView(FocusUi.spacer(this, 16))
        column.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Six quick questions and it will suggest a starting setup. You will see the whole " +
                    "thing before anything is applied."
            )
        )
        column.addView(FocusUi.spacer(this, 28))
        column.addView(FocusUi.primaryButton(this, tokens, "Start") { next() })
        column.addView(FocusUi.spacer(this, 10))
        column.addView(
            FocusUi.secondaryButton(this, tokens, "Skip and use sensible defaults") { skipToDefaults() }
        )
    }

    private fun buildGoals(column: LinearLayout) {
        question(
            column,
            "What is this for?",
            "Pick as many as fit.",
            listOf(
                "study" to "Studying and exams",
                "work" to "Work and deep focus",
                "sleep" to "Sleeping properly",
                "earn" to "Unlocking the phone by finishing work",
                "less" to "Just less phone, generally"
            ),
            goals
        )
    }

    private fun buildPulls(column: LinearLayout) {
        question(
            column,
            "What pulls hardest?",
            "Be honest. This decides what gets suggested for blocking.",
            listOf(
                "shorts" to "Short video: Shorts, Reels, TikTok",
                "social" to "Social feeds",
                "games" to "Games",
                "browsing" to "Browsing and shopping",
                "messaging" to "Messaging that never ends"
            ),
            pulls
        )
    }

    private fun buildStrictness(column: LinearLayout) {
        column.addView(FocusUi.title(this, tokens, "How firm should it start?"))
        column.addView(FocusUi.spacer(this, 8))
        column.addView(
            FocusUi.secondary(
                this,
                tokens,
                "You can change this any time. Starting gentler and tightening later works better " +
                    "than starting at maximum and abandoning it."
            )
        )
        column.addView(FocusUi.spacer(this, 18))

        val options = listOf(
            Triple("Gentle", "A pause and a nudge. Nothing is held shut.", 0),
            Triple("Firm", "Distractions stop opening during a session.", 1),
            Triple("All the way", "Kiosk sessions available, escape routes closed.", 2)
        )

        options.forEach { option ->
            val selected = strictness == option.third
            val card = FocusUi.card(this, tokens, elevated = selected) {
                strictness = option.third
                render()
            }
            if (selected) {
                card.background = FocusUi.roundedShape(this, tokens.surfaceAlt, tokens.radiusDp, tokens.accent, 2)
            }
            card.addView(FocusUi.heading(this, tokens, option.first))
            card.addView(FocusUi.spacer(this, 6))
            card.addView(FocusUi.secondary(this, tokens, option.second))
            column.addView(card)
        }

        column.addView(FocusUi.spacer(this, 12))
        column.addView(FocusUi.primaryButton(this, tokens, "Continue") { next() })
    }

    private fun buildHardTimes(column: LinearLayout) {
        question(
            column,
            "When is it hardest?",
            "This suggests schedule windows and a bedtime.",
            listOf(
                "morning" to "First thing in the morning",
                "school" to "School or work hours",
                "evening" to "Evenings",
                "night" to "Late at night"
            ),
            hardTimes
        )
    }

    private fun buildEssentials(column: LinearLayout) {
        column.addView(FocusUi.title(this, tokens, "What must never be blocked?"))
        column.addView(FocusUi.spacer(this, 8))
        column.addView(
            FocusUi.secondary(
                this,
                tokens,
                "These stay open through everything, including a kiosk session. " +
                    "Calls and maps belong here."
            )
        )
        column.addView(FocusUi.spacer(this, 16))

        val card = FocusUi.card(this, tokens)
        val candidates = (AppCatalog.detectEssentials(this) + essentials).distinct()
        candidates.forEach { packageName ->
            card.addView(
                FocusUi.toggleRow(
                    this,
                    tokens,
                    AppCatalog.label(this, packageName),
                    packageName,
                    packageName in essentials
                ) { checked ->
                    if (checked) essentials.add(packageName) else essentials.remove(packageName)
                }
            )
        }
        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.smallButton(this, tokens, "Add more apps") {
                val choices = AppCatalog.launchable(this).map { app ->
                    FocusDialog.Choice(app.packageName, app.label, app.category.label, app.packageName)
                }
                FocusDialog.multiChoice(
                    this,
                    "Never block these",
                    null,
                    choices,
                    essentials
                ) { selected ->
                    essentials.clear()
                    essentials.addAll(selected)
                    render()
                }
            }
        )
        column.addView(card)

        column.addView(FocusUi.spacer(this, 12))
        column.addView(FocusUi.primaryButton(this, tokens, "Continue") { next() })
    }

    private fun question(
        column: LinearLayout,
        title: String,
        subtitle: String,
        options: List<Pair<String, String>>,
        selection: MutableSet<String>
    ) {
        column.addView(FocusUi.title(this, tokens, title))
        column.addView(FocusUi.spacer(this, 8))
        column.addView(FocusUi.secondary(this, tokens, subtitle))
        column.addView(FocusUi.spacer(this, 18))

        val card = FocusUi.card(this, tokens)
        options.forEach { option ->
            card.addView(
                FocusUi.toggleRow(
                    this,
                    tokens,
                    option.second,
                    null,
                    option.first in selection
                ) { checked ->
                    if (checked) selection.add(option.first) else selection.remove(option.first)
                }
            )
        }
        column.addView(card)

        column.addView(FocusUi.spacer(this, 12))
        column.addView(FocusUi.primaryButton(this, tokens, "Continue") { next() })
    }

    // ── Review ────────────────────────────────────────────────────

    private fun buildReview(column: LinearLayout) {
        val plan = buildPlan()

        column.addView(FocusUi.title(this, tokens, "Here is the setup"))
        column.addView(FocusUi.spacer(this, 8))
        column.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Nothing has been applied yet. Change anything now, or after: every part of this " +
                    "is an ordinary setting."
            )
        )
        column.addView(FocusUi.spacer(this, 18))

        val modeCard = FocusUi.card(this, tokens)
        modeCard.addView(FocusUi.caption(this, tokens, "STARTING MODE"))
        modeCard.addView(FocusUi.spacer(this, 6))
        modeCard.addView(FocusUi.heading(this, tokens, plan.mode.label))
        modeCard.addView(FocusUi.spacer(this, 4))
        modeCard.addView(FocusUi.secondary(this, tokens, plan.mode.oneLiner))
        modeCard.addView(FocusUi.spacer(this, 4))
        modeCard.addView(FocusUi.caption(this, tokens, plan.mode.exitLine))
        column.addView(modeCard)

        val blockCard = FocusUi.card(this, tokens)
        blockCard.addView(FocusUi.caption(this, tokens, "SUGGESTED FOR BLOCKING"))
        blockCard.addView(FocusUi.spacer(this, 6))
        if (plan.block.isEmpty()) {
            blockCard.addView(FocusUi.secondary(this, tokens, "Nothing, based on your answers."))
        } else {
            plan.block.forEach { packageName ->
                blockCard.addView(
                    FocusUi.toggleRow(
                        this,
                        tokens,
                        AppCatalog.label(this, packageName),
                        AppCatalog.categoryOf(this, packageName).label,
                        packageName in chosenDistractions
                    ) { checked ->
                        if (checked) chosenDistractions.add(packageName) else chosenDistractions.remove(packageName)
                    }
                )
            }
        }
        column.addView(blockCard)

        val capabilityCard = FocusUi.card(this, tokens)
        capabilityCard.addView(FocusUi.caption(this, tokens, "TURNED ON"))
        capabilityCard.addView(FocusUi.spacer(this, 6))
        plan.enable.forEach { id ->
            Capabilities.spec(id)?.let { spec ->
                capabilityCard.addView(
                    FocusUi.listRow(this, tokens, spec.label, spec.blurb)
                )
            }
        }
        column.addView(capabilityCard)

        if (plan.notes.isNotEmpty()) {
            val notesCard = FocusUi.card(this, tokens)
            notesCard.addView(FocusUi.caption(this, tokens, "ALSO SET UP"))
            notesCard.addView(FocusUi.spacer(this, 6))
            plan.notes.forEach { note ->
                notesCard.addView(FocusUi.listRow(this, tokens, note.first, note.second))
            }
            column.addView(notesCard)
        }

        val essentialsCard = FocusUi.card(this, tokens)
        essentialsCard.addView(FocusUi.caption(this, tokens, "NEVER BLOCKED"))
        essentialsCard.addView(FocusUi.spacer(this, 6))
        essentialsCard.addView(
            FocusUi.secondary(
                this,
                tokens,
                if (essentials.isEmpty()) {
                    "Nothing chosen. You can add essentials later in Rules."
                } else {
                    essentials.joinToString { AppCatalog.label(this, it) }
                }
            )
        )
        column.addView(essentialsCard)

        column.addView(FocusUi.spacer(this, 14))
        column.addView(FocusUi.primaryButton(this, tokens, "Use this setup") { apply(plan) })
        column.addView(FocusUi.spacer(this, 10))
        column.addView(
            FocusUi.secondaryButton(this, tokens, "Start over") {
                step = 1
                render()
            }
        )
        column.addView(FocusUi.spacer(this, 10))
        column.addView(
            FocusUi.ghostButton(this, tokens, "Use plain defaults instead") { skipToDefaults() }
        )
        column.addView(FocusUi.spacer(this, 24))
    }

    // ── The recommendation ────────────────────────────────────────

    private data class Plan(
        val mode: FocusMode,
        val enable: List<String>,
        val disable: List<String>,
        val block: List<String>,
        val notes: List<Pair<String, String>>
    )

    /**
     * Turns the answers into a proposal.
     *
     * The mapping is intentionally conservative: strictness sets the ceiling,
     * the pulls decide what gets suggested for blocking, and the hard times
     * decide whether a schedule or bedtime is offered. Anything not clearly
     * implied by an answer is left alone rather than guessed at.
     */
    private fun buildPlan(): Plan {
        val enable = LinkedHashSet<String>()
        val disable = LinkedHashSet<String>()
        val notes = ArrayList<Pair<String, String>>()

        enable.add(Capabilities.ALWAYS_ALLOWED)
        enable.add(Capabilities.SELF_COMPASSION_COPY)
        enable.add(Capabilities.TAKE_A_BREAK)
        enable.add(Capabilities.REPLACEMENT_SUGGESTIONS)
        enable.add(Capabilities.SAFE_BROWSER)
        enable.add(Capabilities.TEXT_SEARCH)

        val mode = when (strictness) {
            0 -> {
                enable.add(Capabilities.ADVISORY_MODE)
                enable.add(Capabilities.LAUNCH_FRICTION)
                disable.add(Capabilities.SUSPEND_BLOCKED_APPS)
                disable.add(Capabilities.HIDE_BLOCKED_APPS)
                FocusMode.SOFT
            }
            2 -> {
                enable.add(Capabilities.KIOSK_MODE)
                enable.add(Capabilities.APP_BLOCK)
                enable.add(Capabilities.WEB_BLOCK)
                enable.add(Capabilities.SUSPEND_BLOCKED_APPS)
                enable.add(Capabilities.SAFE_BOOT_BLOCK)
                enable.add(Capabilities.UNINSTALL_PROTECTION)
                enable.add(Capabilities.PERSISTENT_HOME)
                notes.add(
                    "Kiosk needs a computer" to
                        "Device Owner is set over ADB. Until then, kiosk falls back to Sanctuary."
                )
                FocusMode.KIOSK
            }
            else -> {
                enable.add(Capabilities.APP_BLOCK)
                enable.add(Capabilities.SANCTUARY_MODE)
                enable.add(Capabilities.LAUNCH_FRICTION)
                enable.add(Capabilities.SUSPEND_BLOCKED_APPS)
                FocusMode.BLOCK
            }
        }

        if ("shorts" in pulls) {
            enable.add(Capabilities.CONTENT_GUARD)
            enable.add(Capabilities.SHORTS_BLOCK)
            enable.add(Capabilities.REELS_BLOCK)
            notes.add("Shorts and Reels guards" to "Closes those surfaces without blocking the whole app.")
        }
        if ("social" in pulls) {
            enable.add(Capabilities.CONTENT_GUARD)
            enable.add(Capabilities.NOTIFICATION_BLOCK)
        }
        if ("messaging" in pulls) {
            enable.add(Capabilities.CONTENT_GUARD)
            enable.add(Capabilities.WHATSAPP_GUARD)
            notes.add("WhatsApp guard" to "Chats keep working; Channels, Updates and Meta AI close themselves.")
        }
        if ("browsing" in pulls) {
            enable.add(Capabilities.WEB_BLOCK)
            enable.add(Capabilities.ADULT_BLOCK)
        }
        if ("games" in pulls) {
            enable.add(Capabilities.PER_APP_LIMITS)
            notes.add("A daily budget for games" to "60 minutes a day, adjustable in Rules.")
        }

        if ("night" in hardTimes || "sleep" in goals) {
            enable.add(Capabilities.BEDTIME_MODE)
            notes.add("Bedtime" to "22:00 to 06:00, social and video quiet, screen dimmed.")
        }
        if ("school" in hardTimes) {
            enable.add(Capabilities.SCHEDULES)
            notes.add("A school-hours window" to "08:00 to 15:00 on weekdays, essentials still open.")
        }
        if ("morning" in hardTimes) {
            enable.add(Capabilities.SCHEDULES)
            notes.add("A morning window" to "07:00 to 09:00 every day.")
        }
        if ("evening" in hardTimes) {
            enable.add(Capabilities.SCHEDULES)
            notes.add("An evening window" to "19:00 to 22:00 every day.")
        }

        if ("study" in goals || "work" in goals) {
            enable.add(Capabilities.ANALYTICS)
            enable.add(Capabilities.STREAKS)
        }

        // Earn Mode is only ever proposed because the person asked for exactly
        // this, never inferred from a general wish to use the phone less. The
        // research is clear enough about the downside that it should not be a
        // default anybody drifts into.
        if ("earn" in goals) {
            enable.add(Capabilities.EARN_MODE)
            enable.add(Capabilities.ANALYTICS)
            notes.add(
                "Earn mode" to "Finish a task, unlock leisure minutes. Mark work you already " +
                    "enjoy as enjoyable and it will pay nothing, on purpose."
            )
        }

        val suggestions = UsageAnalytics.suggestedDistractions(this, 12)
            .filterNot { it in essentials }
            .filter { packageName ->
                val category = AppCatalog.categoryOf(this, packageName)
                when {
                    pulls.isEmpty() -> true
                    "shorts" in pulls && category == AppCategory.VIDEO -> true
                    "social" in pulls && category == AppCategory.SOCIAL -> true
                    "games" in pulls && category == AppCategory.GAMES -> true
                    "browsing" in pulls && category == AppCategory.BROWSING -> true
                    "messaging" in pulls && category == AppCategory.MESSAGING -> false
                    else -> false
                }
            }

        return Plan(mode, enable.toList(), disable.toList(), suggestions, notes)
    }

    private fun apply(plan: Plan) {
        // Belt-and-braces: the rerun entry point in You -> Advanced already
        // refuses to open this screen while frozen, but the underlying write
        // is what actually has to hold, in case this is ever reached another
        // way.
        if (SessionLock.isFrozen(this)) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        val values = HashMap<String, Boolean>()
        plan.enable.forEach { values[it] = true }
        plan.disable.forEach { values[it] = false }
        CapabilityRegistry.applySet(this, values)

        AppRules.setAlwaysAllowed(this, essentials)

        val toBlock = chosenDistractions.filterNot { it in essentials }
        if (toBlock.isNotEmpty()) {
            AppRules.setPolicies(this, toBlock, AppPolicy.BLOCK)
        }

        if ("games" in pulls) {
            AppCatalog.packagesInCategory(this, AppCategory.GAMES)
                .filterNot { it in essentials }
                .forEach { AppLimits.setMinuteLimit(this, it, 60) }
        }

        if (CapabilityRegistry.isEnabled(this, Capabilities.BEDTIME_MODE)) {
            Bedtime.setWindow(this, 22 * 60, 6 * 60)
            Bedtime.setBlockedCategories(
                this,
                listOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES)
            )
        }

        seedSchedules()
        finishOnboarding()
    }

    private fun seedSchedules() {
        if (!CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES)) return
        val existing = ScheduleManager.getSchedules(this).toMutableList()

        if ("school" in hardTimes) {
            existing.add(
                ScheduleManager.newSchedule(
                    startMinutes = 8 * 60,
                    endMinutes = 15 * 60,
                    repeat = RepeatType.WEEKLY,
                    daysOfWeek = listOf(
                        java.util.Calendar.MONDAY,
                        java.util.Calendar.TUESDAY,
                        java.util.Calendar.WEDNESDAY,
                        java.util.Calendar.THURSDAY,
                        java.util.Calendar.FRIDAY
                    ),
                    dayOfMonth = 0,
                    message = "School hours",
                    allowedApps = essentials
                )
            )
        }
        if ("morning" in hardTimes) {
            existing.add(
                ScheduleManager.newSchedule(
                    startMinutes = 7 * 60,
                    endMinutes = 9 * 60,
                    repeat = RepeatType.DAILY,
                    daysOfWeek = emptyList(),
                    dayOfMonth = 0,
                    message = "Morning, before the phone starts",
                    allowedApps = essentials
                )
            )
        }
        if ("evening" in hardTimes) {
            existing.add(
                ScheduleManager.newSchedule(
                    startMinutes = 19 * 60,
                    endMinutes = 22 * 60,
                    repeat = RepeatType.DAILY,
                    daysOfWeek = emptyList(),
                    dayOfMonth = 0,
                    message = "Evening",
                    allowedApps = essentials
                )
            )
        }
        ScheduleManager.saveSchedules(this, existing)
    }

    /**
     * The skip path still produces a working phone: the same defaults the specs
     * declare, plus detected essentials protected. Skipping must never mean
     * "unconfigured".
     */
    private fun skipToDefaults() {
        CapabilityRegistry.markSeeded(this)
        if (AppRules.alwaysAllowedRaw(this).isEmpty()) {
            AppRules.setAlwaysAllowed(this, AppCatalog.detectEssentials(this))
        }
        finishOnboarding()
    }

    private fun finishOnboarding() {
        FocusStore.setBool(this, Constants.KEY_ONBOARDING_DONE, true)
        PolicySync.request(this, "onboarding")
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_RERUN = "onboarding_rerun"
        private const val TOTAL_STEPS = 6
    }
}
