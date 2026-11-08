# Changelog

## Unreleased

### Added

- Clear cache entry in the options menu; the page cache is also erased when the app is closed normally.
- Obtainium badge and Downloader code at the top of the README.
- Search engine choice (Google, DuckDuckGo, Bing, Brave); queries use the selected engine.
- Voice search using the speech recognizer installed on the device (audio is processed by that recognizer).
- Regional filter list choice (automatic, Portuguese, Spanish, none) in Menu > Block ads and trackers > Regional list.
- Per-site exception toggle ("Filter on this site") under Menu > Block ads and trackers, lasting for the session.
- Menu status indicators: "loading lists" during compilation and "basic list only" when running on fallback rules.
- Clear data menu: cache only, cookies and site data only, or all three together.

### Changed

- D-pad moves focus on the home screen, in menus and in the toolbar, and moves the pointer on web pages; up at the top of a page focuses the address bar and down leaves it again.
- The analog stick moves the pointer only over web pages; it is ignored on the home screen, in menus and in the toolbar.
- Up to 4 favorites are shown on the home screen to fit without scrolling; favorites beyond 4 remain stored.
- Preferences and the regional filter list choice are stored in private app storage.
- CI builds the release variant with R8, checks ABI splits and the JNI library and writes `SHA256SUMS.txt`.
- Workflows pin Rust 1.99.1, cargo-ndk 4.1.2, each action by commit SHA and the Gradle wrapper checksum.
- A `v*` tag publishes the signed `WebMini-*.apk` files and `SHA256SUMS.txt` through `.github/workflows/release.yml`.

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
