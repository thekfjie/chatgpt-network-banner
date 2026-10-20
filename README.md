# ChatGPT Network Banner

一个只针对 ChatGPT Android 指定 SSL“网络配置问题”错误页的 LSPosed
模块。模块只改变该页面的 Compose 根 UI 渲染，不改变网络检测、SSL/TLS 校验、
请求、认证或聊天业务状态。

## 重要边界

这是针对特定 ChatGPT 版本的 UI 模块，不是通用网络修复工具。目标页面没有
同会话聊天状态可复用时，模块不伪造状态，正确放行原版全屏页面；因此当前
实现仍是 UI-only 候选方案，真实 SSL 错误下的完整聊天复用尚未完成真机验收。

## 目标与产物

- ChatGPT `1.2026.279`，`versionCode 2627911`
- 目标包名 `com.openai.chatgpt`
- 目标 APK SHA-256
  `A30AB9145DA62893CD1C2A3CCEE7D05764110908FF4819DE0D7E9E8AA222055E`
- libxposed API 102
- 模块 `versionCode 4`，`versionName 1.0.3`
- 安装包 `dist/ChatGPTNetworkBanner-1.0.3.apk`
- 最终 APK SHA-256
  `59DE70B08860FA0DE9A3634834E81E320C1539C18E45C043B8C6F4F5234C62CC`

调试触发用的 `-debug` APK 由本地构建脚本按需生成，不随 Release 发布；它只在
ChatGPT 进程内注册一个 `Settings.Global` 观察器，用于人工触发 Banner。
正式包不包含该观察器和调试键。正常安装请使用不带 `-debug` 的 APK。

APK 中的混淆类名只匹配上述目标版本。ChatGPT 更新后，如果 Hook 定位失败，
模块会保留原行为，不会猜测替代类名。

## 页面与 Hook 结论

目标文案和页面来自真实 APK 的 JADX 分析与资源核对：

- 标题资源 `f46` 为 `Network configuration issue`
- SSL 提示资源 `f47`，用户截图显示为中文
- 按钮资源 `f45` 为 `Learn more`
- 页面语义名为 `UntrustedNetworkCertificateScreen`

该页面不是 Activity 或 Dialog，而是 MainActivity 内 Jetpack Compose 的独立
导航根状态 `a9p0`；聊天页根状态为 `p8p0`。这两个类位于 APK 的默认包。
JADX 把它们显示为 `defpackage`，但运行时类名没有 `defpackage.` 前缀。

错误来源链路为 `x7d0.a(...)` 检测到
`SSLHandshakeException/CertificateException` 后，把 `f7d0.c` 写成
`c7d0(a7d0)`；`u6p0.d(...)` 再把该状态映射成 `a9p0`。这条链路只更新
证书提示状态；静态路径中没有在 `x7d0` 内重置 `var0.u` 的 `z8r0`
AuthSession 根状态，但整个错误处理链的其他业务状态仍需动态确认。

模块共有两个 Hook：

### 1. `com.openai.chatgpt.MainActivity#onCreate(Bundle)`

只在目标 MainActivity 创建后建立应用内 Banner Controller。Banner 挂到该
Activity 的 DecorView，不使用系统悬浮窗，不增加权限、Service 或后台进程。

### 2. `s511#f(z8r0, c9p0, ...)`

实际签名为：

```text
(z8r0, c9p0, jap0, qd40, Function2, rdc0, bqt, Function2, s2n, int)
```

处理规则：

- 普通聊天 `p8p0` 出现时，缓存这个 Compose 根状态，并关联
  `z8r0.d` 的 AuthSession 标识。
- 目标是 `a9p0` 且存在同一 AuthSession 的聊天状态时，只替换本次
  `chain.proceed(...)` 的 Compose 根状态参数，仍保留原来的 `z8r0` 和
  其他参数。
- 不写回 `a9p0`、不修改原导航状态、不伪造网络正常、不拦截 SSL 结果。
- 没有同会话聊天状态时，不创建 Banner，并严格放行原版 `a9p0` 渲染；最终
  呈现尚未完成真机验证。这是当前 UI-only Hook 已知的限制，不能通过伪造
  正常网络状态或改写业务状态来消除。
