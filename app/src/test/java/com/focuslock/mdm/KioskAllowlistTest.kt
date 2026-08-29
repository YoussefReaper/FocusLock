package com.focuslock.mdm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lock-task package assembly.
 *
 * [KioskPolicy.buildLockTaskPackages] is deliberately pure so the rule that
 * matters most — what a kiosk session will and will not let through — can be
 * checked without a device.
 */
class KioskAllowlistTest {

    private val own = "com.focuslock.mdm"

    @Test
    fun ownPackageIsAlwaysPermitted() {
        val packages = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = false
        )
        assertTrue(packages.contains(own))
    }

    @Test
    fun alwaysAllowedAppsSurviveKiosk() {
        val packages = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = setOf("com.android.phone", "com.google.android.apps.maps"),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = false
        )
        assertTrue(packages.contains("com.android.phone"))
        assertTrue(packages.contains("com.google.android.apps.maps"))
    }

    @Test
    fun launcherIsExcludedWhenFocusLockIsHome() {
        val packages = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = false
        )
        SystemSurfaces.launchers.forEach { launcher ->
            assertFalse(packages.contains(launcher))
        }
    }

    @Test
    fun launcherIsIncludedWhenPersistentHomeIsOff() {
        val packages = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = true
        )
        SystemSurfaces.launchers.forEach { launcher ->
            assertTrue(packages.contains(launcher))
        }
    }

    @Test
    fun settingsOnlyReachableBeforeTheBaselineIsComplete() {
        val duringSetup = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = false,
            allowLauncherEscape = false
        )
        val afterSetup = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = false
        )

        assertTrue(duringSetup.contains("com.android.settings"))
        assertFalse(afterSetup.contains("com.android.settings"))
    }

    @Test
    fun systemSurfacesAreNeverLockedOut() {
        val packages = KioskPolicy.buildLockTaskPackages(
            ownPackage = own,
            userAllowed = emptySet(),
            alwaysAllowed = emptySet(),
            scheduleAllowed = emptySet(),
            baselineReady = true,
            allowLauncherEscape = false
        )
        assertTrue(packages.contains("com.android.systemui"))
        assertTrue(packages.contains("android"))
    }
}
