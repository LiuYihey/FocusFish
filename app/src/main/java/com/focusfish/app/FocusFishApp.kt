package com.focusfish.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.focusfish.app.ai.ProactiveScheduler
import com.focusfish.app.data.repository.AppRepositoryImpl
import com.focusfish.app.detection.AppDetectionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application 入口类
 * 使用 Hilt 进行依赖注入，注意：数据库初始化由 Hilt 处理，不在此处手动初始化
 */
@HiltAndroidApp
class FocusFishApp : Application() {

    /** 应用级协程作用域，用于跑常驻的后台任务 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Hilt EntryPoint，用于在 Application 中获取仓库实例 */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppDetectionEntryPoint {
        fun appRepository(): AppRepositoryImpl

        /** 主动行为调度器：让大肥鱼在你不理它的时候也会自己开口 */
        fun proactiveScheduler(): ProactiveScheduler
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化守护开关状态（从 SharedPreferences 恢复）
        AppDetectionManager.init(this)
        // 注入仓库，用于拦截次数统计
        val entryPoint = EntryPointAccessors.fromApplication(
            this,
            AppDetectionEntryPoint::class.java
        )
        AppDetectionManager.setAppRepository(entryPoint.appRepository())
        createNotificationChannel()

        // 启动主动行为调度：大肥鱼会自己判断「现在要不要说句话」
        applicationScope.launch {
            entryPoint.proactiveScheduler().start(this)
        }
    }

    /**
     * 创建通知渠道
     * 渠道 ID: focus_fish_service
     * P2：minSdk=26 (O)，SDK_INT >= O 检查冗余已移除
     *
     * 需求 2：应用已取消一切用户可见通知（含通知权限申请）。
     * 此渠道仅供前台服务自身的"常驻通知"使用 —— Android 要求前台服务
     * 必须关联一条通知才能保持存活，但它不会打扰用户（IMPORTANCE_MIN，无声无角标）。
     */
    private fun createNotificationChannel() {
        val channelId = NOTIFICATION_CHANNEL_ID
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_desc)
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            // 守护服务通知不应发出声音
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        /** 前台服务常驻通知渠道 ID（唯一保留的通知渠道） */
        const val NOTIFICATION_CHANNEL_ID = "focus_fish_service"
    }
}
