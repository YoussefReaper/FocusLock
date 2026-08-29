package com.focuslock.mdm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout

/**
 * The permission checklist.
 *
 * Two rules here. Each row says what the permission is *for* in the user's own
 * terms, not Android's — "lets FocusLock see which app is in front" rather than
 * "usage access". And nothing is demanded that the user's own capability
 * choices do not actually need, so a person running soft mode is never asked
 * for notification access.
 */
class SetupPermissionsActivity : FocusScreenActivity() {

    private data class Requirement(
        val title: String,
        val why: String,
        val granted: Boolean,
        val required: Boolean,
        val open: () -> Unit
    )

    override fun screenTitle(): String = "Permissions"

    override fun screenSubtitle(): String =
        "Android grants these by hand. FocusLock only asks for what your settings actually use."

    override fun buildContent(column: LinearLayout) {
        val requirements = requirements()
        val outstanding = requirements.count { it.required && !it.granted }

        column.addView(buildSummary(outstanding, requirements.size))
        column.addView(sectionLabel("What is needed"))
        column.addView(buildList(requirements))
        column.addView(sectionLabel("Device Owner"))
        column.addView(buildDeviceOwnerCard())
    }

    private fun buildSummary(outstanding: Int, total: Int): View = card { card ->
        card.addView(
            FocusUi.heading(
                this,
                tokens,
                if (outstanding == 0) "Everything is granted" else outstanding.toString() + " still to grant"
            )
        )
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                if (outstanding == 0) {
                    "Nothing else is needed for the capabilities you have switched on."
                } else {
                    "Of " + total + " permissions FocusLock can use, your settings need these."
                }
            )
        )
    }

    private fun buildList(requirements: List<Requirement>): View = card { card ->
        requirements.forEachIndexed { index, requirement ->
            val trailing = if (requirement.granted) {
                FocusUi.pill(this, tokens, "Granted", tokens.success)
            } else {
                FocusUi.smallButton(this, tokens, "Grant") { requirement.open() }
            }
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    requirement.title,
                    requirement.why + (if (requirement.required) "" else "  ·  not needed by your current settings"),
                    trailing = trailing
                ) { if (!requirement.granted) requirement.open() }
            )
            if (index < requirements.size - 1) card.addView(FocusUi.divider(this, tokens))
        }
    }

    /** True when some capability the user has switched on actually needs this permission. */
    private fun capabilityNeeds(predicate: (CapabilitySpec) -> Boolean): Boolean =
        Capabilities.all.any { predicate(it) && CapabilityRegistry.isEnabled(this, it.id) }

    private fun requirements(): List<Requirement> {
        return listOf(
            Requirement(
                "See which app is in front",
                "Called Usage access in Settings. It is how blocking, limits and the time breakdown work at all.",
                SetupChecks.hasUsageAccess(this),
                capabilityNeeds { it.needsUsageAccess },
            ) { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },

            Requirement(
                "Read the screen",
                "Called Accessibility. Only ever reads text, so the keyword, Shorts, Reels and " +
                    "WhatsApp guards can recognise a surface. It never types or taps.",
                SetupChecks.isContentGuardEnabled(this),
                capabilityNeeds { it.needsAccessibility },
            ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },

            Requirement(
                "Hold back notifications",
                "Lets the shield dismiss alerts from blocked apps. Always-allowed apps are never touched.",
                SetupChecks.isNotificationAccessGranted(this),
                capabilityNeeds { it.needsNotificationAccess },
            ) { open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },

            Requirement(
                "Draw over other apps",
                "A fallback for showing the block screen when Android will not let FocusLock open a window.",
                SetupChecks.canDrawOverlays(this),
                true,
            ) {
                open(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:" + packageName)
                    }
                )
            },

            Requirement(
                "Run without battery limits",
                "Stops Android putting the guard to sleep in the middle of a long session.",
                SetupChecks.isIgnoringBatteryOptimizations(this),
                true,
            ) {
                open(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:" + packageName)
                    }
                )
            },

            Requirement(
                "Location",
                "Only used by place rules, and only as a last known fix. Never a continuous stream.",
                SetupChecks.hasLocationAccess(this),
                capabilityNeeds { it.needsLocation },
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_LOCATION
                )
            },

            Requirement(
                "Device admin",
                "The first half of the Device Owner setup that kiosk mode needs.",
                SetupChecks.isDeviceAdminActive(this),
                capabilityNeeds { it.needsDeviceOwner },
            ) {
                open(
                    Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            android.content.ComponentName(this@SetupPermissionsActivity, AdminReceiver::class.java)
                        )
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Kiosk mode needs device admin."
                        )
                    }
                )
            }
        )
    }

    private fun buildDeviceOwnerCard(): View = card { card ->
        val isOwner = SetupChecks.isDeviceOwner(this)

        // The demo has no device-admin receiver at all, so there is nothing here
        // to set up and no point pretending otherwise. Say so plainly instead of
        // showing a button that leads nowhere.
        if (!BuildConfig.ENFORCEMENT) {
            card.addView(FocusUi.heading(this, tokens, "This is the demo"))
            card.addView(FocusUi.spacer(this, 6))
            card.addView(
                FocusUi.secondary(
                    this,
                    tokens,
                    "Every screen, rule, task and setting here is the real app. What is missing " +
                        "is the enforcement: nothing is ever hidden, suspended, intercepted or " +
                        "closed, no notification is ever dismissed, and this build cannot be made " +
                        "Device Owner even over ADB. Set it up exactly as you mean to for real — " +
                        "the full version is what starts holding the line."
                )
            )
            card.addView(FocusUi.spacer(this, 14))
            card.addView(
                FocusUi.secondaryButton(this, tokens, "What the full version adds") {
                    startActivity(Intent(this, DeviceOwnerHelpActivity::class.java))
                }
            )
            return@card
        }

        card.addView(
            FocusUi.heading(this, tokens, if (isOwner) "Device Owner is active" else "Device Owner is not set")
        )
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                if (isOwner) {
                    "Kiosk sessions can hold, Safe Mode can be closed, and blocked apps can be " +
                        "suspended at the system level."
                } else {
                    "Without it, kiosk mode cannot really hold: the launcher stays reachable and " +
                        "Safe Mode starts the phone with FocusLock inert. It is set from a computer over ADB."
                }
            )
        )
        card.addView(FocusUi.spacer(this, 14))
        card.addView(
            FocusUi.primaryButton(this, tokens, if (isOwner) "See what it allows" else "Show me how") {
                startActivity(Intent(this, DeviceOwnerHelpActivity::class.java))
            }
        )
    }

    private fun open(intent: Intent) {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 3 * 60 * 1000)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            FocusDialog.toast(this, "That page is not available on this phone.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    companion object {
        private const val REQUEST_LOCATION = 4713
    }
}
