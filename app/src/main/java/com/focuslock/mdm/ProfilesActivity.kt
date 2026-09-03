package com.focuslock.mdm

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profiles: save a setup, switch between setups, move one to another phone.
 *
 * Importing always shows what is in the file first and never touches the theme
 * unless asked — a setup arriving from elsewhere should not silently repaint
 * someone's phone.
 */
class ProfilesActivity : FocusScreenActivity() {

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri: Uri = result.data?.data ?: return@registerForActivityResult
        if (ProfileIo.writeTo(this, uri)) {
            FocusDialog.toast(this, "Setup saved.")
        } else {
            FocusDialog.toast(this, "Could not write that file.")
        }
    }

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri: Uri = result.data?.data ?: return@registerForActivityResult
        val payload = ProfileIo.readFrom(this, uri)
        if (payload == null) {
            FocusDialog.toast(this, "That file is not a FocusLock setup.")
            return@registerForActivityResult
        }
        confirmRestore("this file", payload)
    }

    override fun screenTitle(): String = "Profiles"

    override fun screenSubtitle(): String =
        "One setup per situation. Exam week does not need the same phone as a holiday."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildCurrentCard())
        column.addView(sectionLabel("Saved setups"))
        column.addView(buildSavedList())
        column.addView(sectionLabel("Files"))
        column.addView(buildFileCard())
    }

    private fun buildCurrentCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "Right now"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(this, tokens, ProfileIo.describe(this, ProfileIo.snapshot(this)))
        )
        card.addView(FocusUi.spacer(this, 14))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Save this as a profile") {
                FocusDialog.textInput(
                    this,
                    title = "Name this setup",
                    subtitle = "Saving over an existing name replaces it.",
                    hint = "For example: exam week"
                ) { name ->
                    if (name.isBlank()) return@textInput
                    ProfileIo.save(this, name)
                    FocusDialog.toast(this, "Saved.")
                    refresh()
                }
            }
        )
    }

    private fun buildSavedList(): View = card { card ->
        val profiles = ProfileIo.saved(this)

        if (profiles.isEmpty()) {
            card.addView(
                FocusUi.emptyState(
                    this,
                    tokens,
                    "No saved profiles yet. Save the current setup above once it feels right."
                )
            )
            return@card
        }

        profiles.sortedByDescending { it.savedAtMs }.forEachIndexed { index, profile ->
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    profile.name,
                    "Saved " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                        .format(Date(profile.savedAtMs)),
                    trailing = FocusUi.smallButton(this, tokens, "Use") {
                        confirmRestore(profile.name, profile.payload)
                    }
                ) { openProfileSheet(profile) }
            )
            if (index < profiles.size - 1) card.addView(FocusUi.divider(this, tokens))
        }
    }

    private fun openProfileSheet(profile: ProfileIo.Profile) {
        FocusDialog.custom(
            this,
            title = profile.name,
            subtitle = ProfileIo.describe(this, profile.payload),
            confirmLabel = null,
            cancelLabel = "Close"
        ) { body, dialogTokens ->
            body.addView(
                FocusUi.primaryButton(this, dialogTokens, "Switch to this setup") {
                    confirmRestore(profile.name, profile.payload)
                }
            )
            body.addView(FocusUi.spacer(this, 10))
            body.addView(
                FocusUi.secondaryButton(this, dialogTokens, "Overwrite with current setup") {
                    ProfileIo.save(this, profile.name)
                    FocusDialog.toast(this, "Updated.")
                    refresh()
                }
            )
            body.addView(FocusUi.spacer(this, 10))
            body.addView(
                FocusUi.dangerButton(this, dialogTokens, "Delete this profile") {
                    ProfileIo.delete(this, profile.name)
                    refresh()
                }
            )
        }
    }

    /**
     * Restoring rewrites every rule the person has, so it states plainly what
     * will be replaced and offers to leave the look of the app alone.
     */
    private fun confirmRestore(name: String, payload: org.json.JSONObject) {
        var includeAppearance = false

        FocusDialog.custom(
            this,
            title = "Switch to " + name + "?",
            subtitle = ProfileIo.describe(this, payload),
            confirmLabel = "Switch",
            cancelLabel = "Cancel",
            onConfirm = {
                if (ProfileIo.restore(this, payload, includeAppearance)) {
                    FocusDialog.toast(this, "Setup applied.")
                } else {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        ) { body, dialogTokens ->
            body.addView(
                FocusUi.secondary(
                    this,
                    dialogTokens,
                    "This replaces your current app rules, keywords, custom rules, schedules, " +
                        "places, limits and capability switches. A running session is not affected."
                )
            )
            body.addView(FocusUi.spacer(this, 10))
            body.addView(
                FocusUi.toggleRow(
                    this,
                    dialogTokens,
                    "Also take its theme",
                    "Off by default, so an imported setup does not repaint your phone.",
                    false
                ) { value -> includeAppearance = value }
            )
        }
    }

    private fun buildFileCard(): View = card { card ->
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "A setup file is plain JSON: your rules and switches, nothing about you or this device. " +
                    "It never leaves the phone unless you send it."
            )
        )
        card.addView(FocusUi.spacer(this, 14))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Export to a file") {
                LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
                try {
                    exportPicker.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, ProfileIo.suggestedFileName())
                        }
                    )
                } catch (_: Exception) {
                    FocusDialog.toast(this, "No file picker available.")
                }
            }
        )
        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.secondaryButton(this, tokens, "Import from a file") {
                LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
                try {
                    importPicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    )
                } catch (_: Exception) {
                    FocusDialog.toast(this, "No file picker available.")
                }
            }
        )
    }
}
