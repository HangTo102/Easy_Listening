package com.easylistening.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 打开各厂商的"自启动 / 后台启动"设置页（没有标准 API，只能尽力而为）。
 * 找不到厂商页面时回退到本应用的系统详情页。
 */
fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        // 小米 / Redmi
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        // 华为 / 荣耀
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        // OPPO / 一加 / realme
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        ComponentName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        // vivo / iQOO
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        // 魅族
        ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
        // 乐视
        ComponentName(
            "com.letv.android.letvsafe",
            "com.letv.android.letvsafe.AutobootManageActivity"
        )
    )

    for (component in candidates) {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return
            }
        }
    }

    // 回退：应用详情页（用户可在此关闭电池限制）
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
