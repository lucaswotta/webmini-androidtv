# Changelog

## 0.4.0 (2026-11-11)

WebMini becomes OnePage. The application ID changed, so OnePage installs next to WebMini instead of updating it: add the favorites again by hand in OnePage, then uninstall WebMini.

### Added

- Find in page, from Menu > Find in page, with previous and next result.
- Page jumps: the Page Up, Page Down, Channel Up and Channel Down keys move one screen, and Menu > Go to offers Top, Screen up, Screen down and Bottom.
- Media keys act on the page's video: play/pause, and rewind and fast-forward seek 10 s.
- Favorites store the page title. Holding OK on a favorite opens Open, Move earlier, Move later and Remove (#8). Favorites saved by older versions still load.
- Preferences > Screen margin (0, 2, 4 or 6%) for TVs that crop the picture edges.
- A notice, shown once per WebView version, when Android System WebView is below Chromium 80.
- The first navigation waits up to 6 s for the filter engine and says so; if the engine is not ready the page opens with a notice (#23).
- Scripted redirects of the current tab to a host on the filter lists are blocked and reported (#21).
- After the renderer process is killed, the error screen offers to reload the page.
- Scroll position is restored after the system kills the app.

### Changed

- Renamed to OnePage, with a new mark, "Page one": a page plate with a cut-out 1.
- Application ID changed from `io.github.lucaswotta.webmini` to `io.github.lucaswotta.onepage` (debug: `io.github.lucaswotta.onepage.debug`), signed with the same release certificate. The Java package is `io.github.lucaswotta.onepage` and the native library is `libonepage_adblock.so` (Cargo package `onepage-adblock`).
- Repository moved to `https://github.com/lucaswotta/onepage`. Release files are `OnePage-universal.apk`, `OnePage-armeabi-v7a.apk`, `OnePage-arm64-v8a.apk` and `OnePage-x86_64.apk`, plus `SHA256SUMS.txt`.
- `OnePage-armeabi-v7a.apk` is the recommended build for low-RAM TVs; the universal APK stays for 64-bit-only systems.
- Filter lists are downloaded to disk and read by the native side; no list text passes through the Java heap.
- The filter engine loads network rules only (`RuleTypes::NetworkOnly`).
- The compiled engine is persisted as `engine.dat` and reused until the app or a list file changes.
- List updates send `ETag` and `Last-Modified`, handle `304`, and skip the rebuild when the downloaded content is identical.
- The native library is built with `opt-level = "s"`.
- Measured on a TiVo Stream 4K (`armeabi-v7a`, debug build, 2026-11-11): engine build 0.56 s, reload of the persisted engine 0.14 s, home screen PSS 63.3 MB (70.6 MB in 0.3.1), app native heap 11.2 MB (15.4 MB in 0.3.1).
- The HTTP cache is no longer erased on exit; only Menu > Clear data clears it.
- The `window.open` confirmation shows the address the script really opens, read through a short-lived hidden WebView that downloads nothing (#22).
- Download `Referer` follows `strict-origin-when-cross-origin`.

### Fixed

- A remote or keyboard connecting recreated the Activity and reloaded the page (`configChanges`).
- HTTP error responses (404, 500 and others) were replaced by the error screen; the server's own page is shown, and the error screen is only for transport failures.
- Filter list downloads kept running after the app closed.

## 0.3.1 (2026-11-07)

### Added

- The D-pad reaches the address bar and the toolbar: up at the top of a page focuses the address bar, left and right move between the buttons, down returns to the pointer.
- With 4 favorites saved, the star lists them so one can be replaced; "Remove favorite" in the options menu deletes one.
- Menu panels with more than seven rows scroll, and can carry a title.
- Menu > Preferences groups the search engine, desktop site (desktop Chrome user agent) and text size (100, 125, 150%).
- Blocked request count for the current page next to the ad blocking option in the menu.
- The pointer scrolls pages sideways at the left and right edges.
- Ad blocking options screenshot in the README.

### Changed

- Toolbar focus is read from the real focus instead of a flag, and leaving the toolbar or the address bar moves focus to the page rather than back to the Home button.
- The analog stick moves the pointer only over web pages.
- Address bar parsing moved into `NavigationPolicy.addressToUrl` with tests; `localhost` and IPv4 addresses open over HTTP instead of becoming a search.

### Fixed

- With an odd number of favorites the last one stretched across the whole row.

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
