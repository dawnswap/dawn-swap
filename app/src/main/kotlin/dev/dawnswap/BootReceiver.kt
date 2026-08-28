package dev.dawnswap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms do not survive a reboot, and an app update cancels them too - hence both
 * `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        ArmScheduler.schedule(context)
        ShortcutController.refresh(context)
    }
}
