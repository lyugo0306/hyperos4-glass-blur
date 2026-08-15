# OS4 玻璃模式统一低模糊

一个用于小米 **HyperOS / OS4**（Android 17）的 Xposed/LSPosed 模块，把控制中心与通知中心的玻璃材质统一为通透的低模糊风格，并修复通知列表最后一条玻璃背景被硬切的问题。

> ⚠️ 仅适配作者机型（Xiaomi 17 / pudding）与上述系统版本，其他机型/版本不保证有效。

---

## 功能一览

- 🎴 **玻璃模糊统一 40/40**：控制中心卡片、通知卡、通知列表容器、长按管理弹窗、窗口下拉动画全部统一（原厂 110~130 参差不齐）
- 🔳 **通知卡玻璃由系统渲染管线完成**：直接调用系统自己的玻璃渲染逻辑（`NotificationRowGlassEffect` 全套：模糊模式、混色、圆角、阴影），动画/折叠/堆叠兼容性与系统原生一致
- 🪟 **两种下拉手势模糊一致**：直接下拉通知中心 / 从控制中心滑过去，效果完全一致
- 🌫️ **整屏背景模糊可调**：默认 50%，更通透
- ⚙️ **内置设置页**：所有参数图形化调节，保存即实时生效（无需重启）
  - 玻璃模糊半径、面板模糊比例、材质微调（亮度/压暗/折射/烧焦）
  - 高级材质参数（饱和度/透明度/RGB 色调/边缘厚度/反射/方向光/背景色）
  - 通知行玻璃开关、玻璃层高度钳制开关
  - **下拉背景缩放强度**（0=无缩放，1=原样，2=加倍）
- ✅ **修复最后一条通知玻璃截断**：系统会把最后一条的背景视图拉伸到列表底部，且非"玻璃"材质模式下不收缩玻璃渲染层；模块复用系统管线并把玻璃层尺寸与内容高度精确对齐，圆角完整

## 技术原理（简述）

系统全局材质通常为 BLUR（非 GLASS），此时通知行走普通材质、且 `NotificationBackgroundViewInjectorImpl.updateActualHeight()` 不会收缩玻璃 SDF 层。模块做的三件事：

1. 对通知行主动调用系统自带的 `NotificationRowGlassEffect.apply()`（内部完整跑 `RowBlurEffect` + `RowGlassEffect`），复用全部系统逻辑；
2. 拦截 `setMiViewMaterialType` / `setMiBackgroundBlurEnhanceFlag`，把行锁定为玻璃材质并保持玻璃轮廓 flag（系统在 BLUR 材质下会清掉它们）；
3. 拦截 `setMiGlassSdfMaxSize`，把玻璃层尺寸钳制到可见内容高度（系统只在 GLASS 材质下做这件事）。

## 安装

1. 下载 [release/](release/) 中的 APK
2. 在 **Vector**（或 LSPosed）中启用模块 `dev.codex.os4glassblur`
3. 勾选作用域：`com.android.systemui` 与 `miui.systemui.plugin`（模块内已固定）
4. 重启 SystemUI（开发者选项 → 重启 SystemUI）或重启手机
5. 关闭模块重启即可完全还原

## 自行构建

需要 JDK 8+ 与 Android SDK Build Tools 37.0.0、`libxposed-api-102`（框架 API jar）以及 `android-37` platform。

```powershell
# 修改 build.ps1 顶部的工具路径后执行
.\build.ps1
```

产物输出到 `release/os4-glass-blur-v4.0.apk`（已签名，使用 debug keystore）。

## 目录结构

```
├── app/
│   ├── AndroidManifest.xml      # 模块清单（versionCode 32 / 4.0）
│   ├── res/                     # 资源（应用名等）
│   ├── meta/                    # Xposed 元数据（作用域、入口）
│   └── src/                     # 模块源码（MainHook.java）
├── release/                     # 构建产物 APK
├── build.ps1                    # 一键构建脚本
└── README.md
```

## 调整参数

所有调参都在 `app/src/dev/codex/os4glassblur/MainHook.java` 顶部附近：

- `GLASS_RADIUS = 40`：统一玻璃模糊半径
- `PANEL_BLUR_PERCENT = 50`：整屏背景模糊比例
- `applySharedMaterialDelta()`：材质微调（亮度/压暗/折射/烧焦）


## 免责声明

- 纯个人分享，无任何收费/广告；Root 模块有风险，折腾需谨慎
- 数值为作者个人口味，不保证适合所有人
- 其他机型/系统版本不保证有效，刷前请备份

## License

[MIT](LICENSE)
