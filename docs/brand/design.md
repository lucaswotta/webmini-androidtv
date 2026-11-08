# WebMini visual system

## Fold mark

Fold is an open W made from a folded ribbon. Its amber rising terminal completes
the letter and points along the same ascending direction as the cursor edge.
The accent is part of the silhouette. There is no detached punctuation or tile
behind the home mark. `webmini-mark.svg` is the transparent master for dark
backgrounds; Android launcher, banner and home paths share its 96-unit geometry.

The home mark uses theme text and accent colors, including dark ink and brown
amber on light phones. Launcher assets retain ivory and amber on ink. The
adaptive foreground fits the central 66-unit safe area; the legacy launcher is
a vector, supported by minimum API 23. Banner lettering is outlined geometry.
No font or bitmap assets are required.

## Palette and contrast

Tokens live in `values/colors.xml`, with phone overrides in `values-night`.
`TvTheme` keeps dark surfaces and ivory text independently of phone mode.

| Role | TV / night | Phone light |
| --- | --- | --- |
| Canvas | `#000000` | `#F4F0E6` |
| Surface | `#181916` | `#FFFFFF` |
| Primary text | `#F4F0E6` | `#23251F` |
| Secondary text | `#B9B9AC` | `#5D6255` |
| Field / panel border | `#75786B` | `#777D6D` |
| Accent / field focus | `#F2BB66` | `#805000` |
| Button focus fill | `#F2BB66` | `#F2BB66` |
| Button pressed fill | `#DCA452` | `#DCA452` |
| Focus / pressed text | `#171714` | `#171714` |
| Disabled text | `#9B9D90` | `#6B7062` |
| Decorative menu separator | `#41443B` | native dialog |

Ratios use sRGB relative luminance and `(lighter + 0.05) / (darker + 0.05)`.
Channel values are not rounded during calculation.

| Pair | Contrast |
| --- | ---: |
| TV primary / surface | 15.51:1 |
| TV secondary / surface | 8.91:1 |
| Phone primary / white | 15.49:1 |
| Phone secondary / white | 6.28:1 |
| Dark text / amber focus | 10.33:1 |
| Dark text / pressed fill | 8.10:1 |
| TV disabled / surface | 6.41:1 |
| Phone disabled / white | 5.10:1 |
| TV amber value / surface | 10.15:1 |
| TV border / surface | 3.91:1 |
| Phone border / white | 4.25:1 |
| Phone accent / white | 6.85:1 |
| Cursor outline / ivory silhouette | 15.78:1 |

Active text pairs exceed AA's 4.5:1 threshold; field boundaries exceed 3:1.
Disabled controls also exceed 4.5:1, although inactive controls are exempt.
These are palette calculations, not a full accessibility audit. The cursor's
ink outline and ivory silhouette protect its shape on light and dark pages;
its amber stripe is supplementary. Separators are decorative.

## Type and layout

| Element | System font | Size / tracking |
| --- | --- | --- |
| Home title | sans-serif-condensed bold | 40 sp; compact 30 sp / +0.035 em |
| Home search | sans regular | TV 20 sp; phone 18 sp |
| Favorite host | sans-serif-medium | TV 18 sp; phone 16 sp |
| Favorite index / suffix | sans regular | same size as host |
| Favorites heading | sans bold | 16 sp / +0.06 em |
| TV menu label / value | sans / sans-serif-medium | 18 / 16 sp |
| TV address | sans regular | 17 sp |

The home group is centered vertically within the viewport below the toolbar.
The ScrollView fills that viewport and permits scrolling only when content
requires more height. Centering adapts to zero through four favorites without
fixed pixel offsets or a spacer tied to screenshot resolution.

The mark occupies 80 dp, or 52 dp when TV display height is below 440 dp.
Search height is 64 dp, or 56 dp in compact TV mode. The field has a persistent
2 dp accent baseline; focus replaces it with a 3 dp perimeter. Voice and submit
are independent 52 dp targets with 8 dp gaps. This separates their silhouettes
and makes the input the largest action on the page.

TV favorite indices run 01–04 in reading order. The first host label is primary;
`www.` and the dotted suffix use secondary text. This is display styling, not
public-suffix parsing or a change to the destination. No network favicon is
requested. On phones the index is omitted and tile padding drops from 18 to
12 dp to reserve width for the host. Hosts remain single-line and ellipsized.
The full host remains the accessible name.