- 同一持续异常只创建一次 Banner 事件；回到普通根状态后清理异常周期。

## Banner

- 左侧 `16dp`。优先按内容描述 `f40`（`Navigation menu`）定位真实菜单按钮，
  放在按钮下方约 `10dp`；找不到该 View 时才回退到状态栏高度加 `56dp`。
  Compose 语义节点是否暴露为普通 View，仍需真机确认。
- 紧凑、内容自适应，圆角 `12dp`，带网络异常图标和 `×` 按钮。
- 深色与浅色模式使用不同背景、边框和文字颜色。
- 文案：`网络连接异常，请检查网络`。
- 点击 `×` 立即关闭；未手动关闭时显示约 `15 秒` 后自动消失。
- 没有常驻轮询、全局网络 Hook 或额外后台任务。
- 侧边栏不参与显示判断；侧边栏打开期间 Banner 仍可以留在聊天页 Activity 内。
  这是为了保持两个 Hook 的最小实现而接受的折中。
- 进入设置、项目或其他非聊天根页面时隐藏。

## 安全边界

模块没有 Hook 或修改：

- SSL/TLS 证书检查、`TrustManager`、Cronet 或 OkHttp 证书验证
- 网络错误对象或网络恢复判定
- API 请求、响应、代理和重试
- 登录、认证、账号状态
- 会话、消息、聊天数据和业务逻辑
- 其他错误页面、Activity 导航或 Drawer 开关动画

证书错误仍由 ChatGPT 原逻辑产生。模块仅在这个特定 Compose 根页面渲染时
替换 UI 入参；安全检测和失败处理结果保持原样。

## 验证状态与已知限制

Redmi K70 / Android 16 上曾完成一次受限的调试验证：模块能注入目标进程，
`s511`、`p8p0`、`a9p0` 能从目标 ClassLoader 加载，根渲染 Hook 能安装，
调试触发器能收到设置变化。之后按用户要求卸载了测试模块并恢复了设备状态。

这不等同于真实 SSL 页面验收。静态分析可以确认页面类型、Hook 签名和状态
对象，但不能单独证明 Compose 替换根状态后一定完整复用聊天输入和交互。
当前仍未完成：

1. 目标 SSL 错误出现后不再覆盖整个聊天画面。
2. 聊天输入区、历史消息和原聊天状态仍可正常使用。
3. 侧边栏打开期间 Banner 不额外隐藏，这是已接受的妥协行为。
4. 同一持续错误不会连续刷屏，`×` 和 15 秒自动消失均有效。
5. 网络恢复后，原应用机制继续工作。
6. 设置、项目及其他页面不受影响。
7. 深色/浅色及系统 inset 下的位置和尺寸正确。
8. 禁用模块后恢复原全屏页面。

如果目标错误出现时没有同会话聊天状态，模块不会创建 Banner，仍走原版
`a9p0` 渲染路径。静态代码明确会把 `a9p0` 映射到独立的导航根状态
`dou.INSTANCE`，但该路径的最终屏幕效果尚未验证。在缺少有效 `p8p0` 时
直接传 `null` 或凭空构造聊天状态，都会影响后续导航/业务参数，因此没有采用
这两种做法。这个边界仍可能出现原全屏页，属于尚未闭环的 UI 限制，不应通过
伪造网络正常或修改 SSL 判定来规避。

即使存在缓存的 `p8p0`，也不能仅凭 `s511.f` 的参数替换声明聊天页一定完整
恢复：原链路在证书状态下固定生成 `a9p0`，而后续 `nui0.a(...)` 仍按同一个
根页面参数决定导航身份。模块没有改写这条业务状态链，所以当前只把它视为
UI-only 候选实现；真实 SSL 错误下的可交互性和是否仍被全屏页覆盖必须由
受限动态测试确认。

## 主动调试触发

