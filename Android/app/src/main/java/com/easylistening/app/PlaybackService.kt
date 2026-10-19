package com.easylistening.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.easylistening.app.ui.PlaybackHolder

/**
 * 前台播放服务：使用 MediaStyle + MediaSession，
 * 折叠态即可显示「上一个 / 暂停·继续 / 下一个」三个按钮；
 * 锁屏/桌面媒体控件也可控制播放。
 */
class PlaybackService : Service() {

    private val controller get() = PlaybackHolder.controller
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pc = controller
        val s = MediaSession(this, "EasyListening")
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                if (pc.paused) pc.togglePause()
            }

            override fun onPause() {
                if (!pc.paused) pc.togglePause()
            }

            override fun onSkipToNext() = pc.next()

            override fun onSkipToPrevious() = pc.prev()

            override fun onStop() {
                pc.stop()
                stopSelf()
            }
        })
        s.isActive = true
        session = s
        pc.onChanged = { updateNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> controller.prev()
            ACTION_TOGGLE -> controller.togglePause()
            ACTION_NEXT -> controller.next()
            ACTION_STOP -> {
                controller.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    private fun updateNotification() {
        if (!controller.active) {
            return
        }
        updateSessionState()
        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            nm.notify(NOTIF_ID, buildNotification())
        }
    }

    private fun updateSessionState() {
        val s = session ?: return
        val state = if (controller.paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, controller.current?.word ?: "播放已结束")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Easy_Listening")
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val word = controller.current?.word ?: "播放已结束"
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val style = Notification.MediaStyle()
            .setMediaSession(session!!.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return builder
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(word)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(style)
            .addAction(action(R.drawable.ic_prev, "上一个", ACTION_PREV))
            .addAction(
                action(
                    if (controller.paused) R.drawable.ic_play else R.drawable.ic_pause,
                    if (controller.paused) "继续" else "暂停",
                    ACTION_TOGGLE
                )
            )
            .addAction(action(R.drawable.ic_next, "下一个", ACTION_NEXT))
            .build()
    }

    private fun action(iconRes: Int, title: String, action: String): Notification.Action {
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            title,
            actionIntent(action)
        ).build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "播放控制",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "显示当前单词并提供播放控制"
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        session?.isActive = false
        session?.release()
        session = null
        controller.onChanged = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "easylistening_playback"
        const val NOTIF_ID = 1001
        const val ACTION_PREV = "com.easylistening.app.ACTION_PREV"
        const val ACTION_TOGGLE = "com.easylistening.app.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.easylistening.app.ACTION_NEXT"
        const val ACTION_STOP = "com.easylistening.app.ACTION_STOP"
    }
}
