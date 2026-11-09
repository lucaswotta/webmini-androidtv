# OnePageTV roadmap

OnePageTV is the redesign and rebrand of WebMini. Goal: the lightest, fastest
single-tab browser for TVs, with ad blocking on from the first page.
Version 0.4.0 is the only release kept public; older releases are removed
when it ships.

Identity rule: one tab, remote only, small. A feature that needs a second tab,
an account or a background service is out of scope.

## Phase 0: rebrand and migration

- New application ID `io.github.lucaswotta.onepagetv`; Java package and JNI
  symbols in `native/adblock/src/lib.rs` renamed together.
- Same release keystore, so the certificate stays the same across the rename.
- Repo renamed to `onepagetv` (GitHub redirects the old URL); update README,
  README.pt-BR, SECURITY, CHANGELOG, `docs/brand`, badges and APK asset names.
- New mark and launcher/banner assets; `docs/brand/design.md` rewritten.
- Installs of WebMini do not update to OnePageTV; the changelog says so and
  how to move favorites by hand.

## Phase 1: architecture

`MainActivity.java` has 1916 lines. Split it before adding features:
navigation and address bar, menus and dialogs, favorites storage, filter
engine lifecycle, D-pad focus. Keep `NavigationPolicy` pure and unit tested.

## Phase 2: speed and footprint

Baseline on the TiVo (0.3.x debug): 70.6 MB PSS on the home screen, 94.9 MB
on uol.com.br, 3.6 MB of lists on disk. Measure cold start before changing
anything, then set budgets in this file and fail CI when they regress.

- Cache the compiled filter engine between launches (#2).
- Shrink the native library, enable R8 and resource shrinking, drop unused
  ABIs from the universal APK (#17).
- Start the WebView and the engine in parallel; show the home screen first.
- Baseline Profile for startup.
- Trim memory on `onTrimMemory`; cap WebView cache size.

## Phase 3: correctness

- Protect the first navigation until the lists are ready (#23).
- Block ad redirects on the current tab (#21).
- Confirm the real destination of `window.open` (#22).
- Coverage of Android System WebView interception (#29).
- Regression tests on a real WebView and a device matrix with API 23, 28 and
  36 (#24).

## Phase 4: features

Order by value for a remote-only browser:

1. Pointer mode: move a cursor with the D-pad on pages that need clicking.
2. Type URLs and text from the phone (QR code to a local page).
3. Favorites: titles, reorder, remove (#8).
4. Cosmetic filtering for blocked slots (#4).
5. YouTube video ads (#26) and sites that detect blockers (#28).
6. Private mode (#27).
7. More regional lists (#30).
8. Media keys and fullscreen video behavior on the remote.

## Phase 5: professional baseline

- Releases from CI with `SHA256SUMS.txt` (#15).
- F-Droid and Amazon Appstore (#16); OpenShelf once the repo has 10 stars.
- Privacy policy, licenses screen, TalkBack and contrast pass.
- Screenshots and a short video recorded on a TV in the README.
- No remote telemetry; any crash report stays on the device.

## 0.4.0 exit criteria

- Phases 0 to 3 done and the 0.4.0 features chosen from phase 4 listed here.
- Memory and cold-start budgets met on the TiVo.
- Tests green on the API matrix.
- Only the `v0.4.0` release remains public.
