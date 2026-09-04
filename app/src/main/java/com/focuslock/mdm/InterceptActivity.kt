package com.focuslock.mdm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * The screen a person actually meets when something is blocked.
 *
 * This is the emotional centre of the app, so it is built around three ideas:
 *
 *  - **Say why, kindly.** The headline names what happened without implying a
 *    failure, and the detail reminds them it was their own earlier decision.
 *  - **Never a dead end.** There is always somewhere to go: a calm alternative,
 *    a counted break, or simply back. A blocker that offers nothing gets
 *    uninstalled at the first hard moment.
 *  - **Pause, don't fight.** In soft and friction cases the way through stays
 *    open; the few seconds are the whole intervention.
 */
class InterceptActivity : AppCompatActivity() {

    private lateinit var tokens: UiPrefs.Tokens
    private val handler = Handler(Looper.getMainLooper())

    private var blockedPackage: String = ""
    private var headline: String = ""
    private var detail: String = ""
    private var source: String = ""
    private var phrase: String = ""
    private var isPause: Boolean = false
    private var offersBreak: Boolean = false
    private var offersEarnedMinutes: Boolean = false
    private var testMode: Boolean = false

    private var countdownSeconds = 0
    private var continueButton: TextView? = null
    private var countdownLabel: TextView? = null

