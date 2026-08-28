package dev.dawnswap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when the window opens, so the icons show what a tap would now do. */
class ArmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        ShortcutController.refresh(context)
    }
}
