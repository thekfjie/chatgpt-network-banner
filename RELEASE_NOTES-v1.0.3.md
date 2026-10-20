## ChatGPT Network Banner v1.0.3

Target: ChatGPT Android `1.2026.279` (`com.openai.chatgpt`)

This release keeps the two-Hook UI-only design:

- `MainActivity#onCreate(Bundle)`
- `s511#f(...)`

It does not modify SSL/TLS verification, network requests, authentication,
chat state, retry logic, or proxy behavior.

### Banner

- Native-style in-Activity banner under the menu area
- Light and dark theme support
- Close button and 15-second automatic dismissal
- One banner event per continuous certificate-error cycle
- No system overlay permission or background service

### Known limitation

When no same-session chat root state is available, the module intentionally
allows the original full-screen `a9p0` page rather than fabricating chat state
or changing network/security results. Real SSL-error rendering and full
Compose chat-state reuse have not yet been verified on a device. The banner can
remain visible while the history drawer is open; this is an accepted
trade-off for the minimal two-Hook implementation.

### Install

Install the APK, enable it in LSPosed with scope limited to
`com.openai.chatgpt`, then force-stop and restart ChatGPT.

APK SHA-256:

`59DE70B08860FA0DE9A3634834E81E320C1539C18E45C043B8C6F4F5234C62CC`
