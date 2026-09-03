package com.focuslock.mdm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * The screen that owns the phone when a permission the running session
 * depends on was just taken away.
 *
 * Nothing here is punitive framing - the headline says plainly what happened
 * and how to undo it - but there is deliberately no way past it except
 * fixing the permission. Back does nothing. There is no "later". The one
 * route out is the Settings screen for whichever permission is missing,
 * which stays reachable because [KioskPolicy] admits the Settings family of
 * packages into the lock-task allowlist for exactly this state and nothing
 * else.
 */
class PermissionRecoveryActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tokens: UiPrefs.Tokens
    private lateinit var column: LinearLayout

    private val ticker = object : Runnable {
        override fun run() {
            PermissionGuard.clearResolved(this@PermissionRecoveryActivity)
            if (!PermissionGuard.isEmergency(this@PermissionRecoveryActivity)) {
                KioskPolicy.exitPermissionEmergency(this@PermissionRecoveryActivity)
                finish()
                return
            }
            render()
            handler.postDelayed(this, 1_500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokens = UiPrefs.resolve(this)

        // Consumes back outright. There is nothing behind this screen to go
        // to - the phone was already somewhere, and that somewhere is exactly
        // what this is holding.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* deliberately does nothing */ }
        })

        val scroll = ScrollView(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
            setBackgroundColor(tokens.background)
        }
        scroll.addView(column)
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.enterPermissionEmergency(this)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        column.removeAllViews()
        val missing = PermissionGuard.activeEmergency(this)
        if (missing.isEmpty()) {
            finish()
            return
        }

        column.addView(
            TextView(this).apply {
                text = "This needs fixing before the phone goes back to normal"
                textSize = 22f
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
                typeface = tokens.typeface
            }
        )
        column.addView(
            TextView(this).apply {
                text = "A permission the running session depends on was turned off. " +
                    "Nothing else works properly until it's back on."
                textSize = 15f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(24))
                typeface = tokens.typeface
            }
        )

        missing.forEach { g ->
            column.addView(
                TextView(this).apply {
                    text = g.label
                    textSize = 17f
                    setTextColor(tokens.textPrimary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(16), 0, dp(4))
                    typeface = tokens.typeface
                }
            )
            column.addView(
                TextView(this).apply {
                    text = g.explanation
                    textSize = 14f
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(10))
                    typeface = tokens.typeface
                }
            )
            val intent = settingsIntentFor(g)
            if (intent != null) {
                column.addView(
                    Button(this).apply {
                        text = "Turn it back on"
                        setTextColor(tokens.onAccent)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(tokens.accent)
                        typeface = tokens.typeface
                        setOnClickListener {
                            try {
                                startActivity(intent)
                            } catch (_: Exception) {
                                // No matching settings screen on this build of
                                // Android - nothing more this button can do.
                            }
                        }
                    }
                )
            } else {
                column.addView(
                    TextView(this).apply {
                        text = "This can only be restored by re-provisioning device " +
                            "owner from a computer, over ADB, the same way it was set up."
                        textSize = 13f
                        setTextColor(tokens.textSecondary)
                        gravity = Gravity.CENTER
                        typeface = tokens.typeface
                    }
                )
            }
        }

        // The one deliberate way out, and only because it already is one
        // everywhere else in the app: ending the session is never blocked by
        // anything, this included. If early exit is off, this screen offers
        // exactly as little as Kiosk itself already does in that
        // configuration - nothing new is being taken away, and nothing new
        // is being granted either.
        if (SessionManager.canEndEarly(this)) {
            column.addView(
                TextView(this).apply {
                    text = "Ending the session works the same as always, and clears this too."
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(28), 0, dp(6))
                    typeface = tokens.typeface
                }
            )
            column.addView(
                Button(this).apply {
                    text = "End the session"
                    setTextColor(tokens.textPrimary)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(tokens.surfaceAlt)
                    typeface = tokens.typeface
                    setOnClickListener { confirmEndSession() }
                }
            )
        }
    }

    private fun confirmEndSession() {
        FocusDialog.alert(
            this,
            title = "End the session?",
            message = "Everything unlocks straight away, including this. The time you already did still counts.",
            confirmLabel = "End it",
            cancelLabel = "Keep going",
            onConfirm = {
                SessionManager.end(this)
                KioskPolicy.syncLockTaskState(this)
                finish()
            }
        )
    }

    private fun settingsIntentFor(g: PermissionGuard.Guarded): Intent? = when (g) {
        PermissionGuard.Guarded.USAGE_ACCESS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PermissionGuard.Guarded.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PermissionGuard.Guarded.ACCESSIBILITY ->
            // Android has no reliable cross-OEM way to deep-link straight to one
            // service's own toggle, so this opens the list it lives in.
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PermissionGuard.Guarded.DEVICE_OWNER -> null
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, PermissionRecoveryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
