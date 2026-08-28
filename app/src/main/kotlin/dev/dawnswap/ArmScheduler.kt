package dev.dawnswap

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One inexact alarm a day, purely to repaint icons the moment the window opens.
 *
 * Inexact means no `SCHEDULE_EXACT_ALARM` permission and a negligible battery cost - which
 * matters, because the whole point of this app is to stop burning a morning on a phone.
 * Drift is harmless: the trampoline reads the clock at tap time, so a late alarm only ever
 * makes an icon look stale.
 */
object ArmScheduler {

    private const val REQUEST_CODE = 1

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val repository = SwapRepository(context)
        val pending = pendingIntent(context)

        alarms.cancel(pending)
        if (!repository.enabled) return

        alarms.setInexactRepeating(
            AlarmManager.RTC,
            nextOccurrenceOf(repository.window.startMinute),
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    private fun nextOccurrenceOf(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ArmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
