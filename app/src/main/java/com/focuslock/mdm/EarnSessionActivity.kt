package com.focuslock.mdm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * The screen you sit on while a task is running.
 *
 * It shows one task, one clock, and exactly one way to finish it — whichever the
 * task asked for. No progress bars filling with points, no streak in the corner,
 * nothing to optimise except the work.
 *
 * Stopping early is a plain button with no penalty attached. A reward system
 * that punishes quitting is a reward system people stop opening.
 */
class EarnSessionActivity : FocusScreenActivity() {

    private var task: FocusTask? = null
    private var pendingProof: File? = null

    private var timerView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            timerView?.text = SessionManager.formatCountdown(EarnSession.elapsedMs(this@EarnSessionActivity))
            handler.postDelayed(this, 1_000L)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val file = pendingProof
        pendingProof = null
        if (file == null) return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            PhotoProof.discard(file)
            return@registerForActivityResult
        }
        handleProof(file)
    }

    override fun screenTitle(): String = task?.title ?: "No task running"

    override fun screenSubtitle(): String? = task?.notes?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        task = EarnSession.activeTask(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        task = EarnSession.activeTask(this)
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun buildContent(column: LinearLayout) {
        val current = task
        if (current == null) {
            column.addView(buildNoSessionCard())
            return
        }

        column.addView(buildClockCard(current))
        column.addView(sectionLabel("Finishing"))
        column.addView(buildVerificationCard(current))

        if (EarnMode.showsBudgetWhileActive(this)) {
            column.addView(sectionLabel("Banked"))
            column.addView(buildBudgetCard())
        }

        column.addView(sectionLabel("Open right now"))
        column.addView(buildAllowedApps(current))

        column.addView(FocusUi.spacer(this, 10))
        column.addView(
            FocusUi.ghostButton(this, tokens, "Stop without finishing") { confirmStop(current) }
        )

        column.addView(FocusUi.spacer(this, 14))
        val footer = FocusUi.caption(this, tokens, Copy.onDeviceFooter(this))
        footer.gravity = Gravity.CENTER
        column.addView(footer)
    }

    private fun buildNoSessionCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "Nothing is running"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(this, tokens, "Pick a task from the Tasks tab and start it there.")
        )
        card.addView(FocusUi.spacer(this, 12))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Back to tasks") {
                MainActivity.open(this, MainActivity.TAB_TASKS)
                finish()
            }
        )
    }

    // ── Clock ─────────────────────────────────────────────────────

    private fun buildClockCard(current: FocusTask): View = card { card ->
        val elapsed = FocusUi.display(
            this,
            tokens,
            SessionManager.formatCountdown(EarnSession.elapsedMs(this))
        )
        elapsed.gravity = Gravity.CENTER
        timerView = elapsed
        card.addView(elapsed)

        val caption = FocusUi.caption(this, tokens, "on this task")
        caption.gravity = Gravity.CENTER
        card.addView(caption)

        if (current.verification == Verification.TIMER) {
            val target = EarnSession.timerTargetMinutes(this, current)
            val done = EarnSession.elapsedMinutes(this)
            card.addView(FocusUi.spacer(this, 12))
            card.addView(
                FocusUi.meter(
                    this,
                    tokens,
                    done.toString() + " of " + target + " minutes",
                    if (done >= target) "Ready" else (target - done).toString() + " to go",
                    done.toFloat() / target.toFloat(),
                    if (done >= target) tokens.success else tokens.accent
                )
            )
        }

        val mode = if (EarnSession.isStandalone(this)) {
            if (EarnMode.hasHardEnforcement(this)) {
                "The phone is locked to this task."
            } else {
                "Running as friction: distracting apps get intercepted, not held shut."
            }
        } else {
            "Narrowing your " + SessionManager.mode(this).label.lowercase() + " session."
        }
        card.addView(FocusUi.spacer(this, 10))
        val note = FocusUi.caption(this, tokens, mode)
        note.gravity = Gravity.CENTER
        card.addView(note)
    }

    // ── Verification ──────────────────────────────────────────────

    private fun buildVerificationCard(current: FocusTask): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, current.verification.label))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(FocusUi.secondary(this, tokens, current.verification.blurb))
        card.addView(FocusUi.spacer(this, 14))

        when (current.verification) {
            Verification.MANUAL -> card.addView(
                FocusUi.primaryButton(this, tokens, "I have done it") { completeTask(current) }
            )

            Verification.TIMER -> {
                val ready = EarnSession.timerSatisfied(this, current)
                val button = FocusUi.primaryButton(
                    this,
                    tokens,
                    if (ready) "Finish and collect" else "Keep going"
                ) {
                    if (ready) completeTask(current) else refresh()
                }
                button.isEnabled = ready
                button.alpha = if (ready) 1f else 0.5f
                card.addView(button)

                if (!ready) {
                    card.addView(FocusUi.spacer(this, 8))
                    card.addView(
                        FocusUi.ghostButton(this, tokens, "Finish early, earn what I have done") {
                            completeTask(current)
                        }
                    )
                }
            }

            Verification.PHOTO -> {
                card.addView(
                    FocusUi.primaryButton(this, tokens, "Take the photo") { capture(current) }
                )
                card.addView(FocusUi.spacer(this, 10))
                card.addView(FocusUi.caption(this, tokens, PhotoProof.describeChecks(this)))

                val attempts = EarnSession.photoAttempts(this)
                if (attempts > 0) {
                    card.addView(FocusUi.spacer(this, 8))
                    card.addView(
                        FocusUi.caption(this, tokens, Copy.earnPhotoRetry(this, attempts))
                    )
                }
                if (EarnMode.showsCredibility(this)) {
                    card.addView(FocusUi.spacer(this, 8))
                    card.addView(
                        FocusUi.meter(
                            this,
                            tokens,
                            "Verification trust",
                            (current.credibility * 100).toInt().toString() + "%",
                            current.credibility,
                            if (current.credibility < 0.6f) tokens.warning else tokens.success
                        )
                    )
                }
            }

            Verification.SUBTASKS_ALL -> {
                if (current.subtasks.isEmpty()) {
                    card.addView(
                        FocusUi.secondary(
                            this,
                            tokens,
                            "This task has no steps, so there is nothing to tick. Add some, or " +
                                "switch it to another way of finishing."
                        )
                    )
                } else {
                    current.subtasks.forEach { subtask ->
                        val toggle = FocusUi.switchControl(this, tokens, subtask.done) { done ->
                            val updated = current.copy(
                                subtasks = current.subtasks.map {
                                    if (it.id == subtask.id) it.copy(done = done) else it
                                }
                            )
                            FocusTaskStore.update(this, updated)
                            task = updated
                            refresh()
                        }
                        card.addView(FocusUi.listRow(this, tokens, subtask.title, null, trailing = toggle))
                    }

                    val allDone = current.subtasks.all { it.done }
                    card.addView(FocusUi.spacer(this, 12))
                    val button = FocusUi.primaryButton(
                        this,
                        tokens,
                        if (allDone) "Finish and collect" else "Tick every step first"
                    ) { if (allDone) completeTask(current) }
                    button.isEnabled = allDone
                    button.alpha = if (allDone) 1f else 0.5f
                    card.addView(button)
                }
            }
        }
    }

    // ── Photo proof ───────────────────────────────────────────────

    private fun capture(current: FocusTask) {
        if (!PhotoProof.isCaptureAvailable(this)) {
            FocusDialog.info(
                this,
                "No camera app",
                "Nothing on this phone can take the photo. Switch this task to another way of " +
                    "finishing in the editor."
            )
            return
        }

        val file = PhotoProof.newProofFile(this)
        pendingProof = file
        try {
            cameraLauncher.launch(PhotoProof.captureIntent(this, PhotoProof.uriFor(this, file)))
        } catch (_: Exception) {
            pendingProof = null
            PhotoProof.discard(file)
            FocusDialog.toast(this, "The camera would not open.")
        }
    }

    /**
     * Checked here, on this phone, and then deleted. The only thing that outlives
     * a proof photo is a 64-bit fingerprint that stops the same shot being used
     * twice — and that is stated on screen rather than assumed.
     */
    private fun handleProof(file: File) {
        val current = task ?: return
        val result = PhotoProof.verify(this, current, file, EarnSession.startedAt(this))
        PhotoProof.discard(file)

        val updated = PhotoProof.applyCredibility(this, current, result)
        task = updated

        if (result.accepted) {
            FocusDialog.alert(
                this,
                title = result.headline,
                message = result.detail,
                confirmLabel = "Collect",
                onConfirm = { completeTask(updated) }
            )
        } else {
            EarnSession.recordPhotoAttempt(this)
            FocusDialog.alert(
                this,
                title = result.headline,
                message = result.detail + "\n\n" + Copy.earnPhotoRetry(this, EarnSession.photoAttempts(this)),
                confirmLabel = "Try again",
                cancelLabel = "Later",
                onConfirm = { capture(updated) }
            )
            refresh()
        }
    }

    // ── Budget and apps ───────────────────────────────────────────

    private fun buildBudgetCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, EarnBudget.formatBalance(this)))
        card.addView(FocusUi.spacer(this, 6))

        val projected = task?.let { EarnMode.rewardFor(this, it, EarnSession.elapsedMinutes(this)) } ?: 0
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                when {
                    task?.enjoyable == true ->
                        "This one pays nothing, which was your call. The work still counts."
                    EarnBudget.capReachedToday(this) -> Copy.earnCapReached(this)
                    projected > 0 -> "Finishing now would add about " + projected + " minutes."
                    else -> "Keep going and minutes start to add up."
                }
            )
        )
    }

    private fun buildAllowedApps(current: FocusTask): View = card { card ->
        val allowed = EarnSession.allowedPackages(this, current)
            .filter { it != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it) != null }
            .distinct()
            .sortedBy { AppCatalog.label(this, it) }

        if (allowed.isEmpty()) {
            card.addView(
                FocusUi.emptyState(
                    this,
                    tokens,
                    "FocusLock only. Nothing else opens until this is done."
                )
            )
        } else {
            val strip = FocusUi.row(this)
            strip.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            allowed.forEach { packageName ->
                strip.addView(
                    FocusUi.chip(this, tokens, AppCatalog.label(this, packageName), false) {
                        launch(packageName)
                    }
                )
            }
            card.addView(FocusUi.horizontalScroll(this, strip))
        }

        val rejected = EarnSession.rejectedPackages(this, current)
        if (rejected.isNotEmpty()) {
            card.addView(FocusUi.spacer(this, 10))
            val warning = FocusUi.caption(
                this,
                tokens,
                "Asked for but not on your standing allowlist: " +
                    rejected.joinToString { AppCatalog.label(this, it) } + "."
            )
            warning.setTextColor(tokens.warning)
            card.addView(warning)
        }
    }

    private fun launch(target: String) {
        val intent = packageManager.getLaunchIntentForPackage(target) ?: return
        try {
            startActivity(intent)
        } catch (_: Exception) {
            FocusDialog.toast(this, "The lock is holding that one.")
        }
    }

    // ── Finishing ─────────────────────────────────────────────────

    private fun completeTask(current: FocusTask) {
        val focusedMinutes = EarnSession.elapsedMinutes(this)
        val owed = EarnMode.rewardFor(this, current, focusedMinutes)
        val granted = EarnBudget.credit(this, owed)

        FocusTaskStore.complete(this, current)
        Streaks.recordActivity(this)
        EarnSession.stop(this)

        val capped = owed > 0 && granted < owed
        val spendable = EarnBudget.balanceMinutes(this)
        val canSpendNow = spendable > 0 && !SessionManager.shouldLockTask(this)

        FocusDialog.alert(
            this,
            title = "Task done",
            message = Copy.earnCompleted(this, granted) +
                (if (capped) "\n\n" + Copy.earnCapReached(this) else "") +
                (if (spendable > 0 && !canSpendNow) "\n\n" + Copy.earnSpendBlockedInKiosk(this) else ""),
            confirmLabel = "Good",
            cancelLabel = if (canSpendNow) "Use " + spendable + " min now" else null,
            onConfirm = { leave() },
            onCancel = {
                if (EarnBudget.spend(this, spendable)) {
                    FocusDialog.toast(this, Copy.earnSpending(this, spendable))
                }
                leave()
            }
        )
    }

    /**
     * Stopping early is not a failure state. The task stays exactly where it
     * was, nothing is deducted, and the streak is untouched.
     */
    private fun confirmStop(current: FocusTask) {
        FocusDialog.alert(
            this,
            title = "Stop this task?",
            message = "It stays on your list exactly as it is. Nothing is lost and nothing is " +
                "deducted — the minutes you did not earn simply were not earned.",
            confirmLabel = "Stop",
            cancelLabel = "Keep going",
            onConfirm = {
                EarnSession.stop(this)
                leave()
            }
        )
    }

    private fun leave() {
        MainActivity.open(this, MainActivity.TAB_TASKS)
        finish()
    }
}
