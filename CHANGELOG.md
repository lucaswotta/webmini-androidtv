# Changelog

## 0.2.0 (2026-10-24)

First signed release.

### Security

- HTTP warning now covers pages opened from the address bar, favorites, other apps and confirmed pop-ups; before, only links and redirects were checked.
- App data is excluded from cloud backup, device-to-device transfer and `adb backup`.
- Third-party cookies are refused explicitly.
- Release APKs are signed and not debuggable.

### Changed

- Application ID changed from `br.com.lucaswotta.navegadorleve` to `io.github.lucaswotta.webmini`. Version 0.1.0 installs as a separate app and can be uninstalled.
- Menu options renamed to "Block pop-ups" and "Block ads and trackers".
- APKs per architecture (`armeabi-v7a`, `arm64-v8a`, `x86_64`) plus a universal APK.
- Android TV banner shows the app name; adaptive launcher icon on Android 8.0 and later.

## 0.1.0 (2026-10-23)

Preview with a debug-signed APK.
