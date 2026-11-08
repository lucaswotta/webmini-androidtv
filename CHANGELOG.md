# Changelog

## 0.3.1 (2026-11-08)

### Added

- The D-pad reaches the address bar and the toolbar: up at the top of a page focuses the address bar, left and right move between the buttons, down returns to the pointer.
- With 4 favorites saved, the star lists them so one can be replaced; "Remove favorite" in the options menu deletes one.
- Menu panels with more than seven rows scroll, and can carry a title.
- Ad blocking options screenshot in the README.

### Changed

- Toolbar focus is read from the real focus instead of a flag, and leaving the toolbar or the address bar moves focus to the page rather than back to the Home button.
- The analog stick moves the pointer only over web pages.

## 0.3.0 (2026-11-07)

Redesign, search and filtering options, and a round of fixes from an independent code review.

### Added

- New identity: the W mark with an amber stem, warm black and ivory palette, amber focus, numbered favorites and a menu that shows each option's state. Launcher icon, TV banner and all interface graphics are vectors; app resources dropped from 67.7 KB to 27.4 KB.
- Search engine choice (Google, DuckDuckGo, Bing, Brave) and voice search through the speech recognizer installed on the device.
- Regional filter lists for Portuguese and Spanish, chosen automatically or in Menu > Block ads and trackers > Regional list.
- "Filter on this site" toggle in the same menu, lasting for the session.
- Menu states for the filter: loading lists and partial lists.
- Clear data menu: cache, cookies and site data, or all of them. The page cache is also erased when the app is closed normally.
- Page error screen with reload (GET only), edit address and home.
- Unit tests for the navigation policy and the filter list rules, run by CI.
- `docs/testing.md` with the manual checklist, device matrix and memory measurements.

### Security

- Forms sent over HTTP are blocked: WebView reports no POST navigation to a callback the user can answer.
- HTTP pages opened by GET are confirmed on Android 6.0 too, and a confirmation no longer carries over to the next navigation.
- Only the newest Activity can replace or clear the native filter engine, and the swap runs under a lock; a failed list update keeps the engine that was running.

### Changed

- Up to 4 favorites are shown to fit the screen without scrolling; favorites beyond 4 stay stored. With the grid full, the star lists the stored favorites so one can be replaced, and "Remove favorite" in the options menu deletes one.
- The D-pad moves focus on the home screen, inside menus and in the toolbar, and moves the pointer on pages; pressing up at the top of a page focuses the address bar and down leaves it. The analog stick moves the pointer only over pages.
- Each filter list has its own age; a download is kept in memory when storage is full.
- Dialogs use the app theme, and focus stays on the page instead of the Home button.
- CI runs unit tests and lint, pins Rust 1.99.1, cargo-ndk 4.1.2, each action by commit SHA and the Gradle wrapper checksum. A `v*` tag can publish signed APKs and `SHA256SUMS.txt` when `WEBMINI_CI_RELEASE` is set.

### Fixed

- Clearing the cache from the home screen did nothing because no WebView existed.
- "Data cleared" appeared before the cookies were removed.
- WebView timers stayed paused after reopening the app without killing the process.
- A renderer crash overwrote the current URL with `about:blank`.
- A regional list downloaded for one language could be saved as another.

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
