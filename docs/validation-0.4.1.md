# 0.4.1 validation

Validation date: 2026-11-11. TiVo Stream 4K, Android TV 12, 32-bit ARM,
Android System WebView 156.0.8078.50.

The 0.4.0 smoke test exposed default black HTML text over a black page on TVs.
`createWebView` forced the WebView background to black even when the document
had no CSS background. 0.4.1 removes that override and retains the platform's
white default. Explicit page styles continue to determine the page colors.

The regression fixture was a document with an `h1`, a button, an input and a
long div, with no CSS. CDP confirmed the default text color was `rgb(0, 0, 0)`.
Screenshots were checked visually and decoded with FFmpeg to sample a blank
page pixel at (1000, 600), outside the toolbar and text.

| Case | Observed RGB | Result |
| --- | --- | --- |
| 0.4.0, no CSS | (0, 0, 0) | Black text was invisible |
| 0.4.1, no CSS | (255, 255, 255) | Default black text was readable |
| 0.4.1, explicit `background: #121212; color: #eeeeee` | (18, 18, 18) | Authored dark background and light text retained |
| 0.4.1, CSS removed | (255, 255, 255) | White default restored |

[Default page](images/page-defaults.png), [page with dark CSS](images/page-dark-css.png).

`testDebugUnitTest`, `lintRelease`, `assembleDebug` and `assembleRelease` passed:
16 unit tests, zero failures, zero lint errors and the same seven lint warnings
listed in [0.4.0 validation](validation-0.4.0.md). The WebView regression was
checked on the device because the Java unit suite exercises pure policies,
not Android views. The manual checklist includes this case.