    private val ticker = object : Runnable {
        override fun run() {
            countdownSeconds -= 1
            if (countdownSeconds <= 0) {
                countdownLabel?.visibility = View.GONE
                continueButton?.let {
                    it.isEnabled = true
                    it.alpha = 1f
                    Motion.fadeIn(it, tokens)
                }
                return
            }
            countdownLabel?.text = countdownSeconds.toString()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        headline = intent.getStringExtra(EXTRA_HEADLINE).orEmpty()
        detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        phrase = intent.getStringExtra(EXTRA_PHRASE).orEmpty()
        isPause = intent.getBooleanExtra(EXTRA_PAUSE, false)
        offersBreak = intent.getBooleanExtra(EXTRA_OFFERS_BREAK, false)
        offersEarnedMinutes = intent.getBooleanExtra(EXTRA_OFFERS_EARNED, false)
        testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false)

        // The content guard is the one source that names exactly what it saw -
        // the keyword rule that matched, not the surrounding on-screen text -
        // so the person is never left guessing what tripped it.
        if (headline.isBlank() && source == "contentGuard" && phrase.isNotBlank()) {
            headline = Copy.contentGuardHeadline(this, blockedPackage, phrase)
        }
        if (detail.isBlank() && source == "contentGuard" && phrase.isNotBlank()) {
            detail = Copy.contentGuardDetail(this, phrase)
        }
        if (headline.isBlank() && blockedPackage.isNotBlank()) {
            headline = Copy.blockHeadline(this, blockedPackage)
        }
        if (detail.isBlank() && blockedPackage.isNotBlank()) {
            detail = Copy.blockDetail(this, blockedPackage)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leave()
            }
        })

        render()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    // ── Layout ────────────────────────────────────────────────────

    private fun render() {
        tokens = UiPrefs.resolve(this)
        FocusUi.applySystemBars(window, tokens)

        val root = FocusUi.screenRoot(this, tokens)

        val content = FocusUi.column(this, tokens.density.contentPaddingDp + 6)
        content.gravity = Gravity.CENTER
        content.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        content.addView(buildMark())
        content.addView(FocusUi.spacer(this, 26))

        if (testMode) {
            val banner = FocusUi.pill(
                this,
                tokens,
                getString(R.string.intercept_testing_banner, TestMode.formatRemaining(this)),
                tokens.warning
            )
            banner.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = FocusUi.dp(this@InterceptActivity, 14) }
            content.addView(banner)
        }

        val headlineView = FocusUi.display(this, tokens, headline)
        headlineView.gravity = Gravity.CENTER
        content.addView(headlineView)

        if (detail.isNotBlank()) {
            val detailView = FocusUi.secondary(this, tokens, detail)
            detailView.gravity = Gravity.CENTER
            detailView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = FocusUi.dp(this@InterceptActivity, 12) }
            content.addView(detailView)
        }

        content.addView(FocusUi.spacer(this, 30))
        content.addView(buildActions())

        buildStillOpen()?.let {
            content.addView(FocusUi.spacer(this, 26))
            content.addView(it)
        }

        if (CapabilityRegistry.isEnabled(this, Capabilities.REPLACEMENT_SUGGESTIONS)) {
            content.addView(FocusUi.spacer(this, 26))
            content.addView(buildAlternatives())
        }

        root.addView(content)
        FocusUi.dimOverlay(this, tokens)?.let { root.addView(it) }
        setContentView(root)

        Motion.fadeIn(content, tokens)
    }

    /**
     * The app's own icon inside a soft ring, rather than a warning triangle.
     * Nothing here is an error, and the visual language should not say it is.
     */
    private fun buildMark(): View {
        val holder = FrameLayout(this)
        val size = FocusUi.dp(this, 108)
        holder.layoutParams = LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        holder.background = FocusUi.roundedShape(
            this,
            UiPrefs.withAlpha(tokens.accent, 26),
            54,
            UiPrefs.withAlpha(tokens.accent, 70)
        )

        val inner: View = if (blockedPackage.isNotBlank() && AppCatalog.icon(this, blockedPackage) != null) {
            FocusUi.appIcon(this, tokens, blockedPackage, 46)
        } else {
            val glyph = TextView(this)
            glyph.text = if (isPause) "…" else "•"
            glyph.setTextColor(tokens.accent)
            glyph.typeface = tokens.typeface
            glyph.textSize = tokens.scaled(34f)
            glyph
        }
        holder.addView(
            inner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )

        if (isPause) Motion.breathe(holder, tokens)
        return holder
    }

    private fun buildActions(): View {
        val column = FocusUi.column(this)

        if (isPause) {
            countdownSeconds = pauseSeconds()

            val label = FocusUi.display(this, tokens, countdownSeconds.toString())
            label.gravity = Gravity.CENTER
            label.setTextColor(tokens.textMuted)
            countdownLabel = label
            column.addView(label)
            column.addView(FocusUi.spacer(this, 14))

            val continueAnyway = FocusUi.secondaryButton(
                this,
                tokens,
                getString(R.string.intercept_open_anyway, appLabel())
            ) { openAnyway() }
            continueAnyway.isEnabled = false
            continueAnyway.alpha = 0.4f
            continueButton = continueAnyway

            column.addView(FocusUi.primaryButton(this, tokens, getString(R.string.intercept_not_now_go_back)) { leave() })
            column.addView(FocusUi.spacer(this, 10))
            column.addView(continueAnyway)

            if (testMode) {
                column.addView(FocusUi.spacer(this, 10))
                column.addView(FocusUi.ghostButton(this, tokens, getString(R.string.intercept_end_test_button)) { endTest() })
            }

            handler.postDelayed(ticker, 1_000L)
            return column
        }

        column.addView(FocusUi.primaryButton(this, tokens, getString(R.string.intercept_back_to_focuslock)) { leave() })

        if (testMode) {
            column.addView(FocusUi.spacer(this, 10))
            column.addView(FocusUi.ghostButton(this, tokens, getString(R.string.intercept_end_test_button)) { endTest() })
        }

        if (offersBreak && TakeABreak.canStart(this)) {
            val minutes = TakeABreak.breakMinutes(this)
            val left = TakeABreak.remainingToday(this)
            column.addView(FocusUi.spacer(this, 10))
            column.addView(
                FocusUi.secondaryButton(
                    this,
                    tokens,
                    getString(R.string.intercept_take_break_button, minutes, left)
                ) { startBreak() }
            )
        } else if (offersBreak) {
            column.addView(FocusUi.spacer(this, 12))
            val note = FocusUi.caption(this, tokens, Copy.breakUnavailable(this))
            note.gravity = Gravity.CENTER
            column.addView(note)
        }

        // Banked Earn minutes. Without this the block screen just says no to
        // someone who has already done the work to open this exact app, which
        // is the fastest way to make the whole economy feel like a lie.
        if (offersEarnedMinutes) {
            val balance = EarnBudget.balanceMinutes(this)
            if (balance > 0) {
                val spend = minOf(balance, DEFAULT_SPEND_MINUTES)
                column.addView(FocusUi.spacer(this, 10))
                column.addView(
                    FocusUi.secondaryButton(
                        this,
                        tokens,
                        getString(R.string.intercept_use_earned_button, spend, balance)
                    ) { confirmSpendEarned(spend) }
                )
            }
        }

        return column
    }

    /**
     * Spends banked minutes to open the thing that is currently blocked.
     *
     * Confirmed rather than instant: the minutes were the reward for real work,
     * and spending them by fat-fingering a button on a block screen would be a
     * bad trade the person never actually chose to make.
     */
    private fun confirmSpendEarned(minutes: Int) {
        FocusDialog.alert(
            this,
            title = getString(R.string.intercept_use_minutes_title, minutes),
            message = getString(R.string.intercept_use_minutes_message),
            confirmLabel = getString(R.string.intercept_use_them_button),
            cancelLabel = getString(R.string.intercept_keep_them_button),
            onConfirm = {
                if (EarnBudget.spend(this, minutes)) {
                    FocusDialog.toast(this, Copy.earnSpending(this, minutes))
                    openBlockedApp()
                } else {
                    FocusDialog.toast(this, getString(R.string.intercept_already_spent_toast))
                    leave()
                }
            }
        )
    }

    /**
     * Hands the person the app they just paid for.
     *
     * The spend window is open now, so the blocker will let this through on its
     * next tick; launching directly saves them tapping the icon again and
     * hitting a block screen that is already stale.
     */
    private fun openBlockedApp() {
        val launch = packageManager.getLaunchIntentForPackage(blockedPackage)
        if (launch == null) {
            leave()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
        } catch (_: Exception) {
            leave()
            return
        }
        finish()
    }

    private fun buildAlternatives(): View {
        val column = FocusUi.column(this)

        val label = FocusUi.caption(this, tokens, getString(R.string.intercept_or_do_this_instead))
        label.gravity = Gravity.CENTER
        label.letterSpacing = 0.08f
        column.addView(label)
        column.addView(FocusUi.spacer(this, 10))

        val strip = FocusUi.row(this)
        strip.gravity = Gravity.CENTER
        strip.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        availableAlternatives().forEach { pair ->
            strip.addView(FocusUi.chip(this, tokens, pair.first, false) { openAlternative(pair.second) })
        }

        if (strip.childCount == 0) return FocusUi.spacer(this, 0)

        val scroll = FocusUi.horizontalScroll(this, strip)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        column.addView(scroll)
        return column
    }

    /**
     * During a scheduled window or bedtime, the useful thing to show is not
     * "you cannot do this" but "here is what is still open". Otherwise a quiet
     * window looks like a broken phone.
     */
    private fun buildStillOpen(): View? {
        if (source != "schedule" && source != "scheduleOverlay" && source != "bedtime" && source != "place") return null

        val window = ScheduleManager.activeWindowIfEnabled(this)
        val open = (AppRules.alwaysAllowed(this) + window?.allowedApps.orEmpty())
            .filter { it != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it) != null }
            .distinct()
            .sortedBy { AppCatalog.label(this, it) }
            .take(8)

        if (open.isEmpty()) return null

        val column = FocusUi.column(this)
        val label = FocusUi.caption(this, tokens, getString(R.string.intercept_still_open))
        label.gravity = Gravity.CENTER
        label.letterSpacing = 0.08f
        column.addView(label)
        column.addView(FocusUi.spacer(this, 10))

        val strip = FocusUi.row(this)
        strip.gravity = Gravity.CENTER
        strip.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        open.forEach { packageName ->
            strip.addView(
                FocusUi.chip(this, tokens, AppCatalog.label(this, packageName), false) {
                    val launch = this.packageManager.getLaunchIntentForPackage(packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                        finish()
                    }
                }
            )
        }

        val scroll = FocusUi.horizontalScroll(this, strip)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        column.addView(scroll)
        return column
    }

    /** Only ever offers replacements the user has actually left switched on. */
    private fun availableAlternatives(): List<Pair<String, String>> =
        Seed.replacements.filter { pair ->
            when (pair.second) {
                "textSearch" -> CapabilityRegistry.isEnabled(this, Capabilities.TEXT_SEARCH)
                "safeBrowser" -> CapabilityRegistry.isEnabled(this, Capabilities.SAFE_BROWSER)
                "videoLibrary" -> CapabilityRegistry.isEnabled(this, Capabilities.VIDEO_LIBRARY)
                "analytics" -> CapabilityRegistry.isEnabled(this, Capabilities.ANALYTICS)
                else -> false
            }
        }

    // ── Actions ───────────────────────────────────────────────────

    private fun pauseSeconds(): Int =
        CapabilityRegistry.getIntParam(this, Capabilities.LAUNCH_FRICTION, "seconds", 8)
            .coerceIn(3, 60)

    private fun appLabel(): String =
        if (blockedPackage.isBlank()) getString(R.string.intercept_it_fallback) else AppCatalog.label(this, blockedPackage)

    /**
     * A pause is not a wall. Letting the person through after the countdown is
     * what keeps soft mode honest, so this grants a short pass unconditionally
     * (see [TakeABreak.grantFrictionPass]) rather than the budgeted, capability-
     * gated [TakeABreak.start] - that used to silently fail whenever Take a
     * Break was off or its daily allowance was spent, and the app would just
     * get re-intercepted the moment it opened, looping the pause screen.
     */
    private fun openAnyway() {
        if (blockedPackage.isNotBlank()) {
            TakeABreak.grantFrictionPass(this, blockedPackage)
            val launch = packageManager.getLaunchIntentForPackage(blockedPackage)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                finish()
                return
            }
        }
        finish()
    }

    private fun startBreak() {
        if (blockedPackage.isBlank()) {
            leave()
            return
        }
        if (!TakeABreak.start(this, blockedPackage)) {
            FocusDialog.toast(this, Copy.breakUnavailable(this))
            return
        }
        FocusDialog.toast(this, Copy.breakStarted(this, TakeABreak.breakMinutes(this)))
        val launch = packageManager.getLaunchIntentForPackage(blockedPackage)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    private fun openAlternative(key: String) {
        val intent = when (key) {
            "textSearch" -> Intent(this, TextSearchActivity::class.java)
            "safeBrowser" -> Intent(this, WebViewActivity::class.java)
            "videoLibrary" -> Intent(this, VideoLibraryActivity::class.java)
            "analytics" -> Intent(this, AnalyticsActivity::class.java)
            else -> null
        } ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun leave() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    /** Reachable from every test screen, exactly the "end it anytime" the test promised. */
    private fun endTest() {
        TestMode.end(this)
        leave()
    }

    companion object {
        const val EXTRA_PACKAGE = "intercept_package"
        const val EXTRA_HEADLINE = "intercept_headline"
        const val EXTRA_DETAIL = "intercept_detail"
        const val EXTRA_SOURCE = "intercept_source"
        const val EXTRA_PHRASE = "intercept_phrase"
        const val EXTRA_PAUSE = "intercept_pause"
        const val EXTRA_OFFERS_BREAK = "intercept_offers_break"
        const val EXTRA_OFFERS_EARNED = "intercept_offers_earned"
        const val EXTRA_TEST_MODE = "intercept_test_mode"

        /**
         * How much a single tap on the block screen spends.
         *
         * Small on purpose. Long enough to do the thing that was actually
         * wanted, short enough that it is not a whole evening bought in one
         * distracted moment. Anyone wanting more can spend again, which is a
         * second deliberate decision rather than one large accidental one.
         */
        private const val DEFAULT_SPEND_MINUTES = 10
    }
}
