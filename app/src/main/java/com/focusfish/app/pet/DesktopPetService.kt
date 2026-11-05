package com.focusfish.app.pet

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusfish.app.FocusFishApp
import com.focusfish.app.MainActivity
import com.focusfish.app.R
import com.focusfish.app.ai.FishMoodResolver
import com.focusfish.app.ai.LocalLineLibrary
import com.focusfish.app.data.local.entity.FishStateEntity
import com.focusfish.app.util.FocusSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 桌宠前台服务
 *
 * 为什么必须用前台服务：
 * Android 8.0+ 后台应用不能显示悬浮窗（`TYPE_APPLICATION_OVERLAY` 会抛
 * `WindowManager$BadTokenException`）。只有前台服务才有悬浮窗资格。
 *
 * 为什么用 `SPECIAL_USE` 类型：
 * Android 14+（API 34）起前台服务必须声明具体类型。
 * 桌宠既不是数据同步也不是位置服务，`SPECIAL_USE` 是正确的归类。
 *
 * Android 15 的额外限制：
 * 起前台服务前必须已经有一个可见的叠加层或 App 可见。
 * 所以本服务**只能从 Activity（用户可见）里启动**，
 * 不能靠 BootReceiver 后台拉起 —— 那会抛 `ForegroundServiceStartNotAllowedException`。
 * 开机自启改由 BootReceiver 拉起一个透明 Activity 来中转。
 */
class DesktopPetService : Service() {

