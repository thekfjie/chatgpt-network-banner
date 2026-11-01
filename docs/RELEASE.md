# Release

## 1.2.0

- 目标 ChatGPT `1.2026.293`。
- 在证书错误状态投影到 UI 前 veto，不再进入全屏错误页。
- 通过当前 `MainActivity` DecorView 显示 Activity 内轻提示。
- 保留原版网络检测、SSL 校验和聊天业务逻辑。
- 不读取、拦截或控制侧边栏状态。
- 15 秒自动消失、`×` 手动关闭、同一异常去重。
- 正式 APK 不包含调试触发路径。

## Build

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

默认工具位置为 `D:\develop\android-sdk` 和 `D:\Java17`；可通过环境变量覆盖。

产物：

- `dist/ChatGPTNetworkBanner-1.2.0.apk`
- `dist/ChatGPTNetworkBanner-1.2.0-debug.apk`

发布时只保留正式 APK；debug 包用于 UI 自测，不进入 Release。

本次交付 APK：

- 文件：`dist/ChatGPTNetworkBanner-1.2.0.apk`
- SHA-256：由 `verify-build.ps1` 输出，避免文档与最终产物漂移。

## Verify

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verify-build.ps1
```

脚本确认包名、版本、Scope 文件、无额外权限、APK v3 签名，以及正式 DEX
不含调试键、`Settings.Global` 或 `ContentObserver`。脚本同时输出 SHA-256。
