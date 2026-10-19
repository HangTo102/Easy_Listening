package com.easylistening.app.data

import android.content.Context

/** 复读配置：单词/释义各自的重复次数与间隔（毫秒）。 */
data class RepeatConfig(
    val wordRepeat: Int = 3,
    val wordIntervalMs: Int = 700,
    val speakTranslation: Boolean = true,
    val transRepeat: Int = 3,
    val transIntervalMs: Int = 700
)

/** 每日定时听力配置。 */
data class ScheduleConfig(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
    val bookId: Long = -1,
    val daily: Int = 0
)

object Settings {
    private const val PREF = "easylistening_settings"

    fun load(context: Context): RepeatConfig {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return RepeatConfig(
            wordRepeat = p.getInt("wordRepeat", 3).coerceIn(1, 10),
            wordIntervalMs = p.getInt("wordIntervalMs", 700).coerceIn(0, 2000),
            speakTranslation = p.getBoolean("speakTranslation", true),
            transRepeat = p.getInt("transRepeat", 3).coerceIn(1, 10),
            transIntervalMs = p.getInt("transIntervalMs", 700).coerceIn(0, 2000)
        )
    }

    fun save(context: Context, c: RepeatConfig) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putInt("wordRepeat", c.wordRepeat)
            putInt("wordIntervalMs", c.wordIntervalMs)
            putBoolean("speakTranslation", c.speakTranslation)
            putInt("transRepeat", c.transRepeat)
            putInt("transIntervalMs", c.transIntervalMs)
        }.apply()
    }

    fun loadTargetLang(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("targetLang", "中文") ?: "中文"

    fun saveTargetLang(context: Context, lang: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("targetLang", lang).apply()
    }

    fun loadDailyCount(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt("dailyCount", 0).coerceAtLeast(0)

    fun saveDailyCount(context: Context, n: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("dailyCount", n.coerceAtLeast(0)).apply()
    }

    fun loadSchedule(context: Context): ScheduleConfig {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return ScheduleConfig(
            enabled = p.getBoolean("schedEnabled", false),
            hour = p.getInt("schedHour", 20).coerceIn(0, 23),
            minute = p.getInt("schedMinute", 0).coerceIn(0, 59),
            bookId = p.getLong("schedBookId", -1L),
            daily = p.getInt("schedDaily", 0).coerceAtLeast(0)
        )
    }

    fun saveSchedule(context: Context, c: ScheduleConfig) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putBoolean("schedEnabled", c.enabled)
            putInt("schedHour", c.hour)
            putInt("schedMinute", c.minute)
            putLong("schedBookId", c.bookId)
            putInt("schedDaily", c.daily)
        }.apply()
    }
}
