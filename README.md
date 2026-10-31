# ChatGPT Network Banner

一个只处理 ChatGPT Android 指定 SSL 证书错误页的 LSPosed 模块。它保留原版
网络检测、证书校验、请求、认证和聊天状态，只把该页面的全屏 UI 替换为 Activity
内的轻量提示。

## 目标与构建

- ChatGPT `1.2026.293`，`versionCode 2629319`
- 包名 `com.openai.chatgpt`
- 设备：Redmi K70，Android 16 / HyperOS 3
- LSPosed：2.2.1；libxposed API 102
- 模块：`versionCode 5`，`versionName 1.1.0`

模块只声明 `com.openai.chatgpt` 作用域，不申请系统悬浮窗或其他权限。

## 实际 Hook

以下类名来自真实 APK 的 JADX 分析，不是猜测：

| 位置 | 签名 | 用途 |
| --- | --- | --- |
| `com.openai.chatgpt.MainActivity` | `onCreate(Bundle)` | 创建 DecorView 内的 Banner |
| `p000.vqq0` | `a(a96, List): ksr0` | 仅对 `p000.xnv` 路由保留前一页场景 |
| `p000.dlr` | `invoke(Object, Object, Object): Object` | 目标证书页内容返回 `Unit` |
| `p000.w52` | `m56566g(Object): void` | 同步侧边栏打开/关闭状态 |

Banner 通过菜单语义节点定位：左侧 `16dp`，菜单按钮下方约 `10dp`。进入侧边栏
时立即隐藏，关闭侧边栏不恢复旧提醒；其他导航页面不显示。提醒可点击 `×`
关闭，未点击时约 15 秒自动消失。

## 拦截原理

目标错误仍是原版导航目的地 `p000.xnv`，证书错误对象仍由 ChatGPT 原逻辑产生。
模块只在导航“场景组合”（Compose navigation scene，不是系统调度软件 Scene）
和该目的地的绘制入口做 UI 层处理：

1. 只当路由是 `Untrusted Network Certificate`、原场景是 `fpu0`
   且导航元数据允许保留前一页时，构造前一页保留场景。
2. 只在目的地内容入口识别到标准证书状态
   `hle0(true, false, false, null)` 时返回 `Unit`，跳过全屏内容。
3. 不改错误对象、SSL 判定、网络请求、代理、重试或聊天消息。

普通网络错误和其他错误页面继续走原版渲染。模块禁用或卸载后恢复原行为。

## 多版本策略

当前真正完成符号核对的目标是 `1.2026.293`。旧版 `1.2026.279` 的根渲染签名
已知，但它没有同一套新版导航符号证据，因此不把它伪装成已验证候选。

代码已把版本绑定做成候选表。未来加入新版本时，应只追加经 JADX 或 DEX 实测
通过的整组签名；绑定阶段会验证路由语义、场景构造器、目的地和抽屉 setter
的完整形状。结构不匹配时完全不安装相关 Hook，原版 UI 保持不变。

## 安装

1. 安装 `ChatGPTNetworkBanner-1.1.0.apk`。
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

- 真实 APK 的页面、路由、场景、目的地和抽屉符号核对；
- `javac`、aapt2、D8、APK Signature Scheme v3 通过；
- `versionCode=5`、`versionName=1.1.0`，无额外权限；
- 正式 DEX 不含调试触发键或 `Settings.Global`；
- ADB 安装与系统版本 `versionCode=5`、`versionName=1.1.0` 查询通过。

尚未完成：

- LSPosed 作用域重新勾选后的运行日志确认；
- 在受控 SSL 错误网络上验证目标页面被替换；
- 真机聊天输入、历史、侧边栏动画和网络恢复的完整验收。

离线构建、签名和安装通过不等于真实 SSL 页面验收通过。

更详细的设计与测试记录见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、
[docs/TESTING.md](docs/TESTING.md) 和 [docs/RELEASE.md](docs/RELEASE.md)。
