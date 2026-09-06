package com.focuslock.mdm

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * The base for a full-screen "edit in place" editor - schedules, tasks, and
 * anything else that used to be a [FocusDialog.custom] sheet long enough to
 * make its own Save button hard to reach.
 *
 * The difference from [FocusScreenActivity] is exactly one thing: the header
 * (close + title + Save) sits outside the scrolling column instead of inside
 * it, so Save stays on screen no matter how long the body runs. That was the
 * actual bug behind "there's no Save button" - a dialog's bottom sheet had no
 * such fixed region, so its action row could scroll (or overflow) out of
 * reach entirely. See the design doc's "input panels" section.
 */
abstract class FocusEditorActivity : AppCompatActivity() {

    protected lateinit var tokens: UiPrefs.Tokens

    /** Shown in the persistent header. */
    protected abstract fun editorTitle(): String

    /** Fill the scrolling column below the header. Called on create and on every resume. */
    protected abstract fun buildContent(column: LinearLayout)

    /** Invoked when Save is tapped. Only reachable when [canSave] is true. */
    protected abstract fun onSave()

    /** Grays out and disables Save - e.g. a blank required title - instead of saving something incomplete. */
    protected open fun canSave(): Boolean = true

    protected open fun saveLabel(): String = getString(R.string.common_save)

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

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        outer.addView(
            FocusUi.editorHeader(
                this,
                tokens,
                editorTitle(),
                saveLabel(),
                canSave(),
                onClose = { finish() },
                onSave = { onSave() }
            )
        )
        outer.addView(FocusUi.divider(this, tokens))

        val content = FocusUi.column(this, tokens.density.contentPaddingDp)
        buildContent(content)
        content.addView(FocusUi.spacer(this, 28))

        val scroll = FocusUi.scroll(this, content)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        outer.addView(scroll)

        root.addView(outer)
        FocusUi.dimOverlay(this, tokens)?.let { root.addView(it) }
        setContentView(root)

        Motion.fadeIn(content, tokens)
    }

    /** Redraw after a change - rebuilds the whole screen, header included, so a title/canSave change shows immediately. */
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
