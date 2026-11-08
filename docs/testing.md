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
| Pop-up | Open a page that calls `window.open` from a click | Confirmation with the destination host |
| Downloads | Start a download | Confirmation with name, host and type; nothing opens automatically |
| Filtering | Open `uol.com.br` with filtering on and off | Fewer requests with filtering on |
| Filtering | Menu, ad blocking, "Filter on this site" | Page reloads with the site exempt until the app closes |
| Data | Menu, clear data, each option | Cache, cookies or both are removed; the page reloads |
| Search | Change the engine, search from home and from the address bar | Uses the chosen engine |
| Voice | Press the microphone button on a device with a recognizer | The spoken text is searched |
| Renderer | Kill the WebView process (`adb shell am kill`) | App stays open; page offers home and edit address |
| Lifecycle | Visit a site, press home, reopen the app without killing it | Pages load and timers run |

## Devices

| Device | System | Architecture | WebView | Checklist run |
| --- | --- | --- | --- | --- |
| TiVo Stream 4K | Android TV 12 | `armeabi-v7a` | 155 | Partial: home, favorites, menu, search engine, regional list, D-pad focus |

Android 6.0 (API 23) is the minimum supported version and has not been run on a device. Device reports go to the [device report form](https://github.com/lucaswotta/webmini-androidtv/issues/new?template=device-report.yml).

## Memory and storage

Measured on the TiVo Stream 4K on 2026-11-07 with `dumpsys meminfo` and `du` in a debug build, three filter lists loaded:

| State | App PSS | App native heap | WebView renderer |
| --- | ---: | ---: | ---: |
| Home screen | 70.6 MB | 15.4 MB | none |
| `uol.com.br` home page loaded | 94.9 MB | 20.8 MB | about 218 MB, separate process |

Private storage after loading the page: filter lists 3.6 MB, page cache 3.6 MB, WebView data 0.9 MB.
