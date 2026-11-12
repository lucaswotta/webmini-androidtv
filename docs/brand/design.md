# OnePage visual system

## Page one mark

The mark is an amber page plate with cut corners and a negative-space 1.
`onepage-mark.svg` is the 256-unit master for dark backgrounds;
`onepage-lockup.svg` combines the mark and outlined wordmark in black for
light backgrounds. Android launcher, banner and home mark use the same geometry.

The native vector's inner contour opposes the outer contour. Android API 23
uses nonzero winding and has no `fillType`, so the 1 remains a cutout without
an API-specific resource. The home mark takes the theme accent color.

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
| Favorite title / host fallback | sans-serif-medium | TV 18 sp; phone 16 sp |
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

TV favorite indices run 01–04 in reading order. A saved title is the primary
label; favorites without a title show the host. Host fragments such as `www.`
and the dotted suffix use secondary text. No network favicon is requested.
Phone tiles omit the index and reduce padding to reserve space for the label.
The full title or fallback host remains the accessible name.

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

## Resources and download actions

Launcher, banner, home mark and pointer graphics are vector resources. No
packaged font is required. Memory and APK measurements belong in the dated
validation records linked from [`testing.md`](../testing.md).

The README download actions use local SVG buttons in English and Brazilian
Portuguese. Amber identifies the recommended 32-bit TV build; the dark button
links to the universal build. Both point to fixed asset names under
`releases/latest/download/`, so publishing a new release keeps the links valid.
Every supported ABI and the checksum file also have plain text download links.

Maintain the mark geometry across the SVG and Android vector resources.
Interaction colors live in `values*/colors.xml` and the `tv_button*` resources;
home and menu layout are built in `MainActivity.java`.
