# FloatingWindowDemo 悬浮窗演示

一个使用 **Kotlin** 编写的 Android 悬浮窗（Floating Window / Overlay）示例项目。
应用启动后即可申请悬浮窗权限并显示一个可拖动的悬浮窗，覆盖 Android 6.0 ~ Android 14 的兼容处理。

## ✨ 功能

- 启动应用后检查悬浮窗权限，未授权时引导用户前往系统设置开启
- 已授权时**自动显示悬浮窗**（前台服务承载）
- 悬浮窗支持**手指拖动**移动位置
- 悬浮窗带关闭按钮（✕）
- 前台服务常驻通知，避免被系统回收
- 覆盖 Android 6.0（API 23）~ Android 14（API 34）的权限与窗口类型适配

## 📱 Android 悬浮窗权限变更梳理

悬浮窗权限 `SYSTEM_ALERT_WINDOW` 在各 Android 版本中的行为差异较大，本项目已针对性处理：

| Android 版本 | 关键变更 | 本项目处理 |
|:---|:---|:---|
| **6.0 (API 23)** | `SYSTEM_ALERT_WINDOW` 成为运行时权限，但**不走 `requestPermissions()`**，必须跳 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 手动开启 | `OverlayPermission.requestOverlayPermission()` 跳转系统设置页 |
| **8.0 (API 26)** | 引入 `TYPE_APPLICATION_OVERLAY`，废弃 `TYPE_PHONE` / `TYPE_SYSTEM_ALERT` | `overlayWindowType()` 按 SDK 版本选择窗口类型 |
| **9+ (API 28+)** | 前台服务需声明 `FOREGROUND_SERVICE` | Manifest 已声明 |
| **10 (API 29)** | `TYPE_APPLICATION_OVERLAY` 窗口**禁止直接获取焦点**；`canDrawOverlays()` 返回 true 也不保证能显示 | 添加 `FLAG_NOT_FOCUSABLE`；`addView` 加 try-catch 兜底 |
| **12 (API 31)** | 权限列表中 `SYSTEM_ALERT_WINDOW` 默认不再展示；前台服务启动受限 | 使用前台服务 + 常驻通知 |
| **13 (API 33)** | 通知需 `POST_NOTIFICATIONS` 权限 | Manifest 已声明 |
| **14 (API 34)** | 前台服务必须声明具体类型 `foregroundServiceType` | 使用 `specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE` |

### 国产 ROM 适配

- **MIUI**：需在「安全中心 → 权限管理」额外开启「后台弹出界面」，否则 `TYPE_APPLICATION_OVERLAY` 窗口会静默创建失败。
- **ColorOS**：类似限制，代码中通过 `OverlayPermission.isMiui()` / `isColorOs()` 识别 ROM 以便扩展处理。

## 🏗️ 项目结构

```
FloatingWindowDemo/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/floatingwindow/
│       │   ├── MainActivity.kt           # 主界面：权限申请与启动服务
│       │   ├── FloatingWindowService.kt  # 悬浮窗前台服务（核心）
│       │   └── OverlayPermission.kt      # 权限检查与 ROM 适配工具
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── layout_floating_window.xml
│           ├── drawable/bg_floating.xml
│           ├── mipmap-*/                 # 各密度应用图标
│           └── values/                   # strings / themes / colors
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .github/workflows/build.yml           # GitHub Actions 云端构建
```

## 🔧 构建

由于本地 Android 环境受限，本项目推荐使用 **GitHub Actions 云端构建**：

1. 推送代码到 `main` / `master` 分支，或手动触发 `workflow_dispatch`
2. 等待 Actions 完成
3. 在 Actions 运行详情页的 **Artifacts** 中下载：
   - `app-debug`：调试版 APK（可直接安装）
   - `app-release-unsigned`：未签名 Release APK

### 本地构建（需 Android SDK）

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 📄 技术要点

**窗口参数核心配置：**

```kotlin
val params = WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    overlayWindowType(),   // API 26+ 用 TYPE_APPLICATION_OVERLAY
    FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = 80; y = 300
}
```

**权限检查（组合方式，避免单一判断失效）：**

```kotlin
Settings.canDrawOverlays(context)  // 基础检查
// + addView 失败的 try-catch 兜底（应对 ROM 层拦截）
```

## License

MIT
