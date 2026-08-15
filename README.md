# OS4 玻璃模式统一低模糊

一个用于小米 **HyperOS / OS4（Android 17）** 的 Xposed / LSPosed 模块，把控制中心与通知中心的玻璃材质统一为更通透的低模糊风格，并修复通知列表最后一条玻璃背景被硬切、悬浮通知材质跳变等问题。

> 当前版本：**v6.1**  
> 主要测试设备：**Xiaomi 17（pudding）**  
> 其他机型、系统版本与 SystemUI 插件版本可能存在差异，请自行评估风险。

[下载最新 Release](https://github.com/lyugo0306/hyperos4-glass-blur/releases/latest) · [提交 Issue](https://github.com/lyugo0306/hyperos4-glass-blur/issues)

---

## 功能一览

- 🎴 **统一玻璃模糊**：控制中心卡片、通知卡、通知列表容器、长按管理弹窗、窗口下拉动画等使用统一的低模糊风格
- 🔳 **通知卡复用系统玻璃渲染管线**：调用系统 `NotificationRowGlassEffect` / `RowGlassEffect`，尽量保留原生动画、折叠、堆叠、圆角和阴影行为
- 🔔 **悬浮通知玻璃统一**：识别 heads-up 专属玻璃参数并替换为列表通知配方，减少弹出、点击时的颜色和透明度跳变
- ✅ **修复最后一条通知玻璃截断**：对玻璃 SDF 层尺寸进行钳制，使背景与实际可见内容高度对齐
- 🪟 **两种下拉手势效果统一**：直接下拉通知中心与从控制中心横向切换时使用一致的模糊逻辑
- 🌫️ **整屏背景模糊可调**：默认 50%
- ⚙️ **内置设置页**：参数图形化调整，保存后大部分设置可实时生效
  - 玻璃模糊半径、面板背景模糊比例
  - 亮度、压暗、折射、烧焦
  - 饱和度、透明度、RGB 色调
  - 边缘厚度、反射强度、方向光强度、背景色饱和度 / 亮度
  - 通知行强制玻璃、玻璃层高度钳制开关
  - 下拉背景缩放强度（0 = 无缩放，1 = 原样，2 = 加倍）

## 技术原理（简述）

HyperOS 4 的通知行本身具有 GLASS 渲染路径，但系统在部分场景会使用 BLUR 材质逻辑。模块主要做三件事：

1. 对通知行主动调用系统自带的 `NotificationRowGlassEffect.apply()`，复用 `RowBlurEffect` + `RowGlassEffect`；
2. 在需要时保持玻璃材质与玻璃轮廓相关 flag；
3. 对 `setMiGlassSdfMaxSize` 相关尺寸进行约束，使玻璃层尽量与可见内容高度一致。

v6.1 另外识别 heads-up 通知专属玻璃参数，并将其统一到普通列表通知的材质配方，避免点击时明显跳变。

核心原则是：**尽量调用系统原生玻璃渲染，而不是重新绘制一套独立材质。**

## 安装

1. 从 [Releases](https://github.com/lyugo0306/hyperos4-glass-blur/releases/latest) 下载 APK
2. 安装 APK
3. 在 **Vector** 或兼容的 LSPosed 管理器中启用模块
4. 确认作用域包含：
   - `com.android.systemui`
   - `miui.systemui.plugin`
5. 重启 SystemUI 或重启手机

关闭模块并重启 SystemUI / 手机即可还原。

## 设置页

v6.0 起提供独立设置页。参数保存到系统设置中，SystemUI 内的模块通过监听配置变化读取新值。

大多数参数保存后可直接生效；已经渲染的部分组件可能需要重新展开通知中心，必要时重启 SystemUI 才能完全刷新。

## 自行构建

需要：

- JDK 8+
- Android SDK Build Tools 37.0.0
- Android 37 platform (`android.jar`)
- `libxposed-api-102.0.0.jar`

先修改 `build.ps1` 顶部的本地工具路径和签名配置，然后执行：

```powershell
.\build.ps1
```

构建脚本会自动递归编译 `app/src` 下的全部 Java 源文件，包括 `MainHook.java` 和 `SettingsActivity.java`。产物默认输出到：

```text
release/os4-glass-blur-v6.1.apk
```

`release/` 是本地构建输出目录，不要求提交到仓库。公开发布时建议把 APK 上传到 GitHub Releases。

> 仓库不会保存你的私有签名密钥。正式长期分发建议使用固定的个人 release key，不要把 `.jks` / `.keystore` 上传到 GitHub。

## 目录结构

```text
├── app/
│   ├── AndroidManifest.xml      # versionCode 35 / versionName 6.1
│   ├── res/
│   ├── meta/                    # Xposed 元数据、作用域、入口
│   └── src/
│       └── dev/codex/os4glassblur/
│           ├── MainHook.java
│           └── SettingsActivity.java
├── build.ps1                    # PowerShell 一键构建脚本
├── .gitignore
├── LICENSE
└── README.md
```

## 已知限制

- 当前主要在 Xiaomi 17 / pudding 上测试
- HyperOS 4 Beta、SystemUI 插件、ROM 构建差异都可能影响 Hook
- 通知样式、媒体通知、大量通知、折叠 / 展开动画仍可能出现兼容性问题
- Root / Xposed 类模块始终存在 SystemUI 崩溃或启动异常风险

遇到问题时建议在 Issues 中提供：**机型、系统版本、模块版本、复现步骤、截图或录屏**。

## 项目说明

- 方案设计、测试与维护：**Lyugo**
- 开发与调试过程中使用了 **Codex**、**DeepSeek Harness** 等 AI 工具辅助代码分析与实现
- 项目目前属于个人实验性质，不保证适用于所有 HyperOS 4 设备

## License

[MIT License](LICENSE)
