# HyperOS 4 Glass Blur

一个面向小米 **HyperOS 4 / Android 17** 的 Xposed / LSPosed 模块，用更通透、低模糊的玻璃材质替换部分原厂“柔光塑料”效果，并改善通知中心玻璃层的显示与裁剪。

> 当前版本：**v6.0**  
> 主要测试设备：**Xiaomi 17（pudding）**  
> 其他机型、系统版本与插件版本可能存在差异，请自行评估风险。

[下载最新 Release](https://github.com/lyugo0306/hyperos4-glass-blur/releases/latest) · [提交 Bug / 兼容性反馈](https://github.com/lyugo0306/hyperos4-glass-blur/issues)

---

## 功能

- 🎴 **统一玻璃模糊**：控制中心卡片、通知卡、通知列表容器、长按管理弹窗等使用更统一的低模糊风格
- 🔳 **通知行复用系统玻璃渲染管线**：调用系统 `NotificationRowGlassEffect` / `RowGlassEffect`，尽量保留原生动画、折叠、堆叠与圆角行为
- ✅ **改善最后一条通知玻璃截断**：对玻璃 SDF 层尺寸进行钳制，使背景更贴近实际可见内容高度
- 🪟 **下拉背景效果统一**：直接下拉通知中心与从控制中心横向切换时使用一致的模糊逻辑
- 🌫️ **整屏背景模糊可调**：默认 50%
- ⚙️ **内置设置页**：参数图形化调整，保存后大部分设置可实时生效
  - 玻璃模糊半径、面板模糊比例
  - 亮度、压暗、折射、烧焦
  - 饱和度、透明度、RGB 色调
  - 边缘厚度、反射、方向光、背景色
  - 通知行玻璃开关、SDF 高度钳制开关
  - 下拉背景缩放强度（0 = 无缩放，1 = 原样，2 = 加倍）

## 技术原理

HyperOS 4 的通知行本身具有 GLASS 渲染路径，但在部分场景会使用 BLUR 材质逻辑。模块主要做三件事：

1. 对通知行调用系统自带的 `NotificationRowGlassEffect.apply()`，复用 `RowBlurEffect` + `RowGlassEffect`；
2. 在需要时保持玻璃材质与玻璃轮廓相关 flag；
3. 对 `setMiGlassSdfMaxSize` 相关尺寸进行约束，使玻璃层尽量与可见内容高度一致。

核心原则是：**尽量调用系统原生玻璃渲染，而不是重新绘制一套独立材质。**

## 安装

1. 从 [Releases](https://github.com/lyugo0306/hyperos4-glass-blur/releases/latest) 下载 APK
2. 安装 APK
3. 在 **Vector** 或兼容的 LSPosed 管理器中启用模块
4. 确认作用域包含：
   - `com.android.systemui`
   - `miui.systemui.plugin`
5. 重启 SystemUI 或重启手机

需要还原时，禁用模块并重启 SystemUI / 手机即可。

## 设置页

v6.0 起提供独立设置页。参数会保存到系统设置中，SystemUI 内的模块通过监听配置变化读取新值。

部分已经渲染的界面可能需要重新展开通知中心，或使用“保存并重启 SystemUI”后才完全刷新。

## 已知限制

- 当前主要在 Xiaomi 17 / pudding 上测试
- HyperOS 4 Beta、SystemUI 插件、ROM 构建差异都可能影响 Hook
- 通知样式、媒体通知、大量通知、折叠/展开动画仍可能出现兼容性问题
- Root / Xposed 类模块始终存在 SystemUI 崩溃或启动异常风险

遇到问题时建议在 Issues 中提供：**机型、系统版本、模块版本、复现步骤、截图或录屏**。

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

构建脚本会自动编译 `app/src` 下的全部 Java 源文件，产物默认输出到：

```text
release/os4-glass-blur-v6.0.apk
```

> 仓库不会保存你的私有签名密钥。公开发布版本建议使用固定的个人 release key，不要把 `.jks` / `.keystore` 上传到 GitHub。

## 目录结构

```text
├── app/
│   ├── AndroidManifest.xml
│   ├── res/
│   ├── meta/                    # Xposed 元数据、作用域、入口
│   └── src/
│       └── dev/codex/os4glassblur/
│           ├── MainHook.java
│           └── SettingsActivity.java
├── release/                     # 发布 APK（可选）
├── build.ps1                    # PowerShell 一键构建脚本
├── .gitignore
├── LICENSE
└── README.md
```

## 项目说明

- 方案设计、测试与维护：**Lyugo**
- 开发与调试过程中使用了 **Codex**、**DeepSeek Harness** 等 AI 工具辅助代码分析与实现
- 项目目前属于个人实验性质，不保证适用于所有 HyperOS 4 设备

## License

[MIT License](LICENSE)