先安装带 `-debug` 的 APK，在 LSPosed 中只勾选
`com.openai.chatgpt`，然后强制停止并重新启动 ChatGPT。进入普通聊天页后
执行：

```powershell
adb shell settings put global chatgpt_banner_debug_trigger 0
adb shell settings put global chatgpt_banner_debug_trigger 1
```

把值从 `0` 改为 `1` 会触发一次 Banner。观察完成后可执行：

```powershell
adb shell settings put global chatgpt_banner_debug_trigger 0
```

正式测试结束后删除临时设置：

```powershell
adb shell settings delete global chatgpt_banner_debug_trigger
```

该触发只验证 Banner 的创建、定位、关闭、自动消失和样式，不会
伪造 SSL 错误，也不能证明真实证书错误页面已经被替换。真实 Hook 仍需在受控
证书错误网络下验证。

### 建议的验证顺序

1. 先验证 Banner 本身：触发一次后检查位置、文案、图标和主题；等待约 15 秒
   确认自动消失。
2. 再次触发后点击 `×`，确认立即关闭且不影响聊天输入。
3. 再次触发后打开左侧历史侧边栏，确认 Banner 不额外隐藏；关闭侧边栏时
   也不会重新计时或产生第二次提醒。
4. 触发后进入设置或其他非聊天页面，确认 Banner 不显示。
5. 最后在受控网络下让 ChatGPT 原逻辑实际产生目标 SSL 错误。只有这一步能
   验证真实 `a9p0` 页面是否被替换，以及原聊天输入区和历史内容是否完整
   可用。

调试键的写入路径只存在于 `-debug` APK；正式包已在 dex 级检查中确认不包含
该键、`ContentObserver` 或 `Settings.Global` 调用。

## 安装

1. 安装 `dist/ChatGPTNetworkBanner-1.0.3.apk`。
2. 在 LSPosed 中启用 `ChatGPT Network Banner`。
3. Scope 只勾选 `com.openai.chatgpt`。
4. 强制停止并重新启动 ChatGPT。
5. 用原测试网络触发目标 SSL 页面并完成上面的动态检查。

模块没有设置界面，也不需要额外授权。

## 卸载

在 LSPosed 中停用模块，再从系统应用管理卸载
`com.kfjie.chatgptnetworkbanner`。ChatGPT 不需要重装；卸载或停用后恢复原版
全屏错误页行为。

## 构建

```powershell
Set-Location E:\Code\auto\chatgpt-network-banner
.\build-local.ps1
```

脚本使用 Android 35 platform、build-tools 35.0.0、libxposed API 102 AAR
和项目内本地签名密钥，执行 aapt2、javac、D8、zipalign、apksigner，并在
`dist` 生成可安装 APK。SDK 路径由脚本按项目相对路径解析，签名密钥仅在
本地保留，不进入 Git。

## 静态验证

已完成：

- 目标 APK 资源、错误页状态和聊天根状态链路核对。
- `s511.f` 的参数顺序和唯一业务调用点核对。
- aapt2 链接、javac 编译、D8 单 dex 生成成功。
- APK 包含 `classes.dex`、`META-INF/xposed/java_init.list`、
  `module.prop` 和 `scope.list`。
- scope 为 `com.openai.chatgpt`，无主动申请的权限。
- 模块清单 `versionCode=4`、`versionName=1.0.3`。
- apksigner 验证 APK Signature Scheme v3 通过。
- 正式 DEX 不含 `chatgpt_banner_debug_trigger`；调试 DEX 才包含该键和
  `Settings.Global` 观察器。
- 正式和调试 DEX 都不再包含 `jhw`、`iy1` Drawer Hook 常量。

未完成：真实 SSL 错误下的页面替换、Compose 复用稳定性、聊天输入/历史内容
可交互性、HyperOS 3 实际视觉位置和页面切换的最终运行时表现。离线
构建和基础注入成功都不等同于真实错误页验收通过。

更详细的设计和测试记录见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、
[docs/TESTING.md](docs/TESTING.md) 和 [docs/RELEASE.md](docs/RELEASE.md)。
