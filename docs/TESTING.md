# Testing

## Build checks

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

已通过：

- `javac --release 8`；
- aapt2 资源链接；
- D8 单 dex 生成；
- APK Signature Scheme v3；
- `aapt2 dump badging`：`versionCode=6`、`versionName=1.2.0`；
- `aapt2 dump permissions`：没有额外权限；
- 正式 DEX 不含 `chatgpt_banner_debug_trigger`、`Settings.Global` 或
  `ContentObserver`；
- 正式 DEX 不含侧边栏 Hook 或场景重建 Hook；
- 正式 APK 包含 classes.dex、module.prop、scope.list 和 java_init.list。

## Device checks

设备：Redmi K70，Android 16，序列号 `f5b8b7e`。

已完成：

- ADB 安装成功；
- 系统记录 `versionCode=6`、`versionName=1.2.0`；
- ChatGPT 包存在且可启动。
- 调试包日志确认 `UI projection hook installed`；
- 真实 SSL 错误日志确认 `certificate UI projection vetoed`；
- 真实 SSL 错误日志确认 `certificate banner request: controller=true`；
- 真实 SSL 错误日志确认 `banner visible`；
- 调试触发已实测 Banner 可见，左对齐；
- 15 秒后 Banner 实测变为 `GONE`。

> 本次重装清除了旧的 LSPosed 启用状态和 Scope。需要在 LSPosed 中重新启用
> 模块，只勾选 `com.openai.chatgpt`，再强制停止 ChatGPT 后重新打开。

## Debug banner trigger

只使用 `-debug` APK 时，先进入普通聊天页，再执行：

```powershell
& 'D:\develop\platform-tools-latest-windows\platform-tools\adb.exe' -s f5b8b7e shell settings put global chatgpt_banner_debug_trigger 0
& 'D:\develop\platform-tools-latest-windows\platform-tools\adb.exe' -s f5b8b7e shell settings put global chatgpt_banner_debug_trigger 1
```

该触发只验证 Banner UI，不伪造 SSL 错误。结束后删除：

```powershell
& 'D:\develop\platform-tools-latest-windows\platform-tools\adb.exe' -s f5b8b7e shell settings delete global chatgpt_banner_debug_trigger
```

## Runtime acceptance

真实证书错误路径已确认 UI 状态投影被跳过，Banner 出现在当前聊天 Activity
的 DecorView 中。构建、安装和日志命中不能替代最终视觉检查；截图与有效 View
层级应按目标 ChatGPT 版本重新记录。
