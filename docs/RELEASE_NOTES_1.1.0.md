## ChatGPT Network Banner v1.1.0

Target: ChatGPT Android `1.2026.293` (`versionCode 2629319`)

Package: `com.openai.chatgpt`

This release replaces only the specified full-screen certificate-error
destination with an in-Activity banner. It does not modify SSL/TLS
verification, network requests, proxy behavior, authentication, chat state, or
retry logic.

### Changes

- Native-style banner positioned under the chat menu
- Light and dark theme support
- Close button and 15-second automatic dismissal
- One banner event per continuous certificate-error cycle
- Immediate hiding while the history drawer is open
- No system-overlay permission or background service
- libxposed API 102 with scope limited to `com.openai.chatgpt`

### Hooks

- `com.openai.chatgpt.MainActivity#onCreate(Bundle)`
- `p000.vqq0#a(a96, List)`
- `p000.dlr#invoke(Object, Object, Object)`
- `p000.w52#m56566g(Object)`

### Verification

The APK passes package/version checks, has no Android permissions, uses APK
Signature Scheme v3, and contains no production debug trigger. ADB installation
reports `versionCode=5` and `versionName=1.1.0`.

Real SSL-error rendering, chat interaction after interception, drawer animation,
and network recovery have not yet been verified on a device. A symbol mismatch
leaves the original UI unchanged.

### Install

Install the APK, enable it in LSPosed with scope limited to
`com.openai.chatgpt`, then force-stop and restart ChatGPT.

APK SHA-256:

`8E1416C486A246AC4248F29147AF73F3512602FD318A28F66F200EDDD39C96C8`
