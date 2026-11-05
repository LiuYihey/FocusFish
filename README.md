<img src="./logo.png" alt="FocusFish Logo" width="160" />

# FocusFish · 专注大肥鱼

> 用反思问答挽回注意力，用蓝色大肥鱼陪你专注

一只住在手机里的蓝色大肥鱼，靠陪你专注换**大白饭**吃。
它会摸鱼、会饿、会犯困、会记得你说过的话，也会在你没理它的时候自己冒泡。

---

## 它是什么

FocusFish 是一个 Android 专注陪伴 App，由 [FocusCat](https://github.com/LiuYihey/FocusCat) 演进而来 —— 保留了原项目成熟的**双引擎拦截**、**反思问答**、**专注计时**逻辑，把陪伴角色从「专注猫」换成了中文互联网上火起来的 **DeepSeek 蓝色大肥鱼** IP。

原项目 FocusCat 的代码保持零改动，FocusFish 是独立的新工程。

## 核心特性

### 🐟 会活的大肥鱼

| 能力 | 说明 |
|------|------|
| **自定义人格卡** | 7 个可编辑字段（身份/性格/说话风格/口头禅/来历/禁忌/称呼）+ **主动开口动机文案** |
| **四维状态系统** | 好感度 × 饱腹度 × 精力 × 心情，四维组合出 10 种行为档位（饥饿/吃饱/想被关注/困倦/低落/熟睡…） |
| **大白饭经济** | 专注满 30 分钟发一份大白饭，好感度升级会解锁不同档位的专属反应 |
| **主动开口** | 按概率掷骰主动找你搭话，触发文案是「她的动机」而不是「系统通知」 |
| **五种开口由头** | 情绪 / 里程碑 / 联想 / 记忆 / 沉默 —— 不是随机冒泡，是有理由地说话 |
| **会记事的候选态** | 它从你话里捡到想记的事，先放候选区，**你点头它才真的记住** |
| **陪读** | 你专注时它自己一页一页读你导入的书，结束后给你感悟 |
| **世界感知** | 知道你今天用了哪些 App、各用了多久、专注了多久 |
| **会休眠** | 每天知道自己睡了多久，醒来会犯迷糊 |

### 🔌 多 Provider 支持

不绑定任何厂商，所有 OpenAI 兼容端点都能接。内置 14 个预设：

`DeepSeek` · `Kimi` · `通义千问` · `智谱 GLM` · `MiniMax` · `硅基流动` · `豆包` · `腾讯混元` · `百度千帆` · `讯飞星火` · `OpenAI` · `Groq` · `本地 Ollama` · `自定义`

- 密钥用 **Android Keystore AES-GCM** 加密后只存在本机
- 推理模型（o 系列 / gpt-5 / deepseek-reasoner）自动切 `max_completion_tokens` 并跳过 `temperature`
- MiniMax 这类非标准路径的厂商已预填正确地址
- 内置「测试连接」按钮，秒验地址/密钥/模型名对不对

### 🛡️ 拦截与约束

沿用 FocusCat 的成熟方案：

- **双引擎拦截**：无障碍服务 + 前台轮询，兼容各厂商 ROM
- 打开约束 App → 反思问答 → 选择退出 → 弹出**大肥鱼表情包鼓励** → 把你送回桌面
- 专注模式下切屏检测，离开即提醒

### 🐾 桌面桌宠（开发中）

像 dsh 电脑版那样的桌面电子宠物，常驻手机桌面。
见 [docs/桌宠设计.md](docs/桌宠设计.md)。

---

## 技术栈

| 项 | 版本 |
|---|---|
| Kotlin | 1.9.25 |
| AGP / Gradle | 8.3.2 / 8.4 |
| Compose BOM | 2024.09.03 |
| Hilt | 2.51 |
| Room | 2.6.1 (KSP) |
| Media3 ExoPlayer | 1.2.1 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JDK | 17 |

网络层是手写的 `HttpURLConnection` + SSE 流式解析，不引入第三方网络库 —— 少一个依赖就少一份体积。

## 构建

```powershell
git clone https://github.com/LiuYihey/FocusFish.git
cd FocusFish

# 复制本地配置并改 SDK 路径
copy gradle.properties.example gradle.properties
# 编辑 local.properties 填 sdk.dir

.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

签名配置见 [gradle.properties.example](gradle.properties.example)（把签名属性放到 `~/.gradle/gradle.properties`，**不要**提交到仓库）。

## 权限说明

| 权限 | 用途 | 可选 |
|------|------|------|
| 无障碍服务 | 拦截约束应用 | 必需 |
| 使用情况访问 | 统计今日各 App 使用时长 | 必需 |
| 悬浮窗 | 桌宠显示在其他应用上层 | 桌宠功能必需 |
| 通知 | 桌宠前台服务 | 桌宠功能必需 |
| 联网 | 调用你自填的 AI API | 必需 |

**所有数据都存在本机。** App 不上传任何数据，聊天记录、记忆、大肥鱼状态全在本地 SQLite 里。

## 素材版权

- 大肥鱼形象基于公开设定特征**原创重制**（呆毛 + 鲸鳍耳 + 蓝渐变发 + 大鲸尾 + 深蓝白配色）
- 表情包（`assets/stickers/`，48 张）来自 [fisharchive.cc](https://fisharchive.cc)「蓝色大肥鱼档案馆」，二创同人素材
- 视频（`assets/videos/`，6 段）来自 B 站二创视频
- 均为**非商业同人素材**，个人使用没问题，**商用需自行取得作者授权**
- 未打包任何 Sketchfab / MakerWorld 的 NC 授权 3D 模型（避免授权风险）

## 致谢

- [FocusCat](https://github.com/LiuYihey/FocusCat) —— 本项目的地基，双引擎拦截与专注逻辑来自它
- [dsh-speak-up](https://github.com/Yuuyoko-uu/dsh-speak-up) —— 主动开口的命中线公式
- [dsh-whale-musume](https://github.com/Sutera-Diffusus/dsh-whale-musume) —— 双档冷却与本地台词库的思路
- [dsh-fish-memory](https://blog.csdn.net/m0_73859037/article/details/169645300) —— 记忆半衰期与注入上限的参数

## License

代码以 MIT 协议开源。素材部分不适用 MIT，请遵守各自来源的授权。

## 合规

本 App 遵循《人工智能拟人化互动服务管理暂行办法》：
- 显著提示用户正在与 AI 而非自然人交互（聊天页顶部）
- 提供便捷退出途径，不阻碍用户离开
- 交互数据本地加密存储，不向第三方提供
- 不将「替代社会交往」作为产品设计目标
