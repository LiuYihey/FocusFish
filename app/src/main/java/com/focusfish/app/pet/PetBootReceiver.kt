package com.focusfish.app.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 桌宠开机自启接收器
 *
 * Android 15 的限制：
 * 「有悬浮窗权限」不再等于「可以后台启动前台服务」。
 * 从后台直接起前台服务会抛 `ForegroundServiceStartNotAllowedException`。
 *
 * 所以桌宠的开机自启采用**中转 Activity** 方案：
 * 广播 → 起一个透明的 `PetBootActivity` → 它在 onResume 里起前台服务 → 自己 finish。
 *
 * 因为是用户可见的 Activity 上下文，符合 Android 15 的例外条款。
 */
class PetBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PetBootReceiver"

        /** SharedPreferences 名 */
        private const val PREFS = "focus_fish_pet"

        /** 是否启用桌宠 */
        private const val KEY_ENABLED = "enabled"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // 用户没开桌宠就不启动
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        // 没有悬浮窗权限也起不来
        if (!com.focusfish.app.util.PermissionHelper.checkOverlayPermission(context)) return

        try {
            // 起中转 Activity，由它在可见状态下启动桌宠服务
            val i = Intent(context, PetBootActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "开机拉起桌宠失败", e)
        }
    }
}
