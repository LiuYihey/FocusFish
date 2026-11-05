package com.focusfish.app.pet

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * 桌宠开机自启的中转 Activity
 *
 * 存在的唯一理由：Android 15 起，后台直接启动前台服务会被系统拒绝
 * （`ForegroundServiceStartNotAllowedException`）。
 * 但从**可见的 Activity** 里启动前台服务是允许的例外。
 *
 * 所以这个 Activity 透明、无动画、不在最近任务里，
 * 在 onResume 里启动桌宠服务然后立刻 finish。
 * 用户完全看不到它。
 */
class PetBootActivity : Activity() {

    companion object {
        private const val TAG = "PetBootActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明主题 + 不显示在最近任务
    }

    override fun onResume() {
        super.onResume()
        try {
            DesktopPetService.start(this)
            Log.i(TAG, "桌宠已通过中转 Activity 启动")
        } catch (e: Exception) {
            Log.w(TAG, "桌宠启动失败", e)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
