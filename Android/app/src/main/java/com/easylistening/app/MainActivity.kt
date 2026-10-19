package com.easylistening.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.easylistening.app.data.Settings
import com.easylistening.app.data.copyAssetIfExists
import com.easylistening.app.ui.EasyListeningApp
import com.easylistening.app.ui.PlaybackHolder
import com.easylistening.app.ui.theme.EasyListeningTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 每次启动都覆盖内置词典（保证随版本更新）
        copyAssetIfExists(this, "dict/main.esd", File(filesDir, "dict/main.esd"))

        // 初始化 C 内核（打开词典 / 单词本 / 调度状态）
        val initResult = NativeLib.nativeInit(filesDir.absolutePath)

        // 内置单词本：用打包的词表生成（已存在同名本则跳过）
        val seed = File(filesDir, "dict/builtin.txt")
        if (copyAssetIfExists(this, "dict/builtin.txt", seed)) {
            NativeLib.nativeSeedBook("内置", seed.absolutePath)
        }

        handleIntent(intent)

        setContent {
            EasyListeningTheme {
                EasyListeningApp(initResult = initResult)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 用户点"定时提醒"通知进入时，直接开始今天的听力。 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ScheduleReceiver.EXTRA_START_SCHEDULE, false) != true) {
            return
        }
        intent.removeExtra(ScheduleReceiver.EXTRA_START_SCHEDULE)

        val cfg = Settings.loadSchedule(this)
        if (cfg.bookId < 0) return
        if (!PlaybackHolder.controller.start(cfg.bookId, cfg.daily)) return

        val svc = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }
}
