package com.focuslock.mdm

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * The base every settings screen sits on.
 *
 * It owns the parts that must never differ between screens: resolving tokens,
 * painting the system bars, the scrolling column, the page header, and
 * re-rendering on resume so a theme change made two screens away is already
 * applied when you come back. Subclasses only describe their content.
 */
abstract class FocusScreenActivity : AppCompatActivity() {

    protected lateinit var tokens: UiPrefs.Tokens
    protected lateinit var content: LinearLayout

    /** Shown in the page header. */
    protected abstract fun screenTitle(): String

    protected open fun screenSubtitle(): String? = null

    /** Fill the scrolling column. Called on create and on every resume. */
    protected abstract fun buildContent(column: LinearLayout)

    /** onResume always follows onCreate; without this the screen builds twice on launch. */
    private var freshlyCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freshlyCreated = true
        renderScreen()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        if (freshlyCreated) {
            freshlyCreated = false
            return
        }
        renderScreen()
    }

    protected fun renderScreen() {
        tokens = UiPrefs.resolve(this)
        FocusUi.applySystemBars(window, tokens)

        val root = FocusUi.screenRoot(this, tokens)

        content = FocusUi.column(this, tokens.density.contentPaddingDp)
        content.addView(
            FocusUi.pageHeader(this, tokens, screenTitle(), screenSubtitle()) { finish() }
        )
        buildContent(content)
        content.addView(FocusUi.spacer(this, 28))

        val scroll = FocusUi.scroll(this, content)
        scroll.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(scroll)
        FocusUi.dimOverlay(this, tokens)?.let { root.addView(it) }
        setContentView(root)

        Motion.fadeIn(content, tokens)
    }

    /** Redraw after a change, keeping the scroll position honest enough. */
    protected fun refresh() {
        renderScreen()
    }

    protected fun card(build: (LinearLayout) -> Unit): View {
        val view = FocusUi.card(this, tokens)
        build(view)
        return view
    }

    protected fun sectionLabel(text: String): View = FocusUi.sectionLabel(this, tokens, text)

    /** Every picker in the app funnels through here so they all behave alike. */
    protected fun pickApps(
        title: String,
        subtitle: String?,
        selected: Set<String>,
        includeSystem: Boolean = false,
        onSave: (Set<String>) -> Unit
    ) {
        val apps = if (includeSystem) AppCatalog.all(this) else AppCatalog.launchable(this)
        val choices = apps.map { app ->
            FocusDialog.Choice(
                key = app.packageName,
                label = app.label,
                subtitle = app.category.label,
                leadingPackage = app.packageName
            )
        }
        FocusDialog.multiChoice(this, title, subtitle, choices, selected) { result ->
            onSave(result)
            refresh()
        }
    }
}
