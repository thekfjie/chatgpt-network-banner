# Architecture

## 目标

只在 ChatGPT `1.2026.279` 出现指定证书错误根状态 `a9p0` 时，尝试用同一
认证会话中已缓存的聊天根状态 `p8p0` 渲染当前 Compose 根，并额外显示一个
Activity 内 Banner。网络检测、SSL 校验和错误处理链保持原样。

## 两个 Hook

1. `com.openai.chatgpt.MainActivity#onCreate(Bundle)`
   - 在目标 Activity 创建后建立 Banner Controller。
   - Banner 添加到 Activity DecorView，不使用系统悬浮窗。
2. `s511#f(z8r0, c9p0, jap0, qd40, Function2, rdc0, bqt, Function2, s2n, int)`
   - 观察根状态参数。
   - `p8p0` 作为聊天状态缓存，并用 `z8r0.d` 关联认证会话。
   - `a9p0` 且同会话聊天状态有效时，只替换该次渲染调用的根状态参数。

没有 Drawer、网络、TLS、认证、请求或通用生命周期 Hook。

## 状态与去重

- `lastChatState` / `lastChatSessionId` 保存可用于 UI 投影的聊天状态和会话标识。
- `certificateCycle` 表示一次连续的证书错误周期。
- `bannerShownForCycle` 保证同一周期只创建一次 Banner 事件。
- 根状态回到普通聊天后清理周期，避免持续刷屏。
- `BannerController` 的单个 `hideRunnable` 负责 15 秒自动关闭；点击 `×` 会
  清除该回调并立即隐藏。

## 安全边界

不修改以下内容：

- `TrustManager`、Cronet、OkHttp 或任何证书验证逻辑
- SSL 异常产生、错误对象、重试和网络恢复判断
- API 请求和响应、代理、认证、聊天消息与会话业务状态
- 原版导航或 Drawer 动画

`a9p0` 仍由原业务链产生。模块只是在 UI 渲染入口尝试投影已经存在的
`p8p0`，不凭空制造聊天状态，也不伪造网络正常。

## 已知限制

没有有效同会话 `p8p0` 时严格放行原版 `a9p0`，所以原全屏页仍可能出现。
传 `null` 或构造假状态会影响后续导航和业务参数，因此没有采用。

即使存在缓存 `p8p0`，也不能只凭参数替换证明聊天输入、历史消息和导航全部
恢复；这必须依赖受控真实 SSL 错误动态验收。Banner 的侧边栏状态不参与
显示判断，侧边栏打开期间可能继续存在，这是当前最小 Hook 方案的已知妥协。

## 版本耦合

混淆类名和 `s511` 方法签名只对目标版本成立。ChatGPT 升级后如果定位失败，
模块会放行原行为，不会猜测替代类名。
