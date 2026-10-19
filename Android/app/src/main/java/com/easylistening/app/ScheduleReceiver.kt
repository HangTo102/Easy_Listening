package com.easylistening.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.easylistening.app.data.ScheduleConfig
import com.easylistening.app.data.Settings
import com.easylistening.app.ui.PlaybackHolder

/** 定时闹钟触发 / 开机后恢复：开始听力并安排下一次。 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cfg = Settings.loadSchedule(context)
        when (intent.action) {
            ACTION_FIRE -> {
                if (!cfg.enabled) return
                trigger(context, cfg)
                AlarmScheduler.schedule(context, cfg.hour, cfg.minute)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                if (cfg.enabled) {
                    AlarmScheduler.schedule(context, cfg.hour, cfg.minute)
                }
            }
        }
    }

    private fun trigger(context: Context, cfg: ScheduleConfig) {
        if (cfg.bookId < 0) return
        val controller = PlaybackHolder.controller
        if (!controller.start(cfg.bookId, cfg.daily)) return

        val svc = Intent(context, PlaybackService::class.java)
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
            true
        } catch (e: Exception) {
            false
        }

        if (!started) {
            // 后台启动前台服务被系统限制：停止半启动的播放，改为发提醒通知
            controller.stop()
            postReminder(context)
        }
    }

    private fun postReminder(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL,
                "定时提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val pi = PendingIntent.getActivity(
            context,
            4301,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_START_SCHEDULE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("定时听力")
            .setContentText("点我开始今天的单词听力")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(REMINDER_ID, n)
    }

    companion object {
        const val ACTION_FIRE = "com.easylistening.app.ACTION_SCHEDULE_FIRE"
        const val EXTRA_START_SCHEDULE = "com.easylistening.app.EXTRA_START_SCHEDULE"
        private const val REMINDER_CHANNEL = "easylistening_reminder"
        private const val REMINDER_ID = 1002
    }
}
