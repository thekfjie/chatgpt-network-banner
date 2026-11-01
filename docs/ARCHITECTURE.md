# Architecture

## Scope

模块只处理 ChatGPT `1.2026.293` 中的证书失败 UI 状态。它不改变网络失败
结果或证书判定，只阻止该状态投影成全屏导航页。

## Hooks

### Activity creation

`com.openai.chatgpt.MainActivity#onCreate(Bundle)` 在 Activity 创建后安装一次
Banner Controller。Banner 添加到 Activity DecorView，不使用系统悬浮窗，也不
创建 Service。

### UI projection veto

真实证书错误出现时：

1. `gme0.a` 遍历异常链并识别证书错误；
2. `jle0` 写入 `txq0.h`；
3. `h00.emit(Object, twp)` 把状态投影到 UI。

模块只在第 3 步检查 `txq0.h instanceof jle0`。命中时返回 `a011.a`
（Kotlin Unit），因此不产生全屏错误页状态。异常对象、检测结果和原方法对
其他状态的处理均不变。

## Deduplication

证书错误被重复 emit 时，Banner Controller 在当前 Activity 内只调度一次；
用户关闭或 15 秒结束后，重复回调不会再次弹出。

## Candidate validation

`TARGETS` 是版本候选表。每个候选必须验证：

- `txq0` 错误字段类型及 `jle0` 继承关系；
- `h00.emit(Object, twp)` 的方法形状；
- Kotlin Unit 单例和静态 final 字段。

任一失败都不安装相关 UI Hook。不会根据模糊名称或旧版本类名猜测替代。

## Banner lifecycle

Banner Controller 按需从 `ActivityThread.currentActivity()` 取得当前
`MainActivity`，只创建一次。Banner 是 DecorView 内的普通 `LinearLayout`，
不使用系统悬浮窗、Service 或后台任务。没有持续布局监听，也没有 Compose
View 树轮询。

## Safety boundary

没有 Hook 或修改：

- SSL/TLS、TrustManager、Cronet、OkHttp 或证书判定；
- 网络错误对象、重试、代理、连接恢复；
- API 请求或响应；
- 登录、认证、账号、聊天消息或会话业务；
- 系统悬浮窗、后台服务、侧边栏状态或通用 Activity 生命周期。

## Known limits

最新版本的整组混淆符号是目标构建特有的。其他版本在候选表中没有完整符号
证据时会保持原版 UI，而不是盲目兼容。真实 SSL 错误下的交互和恢复仍需在
受控网络中验收。
