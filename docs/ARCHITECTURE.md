# Architecture

## Scope

模块只处理 ChatGPT `1.2026.293` 中的独立 Compose 导航目的地
`Untrusted Network Certificate`。它不改变网络失败结果，只改变该目的地的
UI 组合和绘制。

## Hooks

### Activity creation

`com.openai.chatgpt.MainActivity#onCreate(Bundle)` 在 Activity 创建后安装一次
Banner Controller。Banner 添加到 Activity DecorView，不使用系统悬浮窗，也不
创建 Service。

### Navigation scene

`p000.vqq0.a(a96, List)` 负责选择 Compose 导航场景（这里的 scene 指
Compose navigation scene，不是 Android 调度软件 Scene）。模块先让原逻辑执行，
只在以下条件全部成立时替换场景：

- 最新 entry 的路由是 `p000.xnv`；
- 原场景实际是 `p000.fpu0`；
- 该 entry 的元数据显式带有
  `com.openai.feature.navigation.keepPreviousVisible=true`；
- 候选构造器和字段已经通过完整形状校验。

满足后构造 `p000.uqq0(latestEntry, previousEntries, vjr)`，让前一页继续参与
组合。这里不改业务状态，也不伪造网络正常。

### Destination content

`p000.dlr.invoke(Object, Object, Object)` 在目的地是 `p000.xnv` 且参数状态为
标准 SSL 证书状态 `hle0(true, false, false, null)` 时返回
`p000.a011.f37a`（Kotlin Unit），跳过全屏内容。其他状态直接执行原方法。

### Drawer state

只 Hook `p000.w52.m56566g(Object)`，其内部持有当前 Drawer 状态。值为
`Open` 时立即隐藏 Banner，`Closed` 时只解除状态，不重新显示已关闭的提醒。
不 Hook 通用 `vbj0`，也不改动画、手势或导航状态。

## Deduplication

每次真实证书入口生成一个递增 generation。Banner Controller 只对新的
generation 调度一次显示；同一持续异常不会重复刷新。侧边栏打开时取消显示，
关闭时不会复活旧事件。回到非目标页面后不再显示旧提醒。

## Candidate validation

`TARGETS` 是版本候选表。每个候选必须验证：

- 路由 singleton 的语义名为 `Untrusted Network Certificate`；
- 场景 selector、`uqq0` 构造器、`fpu0` 构造器形状；
- 目的地 invoke、目标字段和 Unit 字段；
- entry 元数据字段；
- Drawer setter 签名；
- 标准证书状态字段类型。

任一失败都不安装相关 UI Hook。不会根据模糊名称或旧版本类名猜测替代。

## Safety boundary

没有 Hook 或修改：

- SSL/TLS、TrustManager、Cronet、OkHttp 或证书判定；
- 网络错误对象、重试、代理、连接恢复；
- API 请求或响应；
- 登录、认证、账号、聊天消息或会话业务；
- 系统悬浮窗、后台服务、Drawer 动画或通用 Activity 生命周期。

## Known limits

最新版本的整组混淆符号是目标构建特有的。其他版本在候选表中没有完整符号
证据时会保持原版 UI，而不是盲目兼容。真实 SSL 错误下的交互和恢复仍需在
受控网络中验收。
