package com.easylistening.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** 每日定时：用 AlarmManager 在指定时刻触发播放。 */
object AlarmScheduler {
    private const val REQ = 4201

    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 未授予精确闹钟权限：退化为非精确闹钟
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ScheduleReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