    companion object {
        private const val TAG = "DesktopPetService"

        /** 前台通知 ID */
        const val NOTIFICATION_ID = 1002

        /** SharedPreferences 名 */
        private const val PREFS = "focus_fish_pet"

        /** 是否启用桌宠 */
        private const val KEY_ENABLED = "enabled"

        /** 桌宠尺寸（dp） */
        private const val KEY_SIZE_DP = "size_dp"

        /** 桌宠 X 坐标 */
        private const val KEY_POS_X = "pos_x"

        /** 桌宠 Y 坐标 */
        private const val KEY_POS_Y = "pos_y"

        /** 渲染帧间隔（ms）—— 约 30fps，桌宠不需要 60fps */
        private const val FRAME_INTERVAL_MS = 33L

        /** 状态轮询间隔（ms）—— 每 3 秒看一眼大肥鱼的状态 */
        private const val STATE_POLL_INTERVAL_MS = 3_000L

        /** 主动冒泡检查间隔（ms）—— 每 2 分钟试一次 */
        private const val SPEAK_CHECK_INTERVAL_MS = 120_000L

        /** 主动冒泡冷却（ms）—— 20 分钟，避免烦人 */
        private const val SPEAK_COOLDOWN_MS = 20 * 60 * 1000L

        /** Intent Action */
        const val ACTION_TOGGLE = "com.focusfish.app.action.TOGGLE_PET"

        /**
         * 启动桌宠服务
         *
         * 注意：必须在用户可见的上下文中调用（Activity），
         * 否则 Android 12+ 会拒绝启动前台服务。
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, DesktopPetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "启动桌宠服务失败", e)
            }
        }

        /** 停止桌宠服务 */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DesktopPetService::class.java))
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: PetWindowController? = null
    private var menuController: PetMenuController? = null
    private var renderJob: Job? = null
    private var stateJob: Job? = null

    /** 桌宠是否被用户藏起来了 */
    private var petHidden = false

    /** 上次主动冒泡时间 */
    private var lastSpeakAt = 0L

    /** 上次显示的姿态，避免每 3 秒重复切贴图 */
    private var currentPose: PetSpriteLibrary.Pose? = null

    /** 是否处于睡觉状态 */
    private var sleeping = false

    /** 是否由用户主动开启（决定要不要显示通知） */
    private var userEnabled = true

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        userEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val sizeDp = prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP)
        val savedX = prefs.getInt(KEY_POS_X, -1).takeIf { it >= 0 }
        val savedY = prefs.getInt(KEY_POS_Y, -1).takeIf { it >= 0 }

        val ctl = PetWindowController(this, object : PetWindowController.Listener {
            override fun onPetClicked() {
                speakRandomLine()
                openChat()
            }

            override fun onPetDoubleClicked() {
                speakRandomLine()
                openFeeding()
            }

            override fun onPetLongPressed() {
                showMenu()
            }

            override fun onPetDragged(x: Int, y: Int) {
                savePosition(x, y)
            }

            override fun onPetFlung() {
                // 甩出去了，等落地
            }

            override fun onPetReachedEdge(edge: PetWindowController.Edge) {
                // 贴边时可能冒一句
                if (Math.random() < 0.3) {
                    speakRandomLine()
                }
            }

            override fun onPetSpeakRequest(): String? {
                return null
            }

            override fun onPetStateChanged(state: PetWindowController.PetState) {
                Log.d(TAG, "状态切换：$state")
            }
        })

        controller = ctl
        menuController = PetMenuController(this, object : PetMenuController.Listener {
            override fun onMenuSpeak() {
                speakRandomLine()
            }

            override fun onMenuToggleSleep() {
                sleeping = !sleeping
                controller?.setSleeping(sleeping)
                currentPose = if (sleeping) {
                    PetSpriteLibrary.Pose.SLEEPING
                } else {
                    PetSpriteLibrary.Pose.IDLE
                }
                controller?.showBubble(
                    if (sleeping) "……zzz" else "唔……醒了。"
                )
            }

            override fun onMenuGoHome() {
                val metrics = resources.displayMetrics
                controller?.moveTo(
                    metrics.widthPixels - petSizePx() - dpToPx(12),
                    dpToPx(120)
                )
                savePosition(
                    metrics.widthPixels - petSizePx() - dpToPx(12),
                    dpToPx(120)
                )
            }

            override fun onMenuFlip() {
                val ctl = controller ?: return
                ctl.flipFacing()
            }

            override fun onMenuToggleVisible() {
                petHidden = !petHidden
                controller?.setPetVisible(!petHidden)
            }

            override fun onMenuQuit() {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, false)
                    .apply()
                stopSelf()
            }
        })

        try {
            ctl.show(sizeDp, savedX, savedY)
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
            stopSelf()
            return
        }

        startRenderLoop()
        startStateWatch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        renderJob?.cancel()
        stateJob?.cancel()
        serviceScope.cancel()
        controller?.hide()
        controller = null
        PetSpriteLibrary.clear()
        super.onDestroy()
    }

    /**
     * 渲染循环
     */
    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = serviceScope.launch {
            var last = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val delta = now - last
                last = now
                controller?.let { ctl ->
                    ctl.advance(delta / 1000f)
                    ctl.tick(delta)
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    /**
     * 状态监听：让桌宠跟着大肥鱼的状态变化
     */
    private fun startStateWatch() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            while (isActive) {
                try {
                    val state = loadFishState()
                    stateJobAffinity = currentAffinity()
                    applyState(state)
                    maybeSpeakProactively()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 忽略单次失败
                }
                delay(STATE_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 读取大肥鱼状态
     */
    private suspend fun loadFishState(): FishStateEntity? {
        return try {
            com.focusfish.app.data.local.AppDatabase.getInstance(this)
                .fishStateDao().get()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据状态切换桌宠姿态
     *
     * 这是 FocusFish 相对 dsh-pet 的独有价值：
     * 桌宠读的是同一份 FishStateEntity，
     * 所以大肥鱼在 App 里挨饿，桌宠也会露出饿相。
     */
    private fun applyState(state: FishStateEntity?) {
        val ctl = controller ?: return
        if (state == null) return

        // 睡眠状态优先
        val isSleeping = state.isSleeping
        if (isSleeping != sleeping) {
            sleeping = isSleeping
            ctl.setSleeping(isSleeping)
            currentPose = if (isSleeping) PetSpriteLibrary.Pose.SLEEPING else PetSpriteLibrary.Pose.IDLE
            return
        }
        if (isSleeping) return

        val affinity = stateJobAffinity
        val mood = FishMoodResolver.resolve(
            affinity = affinity,
            satiety = state.satiety,
            mood = state.mood,
            energy = state.energy,
            isSleeping = false
        )

        val pose = when {
            state.satiety < 25 -> PetSpriteLibrary.Pose.HUNGRY
            state.energy < 20 -> PetSpriteLibrary.Pose.SLEEPING
            state.mood < -40 -> PetSpriteLibrary.Pose.SAD
            mood.tier == com.focusfish.app.ai.FishMood.Tier.WANT_FOCUS -> PetSpriteLibrary.Pose.SLACKING
            state.mood > 40 -> PetSpriteLibrary.Pose.EXCITED
            affinity >= FishMoodResolver.AFFINITY_CLOSE -> PetSpriteLibrary.Pose.PROUD
            else -> PetSpriteLibrary.Pose.IDLE
        }

        if (pose != currentPose) {
            currentPose = pose
            ctl.setPose(pose)
        }
    }

    /** 缓存的好感度（在状态轮询里刷新，避免在非协程里调 suspend 函数） */
    private var stateJobAffinity = 0

    /**
     * 读取当前好感度
     */
    private suspend fun currentAffinity(): Int {
        return try {
            val db = com.focusfish.app.data.local.AppDatabase.getInstance(this)
            db.userFishDao().getUserFish()?.affinityLevel ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 主动冒泡
     *
     * 双轨设计（桌宠用户研究的核心教训）：
     * - 本地台词：零 token，随时可冒泡
     * - AI 台词：走 API，需要用户已配置 API
     *
     * 无论哪种，20 分钟冷却 —— 吵一点就翻车。
     */
    private suspend fun maybeSpeakProactively() {
        val ctl = controller ?: return
        val now = System.currentTimeMillis()
        if (lastSpeakAt > 0 && now - lastSpeakAt < SPEAK_COOLDOWN_MS) return

        // 用户正在专注时不打扰
        if (FocusSessionState.isActive) return

        // 概率触发（5%）—— 不是每次检查都冒泡
        if (Math.random() > 0.05) return

        val line = LocalLineLibrary.line(LocalLineLibrary.Scene.IDLE)
        if (line.isNotBlank()) {
            ctl.showBubble(line)
            lastSpeakAt = now
        }
    }

    /**
     * 显示长按菜单
     */
    private fun showMenu() {
        val ctl = controller ?: return
        val menu = menuController ?: return
        val (x, y) = ctl.currentPosition()
        menu.show(x + dpToPx(40), y)
    }

    /**
     * dp → px
     */
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /**
     * 桌宠尺寸（px）
     */
    private fun petSizePx(): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP)
        return dpToPx(sizeDp)
    }

    /**
     * 说一句随机台词（点击/拖拽等交互触发）
     */
    private fun speakRandomLine() {
        val ctl = controller ?: return
        val scene = LocalLineLibrary.Scene.IDLE
        ctl.showBubble(LocalLineLibrary.line(scene))
    }

    /**
     * 打开聊天页
     */
    private fun openChat() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "打开聊天页失败", e)
        }
    }

    /**
     * 打开投喂页
     */
    private fun openFeeding() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_FEEDING, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "打开投喂页失败", e)
        }
    }

    /**
     * 保存位置
     */
    private fun savePosition(x: Int, y: Int) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_POS_X, x)
            .putInt(KEY_POS_Y, y)
            .apply()
    }

    /**
     * 前台通知
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FocusFishApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("大肥鱼在桌面上")
            .setContentText("点它可以跟它说说话")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
