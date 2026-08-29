package com.focuslock.mdm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * How to make FocusLock Device Owner, and an honest account of what that buys.
 *
 * The blueprint is blunt about this and so is this screen: the sideloaded app
 * alone cannot close Safe Mode. Only a Device Owner can, and Device Owner is
 * set from a computer. Claiming otherwise would be the most consequential lie
 * the app could tell.
 */
class DeviceOwnerHelpActivity : FocusScreenActivity() {

    private val command =
        "adb shell dpm set-device-owner com.focuslock.mdm/.AdminReceiver"

    override fun screenTitle(): String = "Device Owner"

    override fun screenSubtitle(): String =
        "The one step that has to happen from a computer, and what it actually changes."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildStatusCard())
        column.addView(sectionLabel("What it changes"))
        column.addView(buildEffectCard())
        column.addView(sectionLabel("Setting it up from a computer"))
        column.addView(buildStepsCard())
        column.addView(sectionLabel("The QR route"))
        column.addView(buildQrCard())
        column.addView(sectionLabel("What it still cannot do"))
        column.addView(buildHonestyCard())
    }

    private fun buildStatusCard(): View = card { card ->
        val isOwner = SetupChecks.isDeviceOwner(this)
        val header = FocusUi.row(this)
        val title = FocusUi.heading(this, tokens, if (isOwner) "Active" else "Not set")
        title.layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        header.addView(
            FocusUi.pill(
                this,
                tokens,
                if (isOwner) "Device Owner" else "Sideloaded only",
                if (isOwner) tokens.success else tokens.warning
            )
        )
        card.addView(header)
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                if (isOwner) {
                    "Everything in the Hardening group is available to switch on."
                } else {
                    "Soft, Block and most guards work without this. Kiosk, Safe Mode blocking, " +
                        "app suspension and uninstall protection do not."
                }
            )
        )
    }

    private fun buildEffectCard(): View = card { card ->
        listOf(
            "Safe Mode can be closed" to
                "Otherwise a reboot into Safe Mode starts the phone with FocusLock switched off.",
            "Blocked apps can be suspended" to
                "The system refuses to launch them, instead of FocusLock catching them a moment late.",
            "Blocked apps can be hidden" to
                "They disappear from the launcher for the length of a session.",
            "FocusLock can be the home screen" to
                "During a kiosk session the home button lands here instead of the launcher.",
            "Uninstalling can be blocked" to
                "Removing the app stops being a way to end a session early.",
            "Browsers can be held to your allowlist" to
                "Chrome and friends get a managed policy pointing at the same list the safe browser uses."
        ).forEach { pair ->
            card.addView(FocusUi.listRow(this, tokens, pair.first, pair.second))
        }
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                "Every one of these is still a switch in Rules. Being Device Owner makes them possible, " +
                    "not automatic."
            )
        )
    }

    private fun buildStepsCard(): View = card { card ->
        val steps = listOf(
            "Sign out of every Google account on this phone, and remove any other user or work profile. " +
                "Android refuses to set a Device Owner while an account exists.",
            "On the phone: Settings, About phone, tap Build number seven times to unlock Developer options.",
            "In Developer options, turn on USB debugging.",
            "Plug the phone into a computer with the Android platform-tools installed, and accept the " +
                "debugging prompt on the phone.",
            "On the computer, run: adb devices, and check the phone is listed as device rather than unauthorized.",
            "Run the command below.",
            "Come back here. This screen should now say Active."
        )

        steps.forEachIndexed { index, step ->
            val row = FocusUi.row(this)
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = FocusUi.dp(this@DeviceOwnerHelpActivity, 12) }

            val number = FocusUi.pill(this, tokens, (index + 1).toString(), tokens.accent)
            row.addView(number)
            row.addView(FocusUi.spacerH(this, 12))

            val text = FocusUi.secondary(this, tokens, step)
            text.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            row.addView(text)
            card.addView(row)
        }

        card.addView(buildCommandBlock())
    }

    private fun buildCommandBlock(): View {
        val block = FocusUi.body(this, tokens, command)
        block.typeface = android.graphics.Typeface.MONOSPACE
        block.setTextIsSelectable(true)
        val padding = FocusUi.dp(this, 14)
        block.setPadding(padding, padding, padding, padding)
        block.background = FocusUi.roundedShape(
            this,
            tokens.input,
            minOf(tokens.radiusDp, 14),
            tokens.divider
        )

        val container = FocusUi.column(this)
        container.addView(block)
        container.addView(FocusUi.spacer(this, 10))
        container.addView(
            FocusUi.secondaryButton(this, tokens, "Copy the command") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("FocusLock", command))
                FocusDialog.toast(this, "Copied.")
            }
        )
        return container
    }

    private fun buildQrCard(): View = card { card ->
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "On a phone that has been factory reset, tapping the welcome screen six times opens " +
                    "a QR scanner. Provisioning from a QR sets Device Owner without a computer, but it " +
                    "needs the app hosted at a URL along with its signature checksum, and it wipes the " +
                    "phone as part of setup. The ADB route above is the practical one for a phone " +
                    "already in use."
            )
        )
    }

    private fun buildHonestyCard(): View = card { card ->
        listOf(
            "A factory reset always works" to
                "Recovery-mode reset is the ultimate exit and no software tier can close it. " +
                    "FocusLock does not try, and never removes the reset button from the You tab.",
            "ADB stays yours to close" to
                "Provisioning does not disable ADB. Only the button in the You tab does, and only " +
                    "when you tap it.",
            "Nothing is remote" to
                "There is no server, no account and no one else who can lock or unlock this phone."
        ).forEach { pair ->
            card.addView(FocusUi.listRow(this, tokens, pair.first, pair.second))
        }
    }
}
