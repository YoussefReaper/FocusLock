package com.focuslock.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the blocker service whenever the phone boots.
 * Without this the lock would vanish after every reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            if (LockManager.isKioskActive(context)) {
                AppBlockerService.start(context)
            }
        }
    }
}
