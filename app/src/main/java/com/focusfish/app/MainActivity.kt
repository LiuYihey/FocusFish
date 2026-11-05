package com.focusfish.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.focusfish.app.data.SeedData
import com.focusfish.app.data.local.dao.AffinityAchievementDao
import com.focusfish.app.data.local.dao.FishCatalogDao
import com.focusfish.app.data.local.dao.FoodCatalogDao
import com.focusfish.app.data.local.dao.QuizQuestionDao
import com.focusfish.app.data.local.dao.QuoteDao
import com.focusfish.app.data.local.dao.ReflectionQuestionDao
import com.focusfish.app.data.local.entity.ReflectionQuestionEntity
import com.focusfish.app.data.repository.AppRepositoryImpl
import com.focusfish.app.detection.AppDetectionManager
import com.focusfish.app.service.FocusFishService
import com.focusfish.app.service.KeepAliveManager
import com.focusfish.app.ui.MainScreen
import com.focusfish.app.ui.theme.FocusFishTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主 Activity
 * 应用入口，使用 Compose 构建 UI，使用 Hilt 进行依赖注入
 * 负责初始化种子数据、更新拦截列表、启动前台保活服务
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra：专注模式防分心拉回标记，FocusFishService 检测到用户切屏时带上 */
        const val EXTRA_FOCUS_MODE_RECALL = "focus_mode_recall"

        /** Intent extra：桌宠点击后要打开聊天页 */
        const val EXTRA_OPEN_CHAT = "open_chat"

        /** Intent extra：桌宠双击后要打开投喂页 */
        const val EXTRA_OPEN_FEEDING = "open_feeding"

        /** 一次性导航事件流：从 ConfirmActivity "去投喂"跳转时发射 true，MainScreen 收集后跳大肥鱼 Tab */
        private val _navigateToFishEvent = MutableStateFlow(false)
        val navigateToFishEvent = _navigateToFishEvent.asStateFlow()

        /** 供 ConfirmActivity 调用：触发跳转到大肥鱼 Tab */
        fun requestNavigateToFish() {
            _navigateToFishEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志，避免重复跳转 */
        fun requestNavigateToFishConsumed() {
            _navigateToFishEvent.value = false
        }

        /** 一次性导航事件流：从 FishScreen 空库存"去添加约束应用"跳转时发射 true */
        private val _navigateToAppsEvent = MutableStateFlow(false)
        val navigateToAppsEvent = _navigateToAppsEvent.asStateFlow()

        /** 供 FishScreen 调用：触发跳转到约束应用 Tab */
        fun requestNavigateToApps() {
            _navigateToAppsEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志，避免重复跳转 */
        fun requestNavigateToAppsConsumed() {
            _navigateToAppsEvent.value = false
        }

        /** 一次性导航事件流：FocusFishService 专注模式拉回时发射 true，MainScreen 跳 Focus 路由 */
        private val _focusRecallEvent = MutableStateFlow(false)
        val focusRecallEvent = _focusRecallEvent.asStateFlow()

        /** 供 FocusFishService 调用：触发跳转回专注页 */
        fun requestFocusRecall() {
            _focusRecallEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志 */
        fun requestFocusRecallConsumed() {
            _focusRecallEvent.value = false
        }

        /** 一次性导航事件流：桌宠点击后发射 true，MainScreen 跳 Chat 路由 */
        private val _navigateToChatEvent = MutableStateFlow(false)
        val navigateToChatEvent = _navigateToChatEvent.asStateFlow()

        /** 供 DesktopPetService 调用：打开聊天页 */
        fun requestNavigateToChat() {
            _navigateToChatEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志 */
        fun requestNavigateToChatConsumed() {
            _navigateToChatEvent.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 专注模式防分心拉回：FocusFishService 检测到用户切屏后通过 Intent extra 通知
        if (intent.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false)) {
            requestFocusRecall()
        }
        // 桌宠点击 → 打开聊天页
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) {
            requestNavigateToChat()
        }
    }

    /** 应用仓库，用于更新拦截列表 */
    @Inject
    lateinit var appRepository: AppRepositoryImpl

    /** 测验题目 DAO，用于初始化种子题库 */
    @Inject
    lateinit var quizQuestionDao: QuizQuestionDao

    /** 大肥鱼形态目录 DAO */
    @Inject
    lateinit var fishCatalogDao: FishCatalogDao

    /** 食物目录 DAO */
    @Inject
    lateinit var foodFishCatalogDao: FoodCatalogDao

    /** 反思问题 DAO */
    @Inject
    lateinit var reflectionQuestionDao: ReflectionQuestionDao

    /** 好感度成就 DAO */
    @Inject
    lateinit var achievementDao: AffinityAchievementDao

    /** 大肥鱼格言 DAO（需求 1） */
    @Inject
    lateinit var quoteDao: QuoteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装 Splash Screen（必须在 super.onCreate 之前）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 启用 edge-to-edge 沉浸式显示
        enableEdgeToEdge()
        // 初始化守护开关状态（从 SharedPreferences 恢复）
        AppDetectionManager.init(this)
        setContent {
            FocusFishTheme {
                MainScreen()
            }
        }
        // 初始化所有种子数据
        initSeedData()
        // 更新拦截列表
        updateBlockedApps()
        // 启动前台保活服务
        startFocusFishService()
        // 处理冷启动时的专注模式拉回 Intent（进程被杀后重新拉起）
        if (intent?.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false) == true) {
            requestFocusRecall()
        }
    }

    /**
     * 初始化种子数据
     * 首次启动时插入：题库、大肥鱼形态、食物目录、反思问题、成就
     */
    private fun initSeedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 兼容旧版选择题题库
            if (quizQuestionDao.count() == 0) {
                quizQuestionDao.insertAll(SeedData.getSeedQuestions())
            }
            // 大肥鱼形态目录
            if (fishCatalogDao.getAllBreeds().isEmpty()) {
                fishCatalogDao.insertAll(SeedData.getSeedFishBreeds())
            }
            // 食物目录
            if (foodFishCatalogDao.getAllFoods().isEmpty()) {
                foodFishCatalogDao.insertAll(SeedData.getSeedFoods())
            } else {
                // 同步最新 dropRate（种子数据可能因版本更新而调整概率）
                SeedData.getSeedFoods().forEach { seed ->
                    foodFishCatalogDao.updateDropRate(seed.foodId, seed.dropRate)
                }
            }
            // 反思问题（需求 3：选择题）
            // 老用户通过 MIGRATION_5_6 已把默认问题转成选择题，
            // 但若用户此前删光了默认问题或改成了自定义题，需要补齐缺失的题型
            val seeds = SeedData.getSeedReflectionQuestions()
            if (reflectionQuestionDao.countActive() == 0) {
                reflectionQuestionDao.insertAll(seeds)
            } else {
                // 缺少选择题 → 补一条；缺少使用时长题 → 补一条
                val existing = reflectionQuestionDao.getAllQuestions()
                val hasChoice = existing.any {
                    it.isActive && it.questionType == ReflectionQuestionEntity.TYPE_CHOICE
                }
                val hasDuration = existing.any {
                    it.isActive && it.questionType == ReflectionQuestionEntity.TYPE_DURATION
                }
                val maxOrder = existing.maxOfOrNull { it.order } ?: 0
                val missing = buildList {
                    if (!hasChoice) add(seeds[0].copy(order = maxOrder + 1))
                    if (!hasDuration) add(seeds[1].copy(order = maxOrder + 2))
                }
                if (missing.isNotEmpty()) {
                    reflectionQuestionDao.insertAll(missing)
                }
            }
            // 好感度成就
            if (achievementDao.getAllAchievements().isEmpty()) {
                achievementDao.insertAll(SeedData.getSeedAchievements())
            } else {
                // 同步成就描述更新（已有成就记录的用户也需更新文案）
                // "初识之友"新增"解锁新伙伴"奖励说明
                SeedData.getSeedAchievements().forEach { seed ->
                    achievementDao.updateDescription(seed.milestone, seed.description)
                }
            }
            // 大肥鱼格言（需求 1）：仅在库中一条都没有时写入内置句，避免覆盖用户自己写的
            if (quoteDao.getAllQuotes().isEmpty()) {
                quoteDao.insertAll(SeedData.getSeedQuotes())
            }
        }
    }

    /**
     * 更新拦截列表
     * 从数据库获取激活的拦截应用，更新到 AppDetectionManager
     */
    private fun updateBlockedApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeApps = appRepository.getActiveBlockedApps()
            val packages = activeApps.map { it.packageName }.toSet()
            AppDetectionManager.updateBlockedApps(packages)
        }
    }

    /**
     * 启动前台保活服务
     * 仅在守护开关开启时启动，避免关闭状态下仍常驻通知
     */
    private fun startFocusFishService() {
        if (!AppDetectionManager.isProtectionEnabled) return
        try {
            val intent = Intent(this, FocusFishService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限，忽略避免崩溃
        }
        // 需求 1+4：进入 App 时确认保活任务已注册
        // （JobScheduler 持久化任务在应用被系统清理后可能丢失，每次打开应用补一次保险）
        KeepAliveManager.schedule(this)
        KeepAliveManager.checkAccessibilityHealth(this)
    }
}
