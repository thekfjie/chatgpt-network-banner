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
- `aapt2 dump badging`：`versionCode=5`、`versionName=1.1.0`；
- `aapt2 dump permissions`：没有额外权限；
- 正式 DEX 不含 `chatgpt_banner_debug_trigger`、`Settings.Global` 或
  `ContentObserver`；
- 正式 APK 包含 classes.dex、module.prop、scope.list 和 java_init.list。

## Device checks

设备：Redmi K70，Android 16，序列号 `f5b8b7e`。

已完成：

- ADB 安装成功；
- 系统记录 `versionCode=5`、`versionName=1.1.0`；
- 当前安装包的 APK SHA-256 为
  `8E1416C486A246AC4248F29147AF73F3512602FD318A28F66F200EDDD39C96C8`；
- ChatGPT 包存在且可启动。

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

尚未完成：

- LSPosed 作用域重新勾选后的 Hook 安装日志；
- 受控 SSL 错误下目标页面被替换；
- 聊天输入、历史、侧边栏和网络恢复的完整交互；
- 深色/浅色、HyperOS 3 最终位置和多窗口行为。

构建和安装成功不能被表述为真实 SSL 页面验收通过。
