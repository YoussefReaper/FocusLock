package com.focuslock.mdm

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * The shell.
 *
 * Four tabs, always labelled, always in the same order: **Focus** is what is
 * happening now, **Library** is what to do instead, **Rules** is everything the
 * user owns, **You** is the app itself. That split is the whole information
 * architecture, and it replaced a single 640-line scrolling dashboard where
 * starting a session and factory-resetting the phone were the same distance
 * from the top.
 *
 * Tabs are plain view builders rather than fragments: the screens are simple,
 * and this keeps state, theming and refresh in one obvious place.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tokens: UiPrefs.Tokens
    private lateinit var contentHost: FrameLayout
    private lateinit var navBar: LinearLayout

    private var currentTab = TAB_FOCUS
    private val tabs = HashMap<Int, FocusTab>()
    private val navButtons = ArrayList<NavButton>()

    /** Set when onCreate bounced to onboarding, so onResume does not build a dead shell. */
    private var handingOffToOnboarding = false

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The last lock-task state we acted on, so the ticker can notice a change
     * without asking the framework twice a second.
     */
    private var lastLockTaskWanted: Boolean? = null

    private val ticker = object : Runnable {
        override fun run() {
            // A session can end while this screen is already resumed — the timer
            // runs out, or the person taps End. onResume will not fire again to
            // release the pin, and while pinned they cannot easily go elsewhere
            // to trigger one. So watch for the transition here and release it.
            val wanted = SessionManager.shouldLockTask(this@MainActivity)
            if (lastLockTaskWanted != wanted) {
                lastLockTaskWanted = wanted
                KioskPolicy.syncLockTaskState(this@MainActivity)
            }
            tabs[currentTab]?.onTick()
            handler.postDelayed(this, 1_000L)
        }
    }

    private data class NavButton(
        val root: LinearLayout,
        val iconHolder: FrameLayout,
        val icon: ImageView,
        val label: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Migration.run(this)
        LockManager.initLock(this)

        if (!FocusStore.getBool(this, Constants.KEY_ONBOARDING_DONE, false)) {
            handingOffToOnboarding = true
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Back must not fall out of the app while a hard session is running.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != TAB_FOCUS) {
                    selectTab(TAB_FOCUS)
                } else if (!SessionManager.shouldLockTask(this@MainActivity)) {
                    moveTaskToBack(true)
                }
            }
        })

        currentTab = intent.getIntExtra(EXTRA_TAB, TAB_FOCUS)
        buildShell()
    }

    override fun onResume() {
        super.onResume()
        if (handingOffToOnboarding || isFinishing) return
        applyPolicyOnResume()
        rebuildIfThemeChanged()
        tabs[currentTab]?.onShow()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra(EXTRA_TAB, -1)
        if (tab >= 0) selectTab(tab)
    }

    // ── Policy ────────────────────────────────────────────────────

    private fun applyPolicyOnResume() {
        lastLockTaskWanted = SessionManager.shouldLockTask(this)
        KioskPolicy.syncLockTaskState(this)
        PolicySync.request(this, "mainResume")
        // The service decides for itself whether there is anything to watch and
        // stops immediately if there is not, so this can be unconditional.
        AppBlockerService.start(this)
    }

    // ── Shell ─────────────────────────────────────────────────────

    private var lastThemeSignature = ""

    private fun themeSignature(): String = listOf(
        UiPrefs.getTheme(this).id,
        UiPrefs.getAccent(this).id,
        UiPrefs.getBackground(this).id,
        UiPrefs.getFont(this).id,
        UiPrefs.getDensity(this).id,
        UiPrefs.getCardRadiusDp(this).toString(),
        UiPrefs.getTextScale(this).toString(),
        UiPrefs.highContrast(this).toString(),
        Bedtime.isActive(this).toString()
    ).joinToString("|")

    private fun rebuildIfThemeChanged() {
        val signature = themeSignature()
        if (signature == lastThemeSignature) return
        buildShell()
    }

    private fun buildShell() {
        tokens = UiPrefs.resolve(this)
        lastThemeSignature = themeSignature()
        tabs.clear()
        navButtons.clear()
        FocusUi.applySystemBars(window, tokens, tokens.surface)

        val root = FocusUi.screenRoot(this, tokens)

        val shell = LinearLayout(this)
        shell.orientation = LinearLayout.VERTICAL
        shell.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        contentHost = FrameLayout(this)
        contentHost.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        shell.addView(contentHost)

        navBar = buildNavBar()
        shell.addView(navBar)

        root.addView(shell)
        FocusUi.dimOverlay(this, tokens)?.let { root.addView(it) }
        setContentView(root)

        selectTab(currentTab, animate = false)
    }

    /**
     * A hand-built bottom bar rather than `BottomNavigationView`.
     *
     * The stock component takes its colours from theme attributes, which is
     * exactly the coupling this refactor removed: here the active state is the
     * user's accent, the labels never collapse, and every target is a full
     * 56dp regardless of text scale.
     */
    private fun buildNavBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(tokens.surface)

        val topLine = View(this)
        topLine.setBackgroundColor(tokens.divider)
        bar.addView(
            topLine,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FocusUi.dp(this, 1))
        )

        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.setPadding(0, FocusUi.dp(this, 6), 0, FocusUi.dp(this, 8))

        TAB_SPECS.forEachIndexed { index, spec ->
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.minimumHeight = FocusUi.dp(this, 56)
            item.isClickable = true
            item.isFocusable = true
            item.background = FocusUi.withRipple(
                this,
                FocusUi.roundedShape(this, UiPrefs.withAlpha(tokens.surface, 0), 14),
                tokens
            )
            item.setOnClickListener { selectTab(index) }
            item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            item.contentDescription = spec.label

            // The active indicator is a filled pill behind the icon rather than a
            // line under the label: it survives any text scale the user picks.
            val iconHolder = FrameLayout(this)
            iconHolder.layoutParams = LinearLayout.LayoutParams(
                FocusUi.dp(this, 58),
                FocusUi.dp(this, 30)
            )

            val icon = ImageView(this)
            icon.setImageResource(spec.iconRes)
            icon.layoutParams = FrameLayout.LayoutParams(
                FocusUi.dp(this, 21),
                FocusUi.dp(this, 21)
            ).apply { gravity = Gravity.CENTER }
            iconHolder.addView(icon)

            val label = TextView(this)
            label.text = spec.label
            label.gravity = Gravity.CENTER
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(11.5f))
            label.typeface = tokens.typeface
            label.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = FocusUi.dp(this@MainActivity, 3) }

            item.addView(iconHolder)
            item.addView(label)
            strip.addView(item)
            navButtons.add(NavButton(item, iconHolder, icon, label))
        }

        bar.addView(strip)
        bar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return bar
    }

    private fun updateNavState() {
        navButtons.forEachIndexed { index, button ->
            val active = index == currentTab
            val color = if (active) tokens.accent else tokens.textMuted

            button.icon.imageTintList = android.content.res.ColorStateList.valueOf(color)
            button.iconHolder.background = if (active) {
                FocusUi.roundedShape(this, UiPrefs.withAlpha(tokens.accent, 38), 15)
            } else {
                null
            }

            button.label.setTextColor(color)
            button.label.typeface = if (active) {
                Typeface.create(tokens.typeface, Typeface.BOLD)
            } else {
                tokens.typeface
            }
        }
    }

    /**
     * Kiosk can take over the whole screen, but only because the user asked for
     * it: hiding the nav also hides the safe browser and the library, and a
     * locked phone with nothing to do on it is what drives people to break out.
     */
    private fun shouldHideNav(): Boolean =
        SessionManager.shouldLockTask(this) &&
            CapabilityRegistry.getBoolParam(this, Capabilities.KIOSK_MODE, "fullScreenSurface", false)

    fun selectTab(index: Int, animate: Boolean = true) {
        val safeIndex = index.coerceIn(0, TAB_SPECS.size - 1)
        currentTab = safeIndex

        val tab = tabs.getOrPut(safeIndex) { createTab(safeIndex) }
        val view = tab.view

        if (view.parent == null) contentHost.addView(view)
        val outgoing = (0 until contentHost.childCount)
            .map { contentHost.getChildAt(it) }
            .firstOrNull { it != view && it.visibility == View.VISIBLE }

        if (animate) {
            Motion.crossfade(outgoing, view, tokens)
        } else {
            outgoing?.visibility = View.GONE
            view.visibility = View.VISIBLE
        }

        tab.onShow()
        updateNavState()
        navBar.visibility = if (shouldHideNav()) View.GONE else View.VISIBLE
    }

    private fun createTab(index: Int): FocusTab = when (index) {
        TAB_TASKS -> TasksTab(this, tokens)
        TAB_LIBRARY -> LibraryTab(this, tokens)
        TAB_RULES -> RulesTab(this, tokens)
        TAB_YOU -> YouTab(this, tokens)
        else -> FocusDashboardTab(this, tokens)
    }

    /** Lets a tab ask the shell to redraw after a change that alters the theme. */
    fun requestShellRebuild() {
        buildShell()
    }

    private data class TabSpec(val label: String, val iconRes: Int)

    companion object {
        const val EXTRA_TAB = "focuslock_tab"
        const val TAB_FOCUS = 0
        const val TAB_TASKS = 1
        const val TAB_LIBRARY = 2
        const val TAB_RULES = 3
        const val TAB_YOU = 4

        // Tasks sits second because it is the other thing you *do*, next to
        // starting a session. Library, Rules and You are all places you go to
        // look something up or change something.
        private val TAB_SPECS = listOf(
            TabSpec("Focus", R.drawable.ic_tab_focus),
            TabSpec("Tasks", R.drawable.ic_tab_tasks),
            TabSpec("Library", R.drawable.ic_tab_library),
            TabSpec("Rules", R.drawable.ic_tab_rules),
            TabSpec("You", R.drawable.ic_tab_you)
        )

        fun open(context: android.content.Context, tab: Int) {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_TAB, tab)
                }
            )
        }
    }
}

/**
 * A tab owns its view and knows how to refresh it. Nothing more.
 */
abstract class FocusTab(protected val activity: MainActivity, protected val tokens: UiPrefs.Tokens) {

    val view: View by lazy { build() }

    protected abstract fun build(): View

    /** Called every time the tab becomes visible. */
    open fun onShow() = Unit

    /** Called once a second while this tab is the visible one. */
    open fun onTick() = Unit
}
