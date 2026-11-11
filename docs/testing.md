# Testing

## Automated

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease
```

`NavigationPolicyTest` covers the URL decisions: HTTPS, repeated HTTP, `intent:` and other schemes, invalid URLs, credentials in the URL, POST, subframes and origin normalization. CI runs the same tasks on every pull request and checks that each release APK contains the native library for its ABIs.

## Manual checklist

Run on a TV device, with the debug or release build.

| Area | Check | Expected |
| --- | --- | --- |
| Home | Open the app, press D-pad down | Focus moves to the search field, then to the favorites |
| Home | Save 5 favorites | The star opens the stored list to replace one; the grid keeps 4 and fits without scrolling |
| Menu | Menu, Remove a favorite | Stored favorites are listed; the removed one leaves the home screen |
| Pointer | Open a page, move the pointer to the bottom edge | Page scrolls |
| HTTP | Open `http://neverssl.com` from the address bar and from a favorite | Warning each time |
| Pop-up | Open a page that calls `window.open` from a click, with a script that changes the address after opening | Confirmation shows the address the script really opens; nothing is downloaded before the answer |
| Downloads | Start a download | Confirmation with name, host and type; nothing opens automatically |
| Filtering | Open `uol.com.br` with filtering on and off | Fewer requests with filtering on |
| Filtering | Menu, ad blocking, "Filter on this site" | Page reloads with the site exempt until the app closes |
| Data | Menu, clear data, each option | Cache, cookies or both are removed; the page reloads |
| Search | Change the engine, search from home and from the address bar | Uses the chosen engine |
| Voice | Press the microphone button on a device with a recognizer | The spoken text is searched |
| Renderer | Kill the WebView renderer process (`adb shell am kill`) | App stays open; the error screen offers to reload the page, edit the address or go home |
| Toolbar | Open a page, press up at the top, then left and right, then down | Focus ring on the address bar and each button; down returns to the pointer. If a text field in the page has the keyboard, press Back first |
| Toolbar | Repeat the previous case 5 times, then move the pointer | Pointer still moves; no button stays highlighted |
| Favorites | With 4 saved, focus the star and press OK | List of stored favorites; choosing one replaces it with the current page |
| Favorites | Menu, "Remove favorite", choose one | The favorite disappears from the home grid |
| Preferences | Menu > Preferences > Desktop site, open `https://httpbin.org/user-agent` | User agent is the desktop Chrome string |
| Preferences | Menu > Preferences > Text size | Cycles 100, 125, 150% and applies without reload |
| Blocking | Open `uol.com.br`, open the menu | "on (N)" with N above zero |
| Pointer | Open a page wider than the screen, hold right | The page scrolls sideways |
| Lifecycle | Visit a site, press home, reopen the app without killing it | Pages load and timers run |
| Lifecycle | Open a page, connect a Bluetooth remote or keyboard | The page stays as it was: no reload, same scroll position |
| Lifecycle | Scroll down a long page, press home, run `adb shell am kill io.github.lucaswotta.onepage`, reopen the app | The same page opens at the same scroll position |
| Renderer | After the renderer is killed, choose reload on the error screen | The page that was open loads again |
| First page | Clear the app storage, open the app and go to `uol.com.br` at once | "Preparing ad blocking…" appears and the page opens within 6 s with filtering on |
| First page | Same, with the network too slow to fetch the lists in 6 s | The page opens after 6 s with the reduced blocking notice |
| Engine | Open the app twice in a row, read `adb logcat` or the menu state | The second start reuses `engine.dat`; the menu shows ad blocking on without "loading lists" |
| Engine | Update the app or delete one list file, reopen | The engine is rebuilt and `engine.dat` is rewritten |
| Lists | Force a list update with unchanged lists | The server answers `304` or identical content and the engine is not rebuilt |
| Lists | Close the app during a list download | The download stops; no connection to `easylist.to` remains |
| Cache | Load a page, exit with Back, reopen the app and load it again | Cached resources are reused; only Menu > Clear data empties the cache |
| Errors | Open `https://httpbin.org/status/404` and `https://httpbin.org/status/500` | The server's response is shown, not the error screen |
| Errors | Open a host that does not resolve, or disconnect the network | Error screen with reload, edit address and home |
| Downloads | Start a download from a link on the same site, on another site and from HTTPS to HTTP | `Referer` is the full URL without fragment, the origin only, and absent, in that order |
| Redirect | Open a page whose script sets `location` to a host on the filter lists | The page stays; "Redirect to an ad was blocked." appears |
| Favorites | Save a page with a title, go home | The favorite shows the page title |
| Favorites | Hold OK on a favorite | Open, Move earlier, Move later and Remove; each one acts on that favorite |
| Favorites | Update from a build that saved favorites without titles | The old favorites load |
| Preferences | Menu > Preferences > Screen margin | Cycles 0, 2, 4, 6%; the picture keeps that margin from each screen edge |
| WebView | Run on a system WebView below Chromium 80, open the app twice | The notice appears once for that WebView version |
| Find | Menu > Find in page, type a word present several times | Matches are highlighted with a count; previous and next move between them; closing clears them |
| Jumps | On a long page, press Page Down, Page Up, Channel Down and Channel Up | The page moves one screen per press |
| Jumps | Menu > Go to, each option | Top, Screen up, Screen down and Bottom scroll accordingly |
| Jumps | Focus a text field in the page, press Page Down | The key goes to the text field; the page does not jump |
| Media | Play a video in a page, press play/pause, fast-forward and rewind | Playback toggles; the position moves 10 s forward and back |

## Devices

| Device | System | Architecture | WebView | Checklist run |
| --- | --- | --- | --- | --- |
| TiVo Stream 4K | Android TV 12 | `armeabi-v7a` | 155 | Partial: home, favorites, menu, search engine, regional list, D-pad focus |

Android 6.0 (API 23) is the minimum supported version and has not been run on a device. Device reports go to the [device report form](https://github.com/lucaswotta/onepage/issues/new?template=device-report.yml).

## Memory and storage

Measured on the TiVo Stream 4K (`armeabi-v7a`) with `dumpsys meminfo` in a debug build:

| Date | Version | State | App PSS | App native heap | WebView renderer |
| --- | --- | --- | ---: | ---: | ---: |
| 2026-11-11 | 0.4.0 | Home screen | 63.3 MB | 11.2 MB | none |
| 2026-11-07 | 0.3.1 | Home screen | 70.6 MB | 15.4 MB | none |
| 2026-11-07 | 0.3.1 | `uol.com.br` home page loaded | 94.9 MB | 20.8 MB | about 218 MB, separate process |

Filter engine on the same device and build on 2026-11-11: build from the list files 0.56 s, reload of the persisted `engine.dat` 0.14 s.

Private storage after loading the page, measured with `du` on 2026-11-07: filter lists 3.6 MB, page cache 3.6 MB, WebView data 0.9 MB.
