# ChatGPT Network Banner v1.2.0

Target: ChatGPT Android `1.2026.293` (`versionCode 2629319`)

Package: `com.openai.chatgpt`

This release targets only the certificate-failure UI state. It does not modify
SSL/TLS verification, network requests, proxy behavior, authentication, chat
state, or retry logic.

## Changes

- Veto the certificate-failure UI projection in `h00.emit(Object, twp)`
- Keep the original certificate detection and error classification intact
- Show an Activity-local banner without overlay permission or a Service
- Support light and dark themes
- Close button and 15-second automatic dismissal
- One banner per Activity to avoid repeated notices
- Resolve the current Activity on demand and reuse its banner controller
- No Compose View tree polling or sidebar hooks
- libxposed API 102 with scope limited to `com.openai.chatgpt`

## Hooks

- `com.openai.chatgpt.MainActivity#onCreate(Bundle)`
- `h00#emit(Object, twp)`

## Verification

The APK passes package/version checks, has no Android permissions, uses APK
Signature Scheme v3, and contains no production debug trigger. Real certificate
error logs confirm the projection veto, controller creation, and visible banner.

The banner position follows the target build's top control band. Re-verify the
position after a layout-changing ChatGPT update.
