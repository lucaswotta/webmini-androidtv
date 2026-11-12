# 0.4.0 validation

Validation date: 2026-11-11. Device: TiVo Stream 4K, Android TV 12,
`armeabi-v7a`, Android System WebView 156.0.8078.50. The debug build used
`io.github.lucaswotta.onepage.debug`; it was installed beside WebMini.
A second run used Waydroid, Android 13, `x86_64`, with WebView 111.0.5563.116.

## Automated checks

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --no-daemon clean \
  :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease
```

- 16 unit tests, zero failures or errors.
- Release lint: zero errors, seven warnings: WebViewApiAvailability,
  VectorRaster, AndroidGradlePluginVersion, PluralsCandidate,
  SetJavaScriptEnabled, DataExtractionRules and UnusedResources.
- Four release APKs: application ID, version, signature and exact native
  library set checked. Each ABI exports all four OnePage JNI entry points.
- An incremental build initially included the former `libwebmini_adblock.so`.
  A clean build removed it. No source change was needed for this build artifact.
- Tests, release lint and debug assembly also passed after the local folder
  was renamed to `onepage`.

## Device checks

A loopback HTTP fixture was served through `adb reverse`. WebView state was
read through CDP; native dialogs were checked with screenshots.

| Check | Result |
| --- | --- |
| Page Up/Down, Channel Down, page start/end | Passed; scroll positions checked in the actual WebView |
| Find in page | Three matches; advancing changed the counter from 1/3 to 2/3 |
| Favorite titles, reorder and removal | Saved the document title; Move later changed persisted URL order; both test favorites were removed through the menu |
| Popup from a button | Direct and delayed destinations prompted for the actual host; neither destination was downloaded before consent |
| Scripted ad redirect | A redirect to an ad-list host was blocked and the existing page stayed open |
| Media controls | Audio pause/play and forward/backward seeks of 10 seconds passed |
| HTTP 404 and 500 | The server document remained visible |
| Background and resume | The same document resumed and its JavaScript timer advanced |
| Renderer crash | The app process survived and offered reload, edit address and home |
| Engine cache | List files and a persisted `engine.dat` were created |
| Waydroid page jumps and media | Native page keys and pause/play/10-second seeking passed on WebView 111 |
| Waydroid system process death | After `am kill`, the app PID changed and the same URL reopened at 1199.64 CSS px, matching the saved position |
| Waydroid renderer recovery | The app survived `Page.crash`; Reload restored the document and media controls worked |

The initial Page Down failure was a test setup error: the HTTP consent dialog
or address editor still held focus. Closing the dialog and returning focus to
the page made the native key tests pass.

## Limits

The API 23/28/36 device matrix, 512 MB reference emulator, protected video
providers, cold-start fallback under throttled networking and conditional
list update responses were not exercised in this run. Issue #24 tracks the
WebView/device matrix; the roadmap retains performance and compatibility work.

On the TiVo, `am kill` did not terminate the app and `run-as` could not signal
it. The successful process-death case used Waydroid: close the CDP connection,
press system Home, wait six seconds, run `am kill`, confirm the PID disappeared,
then reopen the launcher Activity and consent to HTTP again. A debugger-driven
exit was not accepted as an equivalent test.
