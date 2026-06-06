package com.focuslock.mdm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskAllowlistTest {

    @Test
    fun userLaunchableWhitelist_excludesKernelEscapePackages() {
        assertTrue(Constants.USER_LAUNCHABLE_WHITELIST.contains(Constants.OWN_PACKAGE))

        Constants.KIOSK_ESCAPE_SURFACES.forEach { pkg ->
            assertFalse(Constants.USER_LAUNCHABLE_WHITELIST.contains(pkg))
        }

        Constants.SETTINGS_ESCAPE_PACKAGES.forEach { pkg ->
            assertFalse(Constants.USER_LAUNCHABLE_WHITELIST.contains(pkg))
        }
    }

    @Test
    fun lockTaskPackages_preBaseline_includesOnboardingSettings() {
        val lockTaskPackages = Constants.lockTaskPackagesForBaseline(
            baselineReady = false,
            ownPackage = Constants.OWN_PACKAGE
        )

        assertTrue(lockTaskPackages.contains(Constants.OWN_PACKAGE))

        Constants.ONBOARDING_SETTINGS_PACKAGES.forEach { pkg ->
            assertTrue(lockTaskPackages.contains(pkg))
        }

        Constants.KIOSK_ESCAPE_SURFACES.forEach { pkg ->
            assertFalse(lockTaskPackages.contains(pkg))
        }
    }

    @Test
    fun lockTaskPackages_postBaseline_excludesSettingsAndEscapeSurfaces() {
        val lockTaskPackages = Constants.lockTaskPackagesForBaseline(
            baselineReady = true,
            ownPackage = Constants.OWN_PACKAGE
        )

        assertTrue(lockTaskPackages.contains(Constants.OWN_PACKAGE))

        Constants.SETTINGS_ESCAPE_PACKAGES.forEach { pkg ->
            assertFalse(lockTaskPackages.contains(pkg))
        }

        Constants.KIOSK_ESCAPE_SURFACES.forEach { pkg ->
            assertFalse(lockTaskPackages.contains(pkg))
        }
    }
}
