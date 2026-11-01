# ChatGPT Network Banner

一个只处理 ChatGPT Android 指定 SSL 证书错误页的 LSPosed 模块。它保留原版
网络检测、证书校验、请求、认证和聊天状态。目标证书错误仍由原版逻辑产生，
模块只阻止该错误状态投影成全屏导航页，并显示 Activity 内的轻量提示。

## 目标与构建

- ChatGPT `1.2026.293`，`versionCode 2629319`
- 包名 `com.openai.chatgpt`
- 设备：Redmi K70，Android 16 / HyperOS 3
- LSPosed：2.2.1；libxposed API 102
- 模块：`versionCode 6`，`versionName 1.2.0`

模块只声明 `com.openai.chatgpt` 作用域，不申请系统悬浮窗或其他权限。

## 实际 Hook

以下类名来自真实 APK 的 JADX 分析，不是猜测：

| 位置 | 签名 | 用途 |
| --- | --- | --- |
| `com.openai.chatgpt.MainActivity` | `onCreate(Bundle)` | 创建 DecorView 内的 Banner |
| `h00` | `emit(Object, twp): Object` | 仅 veto 证书失败的 UI 状态投影，并请求 Banner |

Banner 左对齐 `16dp`，位于正常聊天页顶部菜单区域下方约 `10dp`。提醒可点击
`×` 关闭，未点击时 15 秒自动消失，同一 Activity 内不重复弹出。模块不读取、
拦截或控制侧边栏状态。

## 拦截原理

证书错误对象仍由 ChatGPT 原逻辑产生。模块只在错误状态投影到 UI 之前做处理：

1. `gme0.a` 的证书检测、`jle0` 错误构造和异常链均保持原样。
2. `h00.emit` 遇到 `txq0` 中的 `jle0` 状态时返回 Kotlin Unit，不生成错误页。
3. Banner Controller 只向当前 Activity DecorView 添加一个普通 View。
4. 不改错误对象、SSL 判定、网络请求、代理、重试或聊天消息。

普通网络错误和其他错误页面继续走原版渲染。模块禁用或卸载后恢复原行为。

## 多版本策略

当前真正完成运行验证的目标是 `1.2026.293`。

代码已把版本绑定做成候选表。未来加入新版本时，应只追加经 JADX 或 DEX 实测
通过的整组签名；绑定阶段会验证状态字段、错误继承关系和 Unit 形状。结构不匹配时
不安装相关 Hook，原版 UI 保持不变。

## 安装

1. 安装 `ChatGPTNetworkBanner-1.2.0.apk`。
2. 在 LSPosed 中启用模块。
3. 作用域只勾选 `com.openai.chatgpt`。
4. 强制停止并重新启动 ChatGPT。

模块没有设置界面，不需要额外授权。

## 卸载

在 LSPosed 中停用模块，再从系统应用管理卸载
`com.kfjie.chatgptnetworkbanner`。ChatGPT 不需要卸载；停用或卸载后恢复原版
全屏错误页。

## 构建

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

脚本默认使用 `D:\develop\android-sdk` 的 Android platform 35、build-tools 35，
以及 `D:\Java17`；也可通过 `ANDROID_SDK_ROOT`、`ANDROID_HOME`、
`ANDROID_BUILD_TOOLS`、`JAVA_HOME17` 或 `JAVA_HOME` 覆盖。构建执行
aapt2、javac、D8、zipalign 和 apksigner，产物位于 `dist`。开发工具、目标
APK、反编译结果和签名文件不进入 Git。

## 验证状态

已完成：

- 真实 APK 的页面、路由、目的地和 Unit 符号核对；
- `h00.emit` 的真实证书错误链路已命中并成功 veto；
- `javac`、aapt2、D8、APK Signature Scheme v3 通过；
- `versionCode=6`、`versionName=1.2.0`，无额外权限；
- 正式 DEX 不含调试触发键或 `Settings.Global`；
- 正式 DEX 不包含侧边栏 Hook；
- ADB 安装与系统版本 `versionCode=6`、`versionName=1.2.0` 查询通过；
- debug 包在真实证书错误下确认控制器创建和 Banner 可见；
- `×` 关闭与 15 秒自动消失已实测。

已知限制：

- Banner 位置按 ChatGPT 当前聊天页顶部 `56dp` 控件带估算，不扫描 Compose
  语义树；目标版本已实测，其他布局变体需要重新验收；
- 侧边栏打开时 Banner 不会立即隐藏；用户已明确接受该简化；
- 同一 Activity 内只提示一次，重建 Activity 后可再次提示。

更详细的设计与测试记录见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、
[docs/TESTING.md](docs/TESTING.md) 和 [docs/RELEASE.md](docs/RELEASE.md)。
