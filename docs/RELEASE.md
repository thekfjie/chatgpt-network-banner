# Release

## 版本

- `versionName`: `1.0.3`
- `versionCode`: `4`
- 目标 ChatGPT: `1.2026.279`
- libxposed API: `102`

## 构建

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
.\build-local.ps1
Get-FileHash .\dist\ChatGPTNetworkBanner-1.0.3.apk -Algorithm SHA256
```

本地签名密钥位于 `signing/debug.keystore`，只用于本地构建，已被
`.gitignore` 排除。仓库和 Release 不包含密钥。

## 产物

- 正式包：`dist/ChatGPTNetworkBanner-1.0.3.apk`
- SHA-256:
  `59DE70B08860FA0DE9A3634834E81E320C1539C18E45C043B8C6F4F5234C62CC`

正式二进制通过 GitHub Release 发布，不直接提交到 Git 历史。调试触发包只在
本地按需构建，不随 Release 发布。

## 安装与卸载

安装正式包并在 LSPosed 中只启用 `com.openai.chatgpt` Scope，随后强制停止
并启动 ChatGPT。卸载模块或在 LSPosed 中停用后，无需重装 ChatGPT。

## 发布检查

- 源码和文档不含签名密钥、设备标识、截图或本地缓存。
- Release 附正式 APK 和 SHA-256。
- 说明目标版本和“无同会话聊天状态时回退原全屏页”的限制。
