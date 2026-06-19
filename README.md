# ⚔️ 金铲铲之战 - 全自动挂机助手

基于 Android 无障碍服务（Accessibility Service）实现的金铲铲之战自动化工具。

## 功能

- ✅ **自动开始对局** — 大厅自动点击开始游戏
- ✅ **自动购买卡牌** — 自动购买商店中所有卡牌
- ✅ **自动刷新商店** — 购买后自动刷新商店
- ✅ **自动购买经验** — 定时升级
- ✅ **自动放置棋子** — 将板凳上的棋子自动拖到棋盘
- ✅ **自动下一局** — 对局结束后自动开始下一局
- ✅ **弹窗自动确认** — 自动关闭各类弹窗
- ✅ **悬浮球控制** — 可拖拽的悬浮按钮，一键启停

## 系统要求

- Android 12 (API 31) 及以上
- 金铲铲之战已安装

## 编译方法

### 方法一：GitHub Actions 云编译（最简单，无需本地环境）

1. 在 GitHub 上创建一个新仓库（如 `tft-auto`）
2. 把本项目所有文件推送到仓库：

```bash
cd tft-auto
git init
git add .
git commit -m "init: tft-auto"
git branch -M main
git remote add origin https://github.com/你的用户名/tft-auto.git
git push -u origin main
```

3. 推送后 GitHub Actions 会自动开始编译（约 3-5 分钟）
4. 打开仓库页面 → **Actions** 选项卡 → 点击最新的 build 任务
5. 在 **Artifacts** 区域下载 `tft-auto-debug` 压缩包，解压得到 APK
6. 传到手机安装即可

> 💡 也可以在 Actions 页面点击 **Run workflow** 手动触发编译

### 方法二：Android Studio

1. 下载安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio → `File` → `Open` → 选择 `tft-auto` 文件夹
3. 等待 Gradle 同步完成
4. 点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
5. APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`

### 方法三：命令行编译

```bash
# 确保已安装 Android SDK 和 JDK 17+
cd tft-auto
chmod +x gradlew
./gradlew assembleDebug

# APK 输出路径
ls app/build/outputs/apk/debug/
```

## 使用步骤

### 1. 安装 APK
```bash
adb install app-debug.apk
# 或者将 APK 传到手机上手动安装
```

### 2. 授权设置
打开「金铲铲自动」应用，依次点击：

1. **授权设置** → 开启「无障碍服务」中找到「金铲铲自动」并开启
2. **授权设置** → 授予「悬浮窗」权限

### 3. 启动自动化
1. 打开金铲铲之战，进入游戏大厅
2. 回到「金铲铲自动」应用，点击「启动自动化」
3. 屏幕上出现绿色悬浮球表示运行中
4. 切换到金铲铲之战，自动化将自动执行

### 4. 控制
- **绿色悬浮球** = 运行中，点击暂停
- **红色悬浮球** = 已暂停，点击恢复
- 悬浮球可拖拽到屏幕任意位置

## 坐标适配

默认坐标基于主流 16:9 / 20:9 屏幕。如果你的设备分辨率不同，可能需要调整 `GameAutomator.java` 中的坐标参数：

```java
// 修改这些百分比值来适配你的屏幕
private static final float LOBBY_START_X = 0.50f;  // 开始按钮 X
private static final float LOBBY_START_Y = 0.85f;  // 开始按钮 Y
// ... 更多坐标见 GameAutomator.java
```

## 工作原理

```
┌─────────────────────────────────────────┐
│         AccessibilityService            │
│  ┌─────────┐    ┌──────────────────┐   │
│  │ 状态检测 │───→│   GameAutomator  │   │
│  │ (800ms) │    │   状态机执行      │   │
│  └─────────┘    └──────┬───────────┘   │
│                        │               │
│  ┌─────────────────────▼─────────────┐ │
│  │     GestureDescription API        │ │
│  │     (点击 / 拖拽 / 长按)           │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘

状态机:
  LOBBY → MATCHMAKING → IN_GAME → GAME_END → LOBBY
              ↑                        │
              └────────────────────────┘
```

## 自动化策略

| 阶段 | 操作 | 频率 |
|------|------|------|
| 大厅 | 点击开始游戏 | 持续 |
| 匹配中 | 等待 | - |
| 对局中 | 买牌 + 刷新 + 买经验 + 放牌 | 每回合 |
| 对局结束 | 点击再来一局 | 一次 |

## 注意事项

- ⚠️ 本工具仅供学习和研究目的
- ⚠️ 使用自动化工具可能违反游戏服务条款
- ⚠️ 请自行承担使用风险
- 建议在人机对战或自定义房间中测试

## 项目结构

```
tft-auto/
├── app/
│   ├── src/main/
│   │   ├── java/com/tft/auto/
│   │   │   ├── MainActivity.java           # 主界面
│   │   │   ├── TFTAccessibilityService.java # 无障碍核心服务
│   │   │   ├── GameAutomator.java           # 游戏自动化逻辑
│   │   │   ├── FloatingButtonService.java   # 悬浮控制按钮
│   │   │   └── ScreenCaptureService.java    # 截屏服务(预留)
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── xml/accessibility_service_config.xml
│   │   │   ├── drawable/  (按钮样式、图标)
│   │   │   └── values/    (颜色、字符串、主题)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle          # 项目级构建
├── settings.gradle
├── gradle.properties
└── README.md
```

## License

MIT - 仅供学习研究
