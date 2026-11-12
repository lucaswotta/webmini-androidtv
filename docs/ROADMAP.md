# OnePage roadmap

OnePage is the redesign and rebrand of WebMini. Goal: the lightest, fastest
single-tab browser for TVs, with ads blocked from the first page and pages
that work like in any standard browser.

## Decisions

- Identity: one tab, remote only, no account, no background service.
- No "lite mode". JavaScript, images, media and smooth scrolling keep the
  standard browser behavior. Savings come from the app, the filter engine and
  from the ads and trackers that are never downloaded.
- Reference device: 1 GB RAM, API 28, 32-bit (`armeabi-v7a`), plus a 512 MB
  emulator. The `armeabi-v7a` APK is the priority build for low-RAM TVs; the
  universal APK stays for 64-bit-only systems.
- `applicationId` `io.github.lucaswotta.onepage`, same release keystore as
  WebMini. Installs of WebMini do not update in place.
- Repo named `onepage`.
- The filter engine loads network rules only (`RuleTypes::NetworkOnly`).
  Cosmetic filtering (#4) is decided after measuring memory; switching to it
  invalidates the persisted engine.
- The HTTP cache is kept on exit; only the user's "Clear data" action clears
  it. `setAppCacheMaxSize` has no effect on the HTTP cache, so there is no
  cache size cap.
- `Sec-Fetch-Dest` is the first source for the request type. Extension
  guessing is not moved ahead of it: a `fetch("/a.js")` would be mislabeled.
- No remote telemetry; any crash report stays on the device.
- Published releases keep their version tags and APKs. The stable download
  links resolve to the latest release.

## Done in 0.4.0

- Rebrand: name, mark, application ID, Java package, native library, repo and
  release asset names.
- Filter engine: lists read from disk by the native side, network rules only,
  compiled engine persisted in `engine.dat`, conditional list updates
  (`ETag`, `Last-Modified`, `304`), downloads cancelled on destroy,
  `opt-level = "s"`.
- First navigation waits for the filter engine (#23).
- Scripted redirects to hosts on the filter lists blocked (#21).
- `window.open` confirmation with the real destination (#22).
- HTTP cache kept on exit.
- `configChanges` complete: a remote or keyboard connecting keeps the
  Activity.
- HTTP error responses shown as the server sent them.
- Reload offered after the renderer is killed; scroll position restored
  after the system kills the process.
- Download `Referer` under `strict-origin-when-cross-origin`.
- Favorites with titles, reorder and remove (#8).
- Screen margin setting.
- Notice for a system WebView below Chromium 80.
- Find in page, page jumps and media keys.

Measurements are in [`testing.md`](testing.md).

## Remaining

### Architecture

- Split `MainActivity.java` into navigation and address bar, menus and
  dialogs, favorites, filter engine lifecycle, pointer and D-pad focus, and
  request filtering. `NavigationPolicy` stays pure and unit tested.

### Speed and footprint

- Baseline Profile, only if it shows a gain in a release build installed from
  the APK (it optimizes DEX, not Rust or Chromium).
- `onTrimMemory`: release rebuildable caches. Destroying the WebView in the
  background only with a measured policy and state restoration, because it
  loses forms and playback.
- Budgets for PSS, cold start and dropped frames (`dumpsys gfxinfo`) on the
  reference device, checked in CI.

### Correctness

- Device matrix tests on a real WebView with API 23, 28 and 36, including
  32-bit execution; navigation and interception tests (#24).
- Coverage of Android System WebView interception (#29).

### Features

- Type URLs and text from the phone: a QR code opens a local page.
- More interface translations, with RTL support.
- Prototype of numbered links (pick a link by number with the remote); ship
  only if it is fast and compatible.
- Cosmetic filtering (#4), after measuring its memory cost.
- EME/protected media, with per-origin consent, validated per provider and
  device.

Candidates without a decision: custom start page, connection diagnostics on
the error screen, YouTube video ads (#26), sites that detect blockers (#28),
private mode (#27), more regional lists (#30), content language per site,
couch reading mode, and per-site switches that are off by default (images on
demand, scripts off, media plays only on user action).

### Distribution

- F-Droid and Amazon Appstore (#16); OpenShelf once the repo has 10 stars.
- Privacy policy, licenses screen, TalkBack and contrast pass.

## Tracked work

Implementation and validation are tracked in the individual issues below.

| Work | Issue |
| --- | --- |
| Cosmetic filtering of blocked ad slots | [#4](https://github.com/lucaswotta/onepage/issues/4) |
| Low-memory measurements and native size/speed comparisons | [#17](https://github.com/lucaswotta/onepage/issues/17) |
| F-Droid and Amazon Appstore distribution | [#16](https://github.com/lucaswotta/onepage/issues/16) |
| Instrumented WebView tests and the minimum-API/arm64 matrix | [#24](https://github.com/lucaswotta/onepage/issues/24) |
| YouTube video ad filtering prototype | [#26](https://github.com/lucaswotta/onepage/issues/26) |
| Private browsing with isolated local storage | [#27](https://github.com/lucaswotta/onepage/issues/27) |
| Saved per-site filtering exceptions | [#28](https://github.com/lucaswotta/onepage/issues/28) |
| Interception coverage on five representative pages | [#29](https://github.com/lucaswotta/onepage/issues/29) |
| Regional filter lists beyond Portuguese and Spanish | [#30](https://github.com/lucaswotta/onepage/issues/30) |
