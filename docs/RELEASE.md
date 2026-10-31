# Release

## 1.1.0

- 目标 ChatGPT `1.2026.293`。
- 将独立全屏证书错误目的地替换为 Activity 内轻提示。
- 保留原版网络检测、SSL 校验和聊天业务逻辑。
- 侧边栏打开立即隐藏，关闭不恢复旧提醒。
- 15 秒自动消失、`×` 手动关闭、同一异常去重。
- 正式 APK 不包含调试触发路径。

## Build

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

默认工具位置为 `D:\develop\android-sdk` 和 `D:\Java17`；可通过环境变量覆盖。

产物：

- `dist/ChatGPTNetworkBanner-1.1.0.apk`
- `dist/ChatGPTNetworkBanner-1.1.0-debug.apk`

发布时只保留正式 APK；debug 包用于 UI 自测，不进入 Release。

本次交付 APK：

- 文件：`dist/ChatGPTNetworkBanner-1.1.0.apk`
- SHA-256：
  `F6868EF38A25ABDAC879F6410894C491E955855A4281E8A6793714983578E7BC`

## Verify

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verify-build.ps1
```

脚本确认包名、版本、Scope 文件、无额外权限、APK v3 签名，以及正式 DEX
不含调试键、`Settings.Global` 或 `ContentObserver`。脚本同时输出 SHA-256。
