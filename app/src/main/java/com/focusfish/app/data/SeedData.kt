package com.focusfish.app.data

import com.focusfish.app.data.local.entity.AffinityAchievementEntity
import com.focusfish.app.data.local.entity.FishCatalogEntity
import com.focusfish.app.data.local.entity.FoodCatalogEntity
import com.focusfish.app.data.local.entity.QuizQuestionEntity
import com.focusfish.app.data.local.entity.QuoteEntity
import com.focusfish.app.data.local.entity.ReflectionQuestionEntity

/**
 * 种子数据对象
 * 用于应用首次启动时初始化题库、大肥鱼形态、食物目录、反思问题、成就
 */
object SeedData {

    /**
     * 获取种子题目列表（保留兼容旧版选择题）
     */
    fun getSeedQuestions(): List<QuizQuestionEntity> {
        return listOf(
            QuizQuestionEntity(
                question = "2 + 3 等于多少？",
                optionsJson = """["3","4","5","6"]""",
                correctIndex = 2,
                category = "数学",
                difficulty = 1
            ),
            QuizQuestionEntity(
                question = "中国的首都是？",
                optionsJson = """["上海","北京","广州","深圳"]""",
                correctIndex = 1,
                category = "常识",
                difficulty = 1
            )
        )
    }

    /**
     * 获取大肥鱼形态种子数据
     * 2 种品种（经典大肥鱼、元气大肥鱼），每种对应不同的动画资源名
     */
    fun getSeedFishBreeds(): List<FishCatalogEntity> {
        return listOf(
            FishCatalogEntity(
                breedId = "bigfatfish",
                displayName = "经典",
                description = "蓝眼温柔，粘人治愈，专注路上的最佳搭档",
                iconAsset = "fish_bigfat",
                idleAnimAsset = "fish_bigfat_idle",
                eatAnimAsset = "fish_bigfat_eat",
                happyAnimAsset = "fish_bigfat_happy"
            ),
            FishCatalogEntity(
                breedId = "bigfatfish2",
                displayName = "元气大肥鱼",
                description = "憨厚贪吃，亲和力满分，永远在等下一顿",
                iconAsset = "fish_bigfat2",
                idleAnimAsset = "fish_bigfat2_idle",
                eatAnimAsset = "fish_bigfat2_eat",
                happyAnimAsset = "fish_bigfat2_happy"
            )
        )
    }

    /**
     * 获取食物目录种子数据
     * 4 种食物，不同稀有度和好感度加成
     * foodId 与 assets/foods 下的图片文件名一致
     */
    fun getSeedFoods(): List<FoodCatalogEntity> {
        return listOf(
            FoodCatalogEntity(
                foodId = "大白饭",
                displayName = "大白饭",
                rarity = "common",
                affinityBonus = 1,
                dropRate = 0.40,
                iconAsset = "food_bigrice"
            ),
            FoodCatalogEntity(
                foodId = "冻干",
                displayName = "冻干",
                rarity = "rare",
                affinityBonus = 3,
                dropRate = 0.20,
                iconAsset = "food_fishjerky"
            ),
            FoodCatalogEntity(
                foodId = "酸奶",
                displayName = "酸奶",
                rarity = "rare",
                affinityBonus = 3,
                dropRate = 0.20,
                iconAsset = "food_milksoup"
            ),
            FoodCatalogEntity(
                foodId = "罐头",
                displayName = "罐头",
                rarity = "epic",
                affinityBonus = 5,
                dropRate = 0.20,
                iconAsset = "food_canned"
            )
        )
    }

    /**
     * 获取默认反思问题种子数据（需求 3：改为选择题）
     *
     * 第 1 题：目标强化选择题 —— 点击即答，降低使用门槛
     * 第 2 题：使用时长选择 —— 决定本次进入后的免打扰时长
     */
    fun getSeedReflectionQuestions(): List<ReflectionQuestionEntity> {
        return listOf(
            ReflectionQuestionEntity(
                questionText = "你进入这个 app 是来做什么的？",
                order = 1,
                isActive = true,
                isCustom = false,
                placeholder = "",
                questionType = ReflectionQuestionEntity.TYPE_CHOICE,
                optionsJson = """["消遣一下","完成具体任务","只是想看看","其他"]"""
            ),
            ReflectionQuestionEntity(
                questionText = "这次打算使用多久？",
                order = 2,
                isActive = true,
                isCustom = false,
                placeholder = "",
                questionType = ReflectionQuestionEntity.TYPE_DURATION,
                optionsJson = """["5 分钟","15 分钟","30 分钟","1 小时"]"""
            )
        )
    }

    /**
     * 获取好感度成就种子数据
     * 3 个里程碑：10/50/100 次投喂
     * "初识之友"(10次) 解锁后可添加第二个伙伴咪作为奖励
     */
    fun getSeedAchievements(): List<AffinityAchievementEntity> {
        return listOf(
            AffinityAchievementEntity(
                milestone = 10,
                title = "初识之友",
                description = "投喂 10 次，大肥鱼开始信任你\n【奖励】解锁添加新伙伴资格"
            ),
            AffinityAchievementEntity(
                milestone = 50,
                title = "默契伙伴",
                description = "投喂 50 次，大肥鱼与你心意相通"
            ),
            AffinityAchievementEntity(
                milestone = 100,
                title = "心有灵犀",
                description = "投喂 100 次，它开始黏你了，说话都不一样了"
            ),
            AffinityAchievementEntity(
                milestone = 200,
                title = "同一条鱼",
                description = "投喂 200 次，你们已经不用多说就懂了"
            )
        )
    }

    /**
     * 获取默认大肥鱼格言种子数据（需求 1）
     *
     * 退出被约束应用时不再发放大白饭，改为随机展示一句大肥鱼格言。
     * 这些是内置的警醒句子，用户可在「设置 → 大肥鱼格言管理」中自由增删改。
     */
    fun getSeedQuotes(): List<QuoteEntity> {
        return listOf(
            QuoteEntity(
                text = "欸，刚摸到手机就被我逮住了吧？再专注一会儿，我就有大白饭吃了。",
                order = 1,
                isActive = true,
                isCustom = false
            ),
            QuoteEntity(
                text = "手机放下吧。你刚放下的这几分钟，比你拿起来的时间值钱。",
                order = 2,
                isActive = true,
                isCustom = false
            ),
            QuoteEntity(
                text = "三分钟的分心换不来三分钟的快乐，只会换来更长的拖延。我看着你呢。",
                order = 3,
                isActive = true,
                isCustom = false
            ),
            QuoteEntity(
                text = "专注不是天赋，是每一次主动放下的总和。我数着呢。",
                order = 4,
                isActive = true,
                isCustom = false
            ),
            QuoteEntity(
                text = "想清楚再回来啊。我又不会跑，就在这儿等着。",
                order = 5,
                isActive = true,
                isCustom = false
            ),
            QuoteEntity(
                text = "今天多守住的一小时，就是明天的大白饭。",
                order = 6,
                isActive = true,
                isCustom = false
            )
        )
    }
}
