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
 * The task panel.
 *
 * Comprehensive on purpose. A task app that only holds a title becomes a second,
 * worse place to keep todos and gets abandoned within a week, so this carries
 * the full field set people expect from Todoist or TickTick — notes, subtasks,
 * priority, a due date and a separate hard deadline, tags, recurrence, a time
 * estimate, local attachments, a reminder — plus the four fields Earn Mode adds:
 * what counts as done, which apps the task may use, what it pays, and whether it
 * should pay at all.
 *
 * Everything saves as you go. There is no "unsaved changes" state to lose.
 */
class TaskEditorActivity : FocusScreenActivity() {

    private var task: FocusTask = FocusTaskStore.newTask("")
    private var isNew = true

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri: Uri = result.data?.data ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some providers refuse a persistable grant; the reference still works this session.
        }
        val kind = if (contentResolver.getType(uri)?.startsWith("image/") == true) {
            AttachmentKind.PHOTO
        } else {
            AttachmentKind.FILE
        }
        mutate { it.copy(attachments = it.attachments + Attachment(kind, uri.toString(), fileLabel(uri))) }
    }

    override fun screenTitle(): String = if (isNew) "New task" else "Task"

    override fun screenSubtitle(): String? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val id = intent.getStringExtra(EXTRA_TASK_ID)
        if (id != null) {
            val existing = FocusTaskStore.find(this, id)
            if (existing != null) {
                task = existing
                isNew = false
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Re-read: a session or a subtask ticked elsewhere may have moved on.
        if (!isNew) FocusTaskStore.find(this, task.id)?.let { task = it }
    }

    /** Single write path, so nothing can drift between the screen and the store. */
    private fun mutate(change: (FocusTask) -> FocusTask) {
        task = change(task)
        if (isNew) {
            if (task.title.isBlank()) return
            FocusTaskStore.add(this, task)
            isNew = false
        } else {
            FocusTaskStore.update(this, task)
        }
        refresh()
    }

    override fun buildContent(column: LinearLayout) {
        column.addView(buildBasics())
        column.addView(sectionLabel("Steps"))
        column.addView(buildSubtasks())
        column.addView(sectionLabel("When"))
        column.addView(buildScheduling())
        column.addView(sectionLabel("Details"))
        column.addView(buildDetails())

        if (EarnMode.isEnabled(this)) {
            column.addView(sectionLabel("Earning"))
            column.addView(buildEarnCard())
        }

        column.addView(sectionLabel("Attachments"))
        column.addView(buildAttachments())

        if (!isNew) {
            column.addView(FocusUi.spacer(this, 10))
            column.addView(buildActions())
        }

        column.addView(FocusUi.spacer(this, 14))
        val footer = FocusUi.caption(this, tokens, Copy.onDeviceFooter(this))
        footer.gravity = android.view.Gravity.CENTER
        column.addView(footer)
    }

    // ── Basics ────────────────────────────────────────────────────

    private fun buildBasics(): View = card { card ->
        val titleField = FocusUi.input(this, tokens, "What is the task?", task.title)
        titleField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                task = task.copy(title = s?.toString().orEmpty())
            }
        })
        card.addView(titleField)

        val notesField = FocusUi.input(this, tokens, "Notes", task.notes, multiline = true)
        notesField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                task = task.copy(notes = s?.toString().orEmpty())
            }
        })
        card.addView(notesField)

        card.addView(
            FocusUi.secondaryButton(this, tokens, if (isNew) "Save the task" else "Save changes") {
                if (task.title.isBlank()) {
                    FocusDialog.toast(this, "It needs a title first.")
                } else {
                    mutate { it }
                    FocusDialog.toast(this, "Saved.")
                }
            }
        )
    }

    // ── Subtasks ──────────────────────────────────────────────────

    private fun buildSubtasks(): View = card { card ->
        if (task.subtasks.isEmpty()) {
            card.addView(
                FocusUi.emptyState(
                    this,
                    tokens,
                    "No steps. Add them if the task is big enough that starting is the hard part."
                )
            )
        } else {
            task.subtasks.forEach { subtask ->
                val toggle = FocusUi.switchControl(this, tokens, subtask.done) { done ->
                    mutate { current ->
                        current.copy(
                            subtasks = current.subtasks.map {
                                if (it.id == subtask.id) it.copy(done = done) else it
                            }
                        )
                    }
                }
                card.addView(
                    FocusUi.listRow(this, tokens, subtask.title, null, trailing = toggle) {
                        confirmRemoveSubtask(subtask)
                    }
                )
            }
            card.addView(
                FocusUi.meter(
                    this,
                    tokens,
                    task.subtasks.count { it.done }.toString() + " of " + task.subtasks.size,
                    task.progressPercent.toString() + "%",
                    task.progressPercent / 100f,
                    tokens.accent
                )
            )
        }

        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.smallButton(this, tokens, "Add a step") {
                FocusDialog.textInput(this, "Add a step", null, "What is the step?") { value ->
                    if (value.isNotBlank()) {
                        mutate { it.copy(subtasks = it.subtasks + FocusTaskStore.newSubtask(value)) }
                    }
                }
            }
        )
    }

    private fun confirmRemoveSubtask(subtask: Subtask) {
        FocusDialog.alert(
            this,
            title = subtask.title,
            message = "Remove this step?",
            confirmLabel = "Remove",
            cancelLabel = "Keep",
            destructive = true,
            onConfirm = {
                mutate { current -> current.copy(subtasks = current.subtasks.filterNot { it.id == subtask.id }) }
            }
        )
    }

    // ── When ──────────────────────────────────────────────────────

    /**
     * Due date and deadline are separate on purpose. "I meant to do this today"
     * and "after this it is pointless" are different facts, and only the second
     * should ever mark something missed.
     */
    private fun buildScheduling(): View = card { card ->
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Planned for",
                task.dueDate?.let { formatWhen(it) } ?: "No date",
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.dateTimePicker(this, "Planned for", task.dueDate) { value ->
                    mutate { it.copy(dueDate = value) }
                }
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Hard deadline",
                task.deadline?.let { formatWhen(it) } ?: "None",
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.dateTimePicker(this, "Hard deadline", task.deadline) { value ->
                    mutate { it.copy(deadline = value) }
                }
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Reminder",
                task.reminderAt?.let { formatWhen(it) } ?: "None",
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.dateTimePicker(this, "Remind me", task.reminderAt) { value ->
                    mutate { it.copy(reminderAt = value) }
                }
            }
        )
        card.addView(FocusUi.divider(this, tokens))

        card.addView(FocusUi.caption(this, tokens, "REPEATS"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                Recurrence.values().map { it.label },
                Recurrence.values().indexOf(task.recurrence)
            ) { index ->
                mutate { it.copy(recurrence = Recurrence.values()[index]) }
            }
        )
        if (task.recurrence == Recurrence.CUSTOM) {
            card.addView(
                FocusUi.sliderRow(
                    this,
                    tokens,
                    "Every",
                    2,
                    30,
                    task.recurrenceEveryDays,
                    { it.toString() + " days" }
                ) { value -> task = task.copy(recurrenceEveryDays = value) }
            )
            card.addView(
                FocusUi.smallButton(this, tokens, "Save interval") { mutate { it } }
            )
        }
    }

    private fun formatWhen(ms: Long): String =
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

    // ── Details ───────────────────────────────────────────────────

    private fun buildDetails(): View = card { card ->
        card.addView(FocusUi.caption(this, tokens, "PRIORITY"))
        card.addView(
            FocusUi.chipStrip(
                this,
                tokens,
                Priority.values().map { it.label },
                Priority.values().indexOf(task.priority)
            ) { index ->
                mutate { it.copy(priority = Priority.values()[index]) }
            }
        )

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "Time estimate",
                0,
                240,
                task.timeEstimateMin ?: 0,
                { if (it == 0) "Not estimated" else it.toString() + " min" }
            ) { value -> task = task.copy(timeEstimateMin = value.takeIf { it > 0 }) }
        )
        card.addView(FocusUi.smallButton(this, tokens, "Save estimate") { mutate { it } })

        card.addView(FocusUi.divider(this, tokens, 8))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Tags",
                if (task.tags.isEmpty()) "None" else task.tags.joinToString(" "),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.textInput(
                    this,
                    "Tags",
                    "Separated by spaces or commas.",
                    "study essay",
                    task.tags.joinToString(" ")
                ) { value ->
                    val tags = value.split(",", " ")
                        .map { it.trim().removePrefix("#") }
                        .filter { it.isNotBlank() }
                        .toSet()
                    mutate { it.copy(tags = tags) }
                }
            }
        )
    }

    // ── Earning ───────────────────────────────────────────────────

    private fun buildEarnCard(): View = card { card ->
        card.addView(FocusUi.caption(this, tokens, "WHAT COUNTS AS DONE"))
        Verification.values().forEach { verification ->
            val available = verification != Verification.PHOTO ||
                (EarnMode.photoProofEnabled(this) && PhotoProof.isCaptureAvailable(this))
            val marker = FocusUi.pill(
                this,
                tokens,
                when {
                    verification == task.verification -> "Now"
                    !available -> "Off"
                    else -> "Set"
                },
                if (verification == task.verification) tokens.accent else tokens.textMuted
            )
            val row = FocusUi.listRow(
                this,
                tokens,
                verification.label,
                verification.blurb,
                trailing = marker
            ) {
                if (available) {
                    mutate { it.copy(verification = verification) }
                } else {
                    FocusDialog.info(
                        this,
                        "Photo proof is unavailable",
                        if (!EarnMode.photoProofEnabled(this)) {
                            "You have photo proof switched off in your deal settings."
                        } else {
                            "This phone has no camera app that FocusLock can call."
                        }
                    )
                }
            }
            row.alpha = if (available) 1f else 0.5f
            card.addView(row)
        }

        if (task.verification == Verification.PHOTO) {
            card.addView(FocusUi.spacer(this, 6))
            card.addView(FocusUi.caption(this, tokens, PhotoProof.describeChecks(this)))
        }
        if (task.verification == Verification.TIMER && task.timeEstimateMin == null) {
            card.addView(FocusUi.spacer(this, 6))
            val note = FocusUi.caption(
                this,
                tokens,
                "No time estimate set, so this will use 25 minutes."
            )
            note.setTextColor(tokens.warning)
            card.addView(note)
        }
        if (task.verification == Verification.SUBTASKS_ALL && task.subtasks.isEmpty()) {
            card.addView(FocusUi.spacer(this, 6))
            val note = FocusUi.caption(
                this,
                tokens,
                "No steps yet, so there is nothing to tick. Add some above."
            )
            note.setTextColor(tokens.warning)
            card.addView(note)
        }

        card.addView(FocusUi.divider(this, tokens, 10))

        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Apps this task may use",
                describeAllowedApps(),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                pickApps(
                    title = "Open during this task",
                    subtitle = "Leave everything off to lock down to FocusLock alone.",
                    selected = task.allowedApps
                ) { selected -> mutate { it.copy(allowedApps = selected) } }
            }
        )

        card.addView(FocusUi.divider(this, tokens, 8))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "I actually enjoy this one",
                "Then it earns nothing. Paying yourself for work you already like tends to make it " +
                    "feel like a chore instead.",
                task.enjoyable
            ) { value -> mutate { it.copy(enjoyable = value) } }
        )

        if (!task.enjoyable) {
            card.addView(
                FocusUi.sliderRow(
                    this,
                    tokens,
                    "Minutes this task pays",
                    0,
                    120,
                    task.rewardMin ?: 0,
                    { if (it == 0) "Use my hourly rate" else it.toString() + " min" }
                ) { value -> task = task.copy(rewardMin = value.takeIf { it > 0 }) }
            )
            card.addView(FocusUi.smallButton(this, tokens, "Save reward") { mutate { it } })
            card.addView(FocusUi.spacer(this, 6))
            card.addView(FocusUi.caption(this, tokens, EarnMode.describeDeal(this)))
        }

        if (EarnMode.showsCredibility(this) && task.verification == Verification.PHOTO) {
            card.addView(FocusUi.divider(this, tokens, 8))
            card.addView(
                FocusUi.meter(
                    this,
                    tokens,
                    "Verification trust",
                    (task.credibility * 100).toInt().toString() + "%",
                    task.credibility,
                    if (task.credibility < 0.6f) tokens.warning else tokens.success
                )
            )
        }
    }

    private fun describeAllowedApps(): String {
        if (task.allowedApps.isEmpty()) return "FocusLock only"
        // Previewing, so ask what would happen if this task started right now.
        val rejected = EarnSession.rejectedPackages(this, task, !SessionManager.isActive(this))
        val base = task.allowedApps.joinToString { AppCatalog.label(this, it) }
        if (rejected.isEmpty()) return base
        return base + " — " + rejected.size + " not on your standing allowlist"
    }

    // ── Attachments ───────────────────────────────────────────────

    private fun buildAttachments(): View = card { card ->
        if (task.attachments.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, "Nothing attached."))
        } else {
            task.attachments.forEach { attachment ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        attachment.label.ifBlank { attachment.value },
                        attachment.kind.id,
                        trailing = FocusUi.smallButton(this, tokens, "Remove") {
                            mutate { current ->
                                current.copy(attachments = current.attachments.filterNot { it == attachment })
                            }
                        }
                    ) { openAttachment(attachment) }
                )
            }
        }

        card.addView(FocusUi.spacer(this, 10))
        val row = FocusUi.row(this)
        row.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        row.addView(FocusUi.smallButton(this, tokens, "Add a file") { pickFile("*/*") })
        row.addView(FocusUi.smallButton(this, tokens, "Add an image") { pickFile("image/*") })
        row.addView(FocusUi.smallButton(this, tokens, "Add a link") { addLink() })
        card.addView(FocusUi.horizontalScroll(this, row))
    }

    private fun pickFile(mime: String) {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
        try {
            filePicker.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
            )
        } catch (_: Exception) {
            FocusDialog.toast(this, "No file picker available.")
        }
    }

    private fun addLink() {
        FocusDialog.textInput(this, "Add a link", null, "https://example.com") { value ->
            if (value.isNotBlank()) {
                val normalized = AllowlistStore.normalizeUrl(value)
                mutate {
                    it.copy(attachments = it.attachments + Attachment(AttachmentKind.LINK, normalized, normalized))
                }
            }
        }
    }

    /**
     * A link opens in the safe browser rather than anywhere else, so a task
     * attachment can never become a route around the allowlist.
     */
    private fun openAttachment(attachment: Attachment) {
        when (attachment.kind) {
            AttachmentKind.LINK -> {
                startActivity(Intent(this, WebViewActivity::class.java))
                FocusDialog.toast(this, "Open it from your site list if it is allowed there.")
            }
            else -> {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(attachment.value), contentResolver.getType(Uri.parse(attachment.value)))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                } catch (_: Exception) {
                    FocusDialog.toast(this, "Nothing on this phone can open that.")
                }
            }
        }
    }

    private fun fileLabel(uri: Uri): String = try {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment"
    } catch (_: Exception) {
        "Attachment"
    }

    // ── Actions ───────────────────────────────────────────────────

    private fun buildActions(): View = card { card ->
        if (EarnMode.isEnabled(this) && !task.completed) {
            card.addView(
                FocusUi.primaryButton(this, tokens, "Start this task") {
                    val standalone = !SessionManager.isActive(this)
                    EarnSession.start(this, task, standalone)
                    startActivity(Intent(this, EarnSessionActivity::class.java))
                    finish()
                }
            )
            card.addView(FocusUi.spacer(this, 8))
        }

        if (!task.completed) {
            card.addView(
                FocusUi.secondaryButton(this, tokens, "Mark it done without earning") {
                    FocusDialog.alert(
                        this,
                        title = "Mark done?",
                        message = "No minutes for this one. Sometimes a task is just finished.",
                        confirmLabel = "Mark done",
                        cancelLabel = "Cancel",
                        onConfirm = {
                            FocusTaskStore.complete(this, task)
                            Streaks.recordActivity(this)
                            finish()
                        }
                    )
                }
            )
            card.addView(FocusUi.spacer(this, 8))
        }

        card.addView(
            FocusUi.dangerButton(this, tokens, "Delete this task") {
                FocusDialog.alert(
                    this,
                    title = "Delete " + task.title + "?",
                    message = "It goes for good. Attachments stay where they are on the phone.",
                    confirmLabel = "Delete",
                    cancelLabel = "Keep",
                    destructive = true,
                    onConfirm = {
                        FocusTaskStore.remove(this, task.id)
                        finish()
                    }
                )
            }
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