Favorite rows are 64 dp, or 56 dp in compact TV mode, including 4 dp tile margins.
Targets are 56 / 48 dp high. The two-column grid and four-item limit remain.
TV horizontal insets are 28 dp; phone insets are 20 dp. Toolbar targets are
48 × 48 dp on TV. Shapes have square corners, without shadows or elevation.

At font scale 1, estimated total heights with toolbar and four favorites are
about 344 dp compact and 452 dp regular. These fit 640 × 360 dp and 960 × 540 dp
respectively. The supplied 1280 × 720 screenshots use approximately 1.33 px/dp,
so they use the regular branch. Insets, font metrics and overscan require device
verification. Phone keyboard and enlarged fonts retain scroll access.

## Interaction states

Normal buttons use primary text over the surface. The unsaved star uses this
same primary token; its home-only disabled state uses theme tertiary text.
Disabled styling takes precedence over pressed and focused styling. Pressed
buttons use darker amber; focused buttons use amber with a 2 dp text-color
border. Secondary favorite text and menu values switch to ink with the parent
focus or pressed state. State-list elevation animators are disabled.

TV menus use a bordered opaque panel, 48 dp rows and inset 1 dp separators.
Six entries occupy 317 dp including 24 dp panel padding and five separators.
An entry containing `: ` is split at its last separator for presentation: label
at the start, original value at the end in amber. Focus fills the entire row.
The complete original entry is its accessible name; selection callbacks and
option strings are unchanged. Entries without that separator remain intact.
Phone choices retain the native themed dialog.

The loading line and cursor accent are both continuous, square-ended 2 dp
strokes in brand amber on TV. The pointer retains ivory fill and an ink outline;
its amber edge replaces the filled inset. Loading timing and navigation behavior
are unchanged. Phone loading uses the theme accent.

## Resource budget

Isolated measurements use SDK build-tools 36.0.0, Android 36, identical manifest
and link options. The temporary manifest only adds the package supplied by AGP.
Documentation is outside packaged resources.

| Measurement | HEAD baseline | Round 1 | Round 2 |
| --- | ---: | ---: | ---: |
| Source files under `res` | 17 | 24 | 27 |
| Source resource bytes | 60,656 | 25,377 | 27,432 |
| Standalone linked resource APK bytes | 67,677 | 37,433 | 40,574 |

Round 2 adds 2,055 source bytes and 3,141 linked bytes over round 1. The linked
artifact is 27,103 bytes smaller than HEAD. This is a resource comparison,
not the application APK size. The +60 KB application budget requires a release
APK comparison including compiled Java. There are no new dependencies, fonts,
bitmaps, blur effects or animations.

## Round 2 verification — 2026-11-07

Input evidence: `d1.png` through `d4.png`, supplied from the 1280 × 720 TV test.
Decisions: center the whole home group; make the amber terminal structural;
use condensed bold system type; add TV indices and secondary host fragments;
separate search actions; align existing menu values without changing options;
raise disabled-star contrast in both light and dark themes.

Validation: 27 resource XML files parsed. `aapt2 compile` and `aapt2 link` passed.
Isolated Java compilation passed against Android 36 using the generated R class
and all three application source files. Its 19 deprecation warnings concern
legacy insets, resource color access, WebView URL handling, URL construction and
back handling; these APIs are retained within this visual-only scope.
`git diff --check` passed. Gradle and device runtime tests were not run.

Visual checks remaining: home with zero and four favorites at 720p; all field,
voice, submit, favorite and menu focus/pressed states; star on home and saved /
unsaved pages; long hosts; six menu entries in Portuguese and Spanish; pointer
and loading line on light/dark pages; launcher banner and adaptive icon; phone
light/night with keyboard and enlarged text. Menu rows use focusable containers
with child text inheriting their state: verify D-pad activation and TalkBack.

Tuning points: `showHomePage()` and `showChoices()` in MainActivity;
`home_search.xml`, `tv_button.xml` and `tv_button_*.xml` for states;
`values*/colors.xml` and `styles.xml` for themes. Keep SVG and launcher/home
mark paths synchronized.
