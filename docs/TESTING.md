# Testing

## 构建与静态检查

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
.\build-local.ps1
```

检查项：

- `aapt2 dump badging`：包名、`versionCode=4`、`versionName=1.0.3`、无额外权限。
- `apksigner verify --verbose --print-certs`：APK Signature Scheme v3。
- 正式 DEX 不含 `chatgpt_banner_debug_trigger`、`ContentObserver` 和
  `Settings.Global`。
- 正式 DEX 不含已移除的 `jhw` / `iy1` Drawer Hook。
- 正式 DEX 只保留 `MainActivity#onCreate` 和 `s511.f` 两个 Hook 相关路径。

## 设备安装

```powershell
$adb = 'D:\develop\platform-tools-latest-windows\platform-tools\adb.exe'
& $adb -s f5b8b7e install -r .\dist\ChatGPTNetworkBanner-1.0.3.apk
& $adb -s f5b8b7e shell dumpsys package com.kfjie.chatgptnetworkbanner
```

在 LSPosed 中只勾选 `com.openai.chatgpt`，然后强制停止并重新启动 ChatGPT。

## Banner 调试触发

只有 `-debug` 包注册触发键。替换安装调试包后，在普通聊天页执行：

```powershell
& $adb -s f5b8b7e shell settings put global chatgpt_banner_debug_trigger 0
& $adb -s f5b8b7e shell settings put global chatgpt_banner_debug_trigger 1
```

每次从 `0` 变为 `1` 触发一次 Banner。结束后删除临时键：

```powershell
& $adb -s f5b8b7e shell settings delete global chatgpt_banner_debug_trigger
```

该键只验证 Banner 的创建、位置、主题、点击关闭和 15 秒自动关闭，不伪造
SSL 错误。

## 验收记录

已完成：

- 离线编译、D8、签名和 APK 结构检查通过。
- 正式包已通过 ADB 覆盖安装；系统记录 `versionCode=4`、
  `versionName=1.0.3`。
- 设备环境：Redmi K70，Android 16，ADB 序列 `f5b8b7e`。

未完成：

- 受控网络下真实产生目标 `a9p0` 后的页面替换。
- 缓存 `p8p0` 时聊天消息、输入区、历史和导航的完整可交互性。
- 侧边栏打开期间的视觉保留策略、深色/浅色和 HyperOS 3 最终位置。
- 网络恢复后的完整链路回退。
- 禁用模块后的原版页面对照。

离线构建成功、Hook 能加载和调试 Banner 能显示，都不能替代真实 SSL 错误
验收。
