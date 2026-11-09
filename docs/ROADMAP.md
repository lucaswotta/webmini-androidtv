# OnePageTV roadmap

OnePageTV is the redesign and rebrand of WebMini. Goal: the lightest, fastest
single-tab browser for TVs, with ads blocked from the first page and pages
that work like in any standard browser.

Version 0.4.0 is the only release kept public; older releases are removed
when it ships (irreversible, confirm before doing it).

## Decisions

- Identity: one tab, remote only, no account, no background service.
- No "lite mode". JavaScript, images, media and smooth scrolling keep the
  standard browser behavior. Savings come from the app, the filter engine and
  from the ads and trackers that are never downloaded.
- Reference device: 1 GB RAM, API 28, 32-bit (`armeabi-v7a`), plus a 512 MB
  emulator. The `armeabi-v7a` APK is the priority build for low-RAM TVs; the
  universal APK stays for 64-bit-only systems.
- New `applicationId` `io.github.lucaswotta.onepagetv`, same release keystore.
  Installs of WebMini do not update in place.
- Repo renamed to `onepagetv`.
- The filter engine loads network rules only (`RuleTypes::NetworkOnly`).
  Cosmetic filtering (#4) is decided after measuring memory; switching to it
  invalidates the persisted engine.
- The HTTP cache is no longer cleared on exit; only the user's "Clear data"
  action clears it. `setAppCacheMaxSize` has no effect on the HTTP cache, so
  there is no cache size cap.
- `Sec-Fetch-Dest` stays the first source for the request type. Extension
  guessing is not moved ahead of it: a `fetch("/a.js")` would be mislabeled.
- EME/protected media and the couch reading mode are after 0.4.0.
- Reviewed independently by Codex and Claude; the list below is the consensus.

## Order of work

1. Measure release builds and write the baseline (see Phase 2).
2. Quick safe fixes: stop clearing the cache on exit, complete
   `configChanges`, loading line without per-progress animation.
3. Tests for navigation and request interception, and first-page protection
   (#23).
4. Network-only rules, then the file-based engine pipeline and the persisted
   engine. Measure again.
5. `onTrimMemory` (revised), immutable request context, WebView age notice.
6. Baseline Profile only if the release build shows a gain.
7. 32-bit execution on the reference device and in CI.
8. Remaining correctness items, features, then the rebrand and release.

## Phase 0: rebrand and migration

- Application ID, Java package and JNI symbols in `native/adblock/src/lib.rs`
  renamed together.
- Repo, README (en and pt-BR), SECURITY, CHANGELOG, `docs/brand`, badges and
  APK asset names updated. New mark, launcher icon and TV banner.
- The changelog says WebMini does not update in place and how to move
  favorites by hand.

## Phase 1: architecture

`MainActivity.java` has about 1900 lines. Split it into navigation and
address bar, menus and dialogs, favorites, filter engine lifecycle, pointer
and D-pad focus, and request filtering. Keep `NavigationPolicy` pure and
unit tested.

## Phase 2: speed and footprint

Baseline on the TiVo (0.3.x debug): app 70.6 MB PSS on the home screen and
94.9 MB on uol.com.br, WebView renderer about 218 MB, lists 3.6 MB on disk.
Measure release builds: cold start, PSS, `dumpsys gfxinfo` dropped frames.
Budgets go in CI.

Filter engine and lists
- Pass the list file path to Rust and read it there; no list text in the Java
  heap, one list at a time (`MainActivity.java` `readCachedFilterLists`,
  `lib.rs` `nativeAddFilterList`).
- `ParseOptions` with `rule_types: RuleTypes::NetworkOnly`.
- Persist the compiled engine: `Engine::serialize` and `Engine::deserialize`
  (adblock 0.13.3). Atomic write; invalidate by app version, lists, region
  and options; rebuild when invalid (#2).
- Download lists to a temporary file with size limit and validation, replace
  atomically. Use `ETag` and `Last-Modified`, handle `304`, skip the rebuild
  when the content is identical.
- Abort the pending filter set on every unsuccessful end; today it stays in
  the process-wide `PENDING_FILTERS` when the sequence is cancelled.
- Swap the engine with `replace` and drop the old one after releasing the
  write lock, so requests are not blocked during the update.
- Cancel list downloads on destroy: disconnect, check generation, total
  deadline, so the Activity is not retained.
- Compare `opt-level` `"z"`, `"s"` and `2` for the engine on `armeabi-v7a`
  with the same lists; keep LTO and strip.

Request path (`filterNetworkRequest`)
- Publish an immutable page context (URL, origin, exempt flag) per
  navigation, including redirects, and read it once per request. Old requests
  that arrive after a navigation must not get the new page's context.
- Passing the page URL to Rust once per page only if a measurement shows a
  gain with that safe context.

Lifecycle and memory
- Keep the HTTP cache on exit (see Decisions).
- `configChanges`: add `keyboard`, `navigation`, `uiMode`,
  `smallestScreenSize` and `screenLayout`, and update layout in
  `onConfigurationChanged`, so a remote or keyboard connecting does not
  recreate the Activity and the WebView.
- `onTrimMemory`: release rebuildable caches. Destroying the WebView in the
  background only with a measured policy and state restoration, because it
  loses forms and playback.
- Save URL and scroll position and restore them after the system kills the
  process. `onRenderProcessGone` already recreates the WebView; offer
  reloading `currentUrl` instead of an empty page.
- Pointer loop: stop the movement on menu, home and window focus loss, so no
  per-frame callbacks or involuntary scrolling remain.
- Loading line: set `scaleX` directly on progress and cancel the animation
  started by `showLoading`.
- Baseline Profile, only if it shows a gain in a release build installed from
  the APK (it optimizes DEX, not Rust or Chromium).
- Notice, dismissible, when the system WebView is below a validated minimum.
  API 23-25 have no native query; handle them separately.

## Phase 3: correctness and security

- Protect the first navigation until the lists are ready (#23).
- Block ad redirects on the current tab (#21).
- Confirm the real destination of `window.open` (#22).
- Coverage of Android System WebView interception (#29).
- HTTP 4xx and 5xx responses with a document are shown as the server sent
  them; the error screen is only for transport failures without a usable
  document (DNS, TLS, connection). Test each case.
- Download `Referer` follows `strict-origin-when-cross-origin`: full URL
  without fragment on the same origin, origin only across sites, none on
  downgrade.
- Regression tests on a real WebView and a device matrix with API 23, 28 and
  36; navigation and interception tests (#24).

## Phase 4: features

The D-pad pointer already exists in 0.3.x and is not a new feature.

Planned for 0.4.0, in this order:
1. Find in page.
2. Page jumps: screen up and down, top, bottom.
3. Overscan margin setting.
4. Favorites: titles, reorder, remove (#8).
5. Type URLs and text from the phone (QR code to a local page).
6. Interface translations with RTL support.
7. Prototype of numbered links (pick a link by number with the remote);
   ship only if it is fast and compatible.
8. Media keys and fullscreen video on the remote.

Candidates after 0.4.0 or if the budget allows: custom start page,
connection diagnostics on the error screen, YouTube video ads (#26), sites
that detect blockers (#28), private mode (#27), more regional lists (#30),
cosmetic filtering (#4), content language per site, EME/protected media
(per-origin consent, validated per provider and device), couch reading mode.

Optional per-site switches, off by default: images on demand, scripts off,
media plays only on user action.

## Phase 5: professional baseline

- Releases from CI with `SHA256SUMS.txt` (#15).
- F-Droid and Amazon Appstore (#16); OpenShelf once the repo has 10 stars.
- Privacy policy, licenses screen, TalkBack and contrast pass.
- Screenshots and a short video recorded on a TV in the README.
- No remote telemetry; any crash report stays on the device.

## 0.4.0 exit criteria

- Phases 0 to 3 done and the phase 4 list above done or explicitly moved.
- Budgets for PSS, cold start and dropped frames met on the reference device.
- Tests green on the API matrix; 32-bit execution verified.
- Only the `v0.4.0` release remains public.
