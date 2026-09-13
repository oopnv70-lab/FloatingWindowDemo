package com.example.floatingwindow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.RequiresApi

/**
 * 悬浮窗权限与 ROM 适配工具类。
 *
 * 关键背景（Android 各版本变更）：
 *  - Android 6.0 (API 23)：SYSTEM_ALERT_WINDOW 成为运行时权限，但不走 requestPermissions()，
 *    必须跳转 Settings.ACTION_MANAGE_OVERLAY_PERMISSION 由用户手动开启。
 *  - Android 8.0 (API 26)：引入 TYPE_APPLICATION_OVERLAY，替代旧的 TYPE_PHONE / TYPE_SYSTEM_ALERT。
 *  - Android 10 (API 29)：TYPE_APPLICATION_OVERLAY 窗口禁止直接获取焦点；canDrawOverlays() 返回 true
 *    也不一定保证窗口能显示（尤其国产 ROM）。
 *  - Android 12 (API 31)：SYSTEM_ALERT_WINDOW 在权限列表默认不再展示，需手动搜索。
 *  - Android 14 (API 34)：进一步收紧后台启动 Activity / 前台服务规则。
 */
object OverlayPermission {

    /**
     * 权限是否已授予。
     *
     * 注意：canDrawOverlays() 只反映系统设置开关状态；部分 ROM（MIUI/ColorOS）即使该值为 true
     * 也可能在系统层拦截窗口，故业务侧仍需对 addView 失败做兜底。
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // Android 6.0 以下 SYSTEM_ALERT_WINDOW 在安装时即授予
            true
        }
    }

    /**
     * 跳转到系统设置页引导用户开启悬浮窗权限。
     * Android 6.0 以下无需申请，直接返回。
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            // 兼容部分 ROM：不加 NEW_TASK 可能无法从设置页正常返回
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 判断当前是否为 MIUI。MIUI 需在「安全中心 - 权限管理」额外开启「后台弹出界面」，
     * 否则 TYPE_APPLICATION_OVERLAY 窗口会静默创建失败。
     */
    fun isMiui(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"))

    /**
     * 判断当前是否为 ColorOS（OPPO/一加）。
     */
    fun isColorOs(): Boolean =
        !TextUtils.isEmpty(getSystemProperty("ro.build.version.opporom"))

    /**
     * 读取系统属性，用于 ROM 识别。
     */
    private fun getSystemProperty(key: String): String? = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    } catch (e: Exception) {
        null
    }
}