# OpenDashboard — Android 4.2 implementation hand-off

The values below are what the app implements (`Ui.kt`, `Dashboard.kt`, `Screens.kt`,
`VehicleWidget.kt`); they come from the reference canvas (22 artboards, 1280 × 720).

## Scaling rule

All sizes are **design units**. At the 1280 × 720 baseline (mdpi) 1 unit = 1 dp and text
sizes are sp. At runtime one unit is `s = min(windowWidth / 1280, windowHeight / 720)` pixels
(`Ui.setWindow`), so the design is never clipped or stretched; spare width goes to the
flexible columns (tile grid, fuel block, clock column, app-grid columns).

| Display | s (px per unit) | Layout space |
|---|---|---|
| ADAYO AC822X, 1920 × 720 @ 240 dpi | 1.0 | 1920 × 720 units |
| Reference / `HeadUnit_1280` AVD, 1280 × 720 | 1.0 | 1280 × 720 |
| `HeadUnit_API16` AVD, 1024 × 600 | 0.8 | 1280 × 750 |

Text sizes ignore the system font scale (fixed car layout). Letter spacing on API 16–20 is
drawn by `BigText` (clocks, titles, big values); other text gets it from API 21.

## Colour

| Token | Hex | Use |
|---|---|---|
| BG | `#050506` | screen background, toast text, text on light buttons |
| CARD | `#0E0F11` | cards, dialogs |
| CARD_DIM | `#0A0B0C` | editor "Apps · Always here" tile |
| RAISED | `#15171A` | icon boxes, menu rows, widget cells, Remove pill |
| PRESSED | `#202328` | pressed fill (any tappable surface) |
| LINE | `#1F2125` | card borders, dividers, disabled arrow borders |
| LINE2 | `#2C2F34` | button borders, dashed slots, switch track off, disabled arrow glyph |
| TEXT | `#F4F5F7` | primary text, solid buttons, switch knob |
| TEXT2 | `#D5D7DB` | secondary text, chip text |
| MUTED | `#9EA1A8` | labels, captions, inactive segment text |
| FAINT | `#7B7F87` | attribution, unavailable/stale values, "Dashboard full", status dots off |
| GREEN | `#30D158` | live / connected dot, incoming label, Accept, active-call border |
| RED | `#FF453A` | door warning (chip, cell border, text), Decline, End |
| SCRIM | `#050506` @ 72 % | behind dialogs and the incoming call |
| Accent Amber (default) | `#FF9F0A` | AA tile outline/icon, "On dashboard", selection, fuel bar, switch on, "In use", editor dashes, "Not installed" |
| Accent Blue / Green / White | `#4DA3FF` / `#30D158` / `#F4F5F7` | Settings › Accent colour |

Semantic colours (green, red, faint) do not follow the accent.

## Typography

Fonts: Geist Regular/Medium/SemiBold and Geist Mono Regular/Medium, bundled in
`assets/fonts` as static instances of the variable fonts (Latin subset, 142 KB, SIL OFL —
`assets/fonts/OFL.txt`). `res/font` needs API 26, so `Typeface.createFromAsset` is used.

| Element | Size (sp) | Face | Tracking (em) | Line height |
|---|---|---|---|---|
| Header time | 56 (+ AM/PM at 32 %) | SemiBold | −0.04 | 1.0 |
| Header date | 20 | Regular, MUTED | 0 | — |
| Clock time | 220 | SemiBold | −0.06 | 0.86 |
| Clock date | 26 | Regular, MUTED | 0 | — |
| Screen title (Apps, Settings, Edit dashboard) | 40 | SemiBold | −0.03 | 1.0 |
| First-start title | 52 | SemiBold | −0.04 | 1.0 |
| Tile title A / B | 28 / 30 | SemiBold (shrinks to 80 %, then "…") | −0.02 | 1.1 |
| Tile line | 17 | Regular, TEXT2 (accent when not installed) | 0 | — |
| Tile sub / small labels | 12 | Mono, uppercase, MUTED | +0.08 | — |
| Section labels (VEHICLE, FUEL, GENERAL…) | 13 | Mono, uppercase | +0.08 | — |
| Chips | 13 (12 in widget) | Mono, uppercase, TEXT2 | +0.06 | — |
| Widget outside temp tall / wide / clock | 76 / 64 / 36 | SemiBold | −0.05 / −0.05 / −0.03 | 0.95 / 0.95 / 1.1 |
| Widget values tall / wide | 24 / 28–30 | SemiBold | −0.02 | 1.1 |
| Buttons | 18 (16 small, 15 tile pins) | Medium | 0 | — |
| Dialog titles | 26–30 | SemiBold | −0.02 | — |
| Dialog body | 18 | Regular, MUTED | 0 | 1.45 |
| Incoming caller | 34 | SemiBold | −0.02 | — |

## Dimensions (units, radii in units)

| Element | Size / spacing | Radius |
|---|---|---|
| Screen padding | 32 × 28 (dashboards, Apps, Settings, editors); clock 40/32/40/40; first start 56 × 44 | — |
| Header | height 64, gap 20 to content; actions gap 12 | — |
| Pill buttons | height 56, padding 0 24 | 28 |
| Icon button (Settings) | 56 × 56, icon 24 | 28 |
| Phone chip | height 44, padding 0 16, dot 8 | 22 |
| Layout A | widget 380 wide; 3 × 2 grid, gap 20 | — |
| Layout B | widget height 168; 5 × 1 row, gap 16 | — |
| Tile A / B | padding 22 / 22 × 24; icon box 64 r16 glyph 32 / 72 r18 glyph 36; text gap 6 / 8 | 20 |
| Tall widget | 380 × fill, padding 24; cells 2 × 2 gap 12, padding 16 × 14; bar height 8 | 20, cells 14, bar 4 |
| Wide widget | fill × 168, padding 28 × 24, columns 190 / 170 / flex / 288, gap 28, 1-unit dividers | 20 |
| Hold menu | 520 wide, padding 24, rows 64 (r16), Cancel 56 | 24 |
| Replace picker | 960 × 620 (fits the window), padding 24, 3 columns, gap 12, 4 rows visible, icon box 56 r14 | 24, items 18 |
| Missing app dialog | 560 wide, padding 28, icon box 64; buttons 60 high, OK 120 wide | 24, buttons 16 |
| Incoming call | 860 × 156, 32 above the bottom, avatar 84, Decline/Accept 76 high | 28, buttons 38 |
| Active-call bar | height 64, padding 22/8, Mute/End 48 | 32, buttons 24 |
| Apps screen | search 300 × 56; segmented 56 (inner 46); cards: padding 16, icon box 72 r18, pin 44; gap 16; columns `floor((W − 48) / 205)` (6 at 1280, 9 at 1920); 2 rows per screen, scrolls | cards 20, pin 22 |
| Settings | layout card 600 wide, previews 150 high; switch 72 × 44, knob 36; swatches 52 with 2-unit ring, dot 40 | cards 20, previews 18 |
| Clock screen | radio column 420 + 48 padding, art 132 (r16), prev/next 92, play 100; quick buttons 88 high, gap 16 | 20 |
| Editors | tiles padding 18 (B 22/18), arrows 44, Remove 44; widget bar 68 (A) / 300 wide (B) | 20 |
| Toast | height 56, padding 0 28, 36 above bottom | 28 |
| Borders | 1 unit; dashed 4 on / 3 off | — |

Touch targets are at least 44 units (≥ 44 px on the unit).

## Named UI states

- **Dashboard** A / B; widget shown / hidden; free slots (A: "N slots free" Add tiles after
  Apps; B: "Free slot" Add tiles before Apps).
- **Tile**: normal, pressed, Android Auto accent (unless Bluetooth reports no phone),
  Not installed (accent line, opens Missing dialog), held (opens menu, never launches).
- **Phone chip**: Phone connected (green), Phone paired, Phone disconnected, No phone paired,
  Bluetooth off, No Bluetooth, Phone status unknown.
- **Vehicle widget**: Live (green), Door open (red chip, red door cell naming the doors),
  Stale (accent dot, values faint), No data (em dashes, explanation, Open CanService; disabled
  when no CanService app); per-reading fresh / stale / unavailable; demo attribution
  "Demo data · not live".
- **Overlays**: Hold menu, Replace picker (This tile / On dashboard disabled / category),
  Choose launch target (Current / Use default), Missing app, Incoming call (with or without
  verified commands), toast.
- **Call**: none, incoming, active with duration, active with unknown start ("In call"),
  muted / unmuted (only when the provider reports it).
- **Apps pins**: Add to dashboard, On dashboard (accent), Dashboard full (disabled),
  Shown as widget / Widget hidden (disabled).
- **Clock radio panel**: metadata present (station, programme, Preset X of Y, play/pause),
  WELLE without metadata (neutral play/pause, "Previous / next station"), controls
  unsupported (dimmed), radio app missing.
- **Settings**: layout A/B with "In use", idle 1 min / 5 min / Never, three switches,
  four accents; links "Widget values" and "Integrations" side by side.
- **Vehicle widget values**: live tall preview + seven places (Large value, Bar, Below the
  bar, Tile 1–4) each with Change; chooser shows CanService values (Current highlighted) and
  OBD-only values disabled ("Needs OBD"); the Bar only offers percentages.

## Icon inventory

Rendered at runtime by `IconView` from 24 × 24 SVG path data (`Icons.kt`, parser
`SvgPath.kt`), stroke 1.75, round caps/joins, path bounds snapped to whole pixels (crisp on
Android 4.x HWUI; verified on API 16). Any colour, so normal / active (accent) / disabled
(LINE2 or FAINT) / on-light (BG) variants need no extra files. The launcher icon is a vector
drawable that the build rasterises to PNG for ldpi–xxxhdpi (API < 21), adaptive on API 26+.

| Icon | Used for | Variants |
|---|---|---|
| RADIO | Radio tile, clock quick button, radio art (stroke 1.25) | TEXT, MUTED, FAINT |
| AA | Android Auto tile / quick button | accent, MUTED |
| PHONE | Calling tile, Accept, End (rotated 135°), Phone app button | TEXT, BG |
| FOLDER | Files tile | TEXT, MUTED |
| CAR, CAR_OUTLINE | CanService app, Widget values link, hidden widget placeholder (stroke 1.5) | accent, TEXT, MUTED |
| GRID | Apps tile, clock "Dashboard" button | TEXT, MUTED |
| CLOCK | header Clock button | TEXT |
| SLIDERS | header Settings, Apps & integrations link | TEXT |
| BACK / NEXT | back buttons, editor move arrows, settings links (chevrons, stroke 2) | TEXT, LINE2 (disabled), MUTED |
| PLUS | Add to dashboard / Add app | TEXT2 |
| SWAP, REMOVE, EDIT | hold menu rows, Edit links | TEXT |
| SEARCH | Apps search | MUTED |
| MIC | Mute / Unmute | TEXT, BG |
| PERSON | incoming caller avatar (stroke 1.5) | TEXT2 |
| ARROW | first-start Start button | BG |
| APP | apps without an icon, missing generic app (all other apps show their real launcher icon) | TEXT, MUTED |
| PREV, SKIP | radio previous / next (filled + stroke 2) | TEXT |
| PLAY, PAUSE, PLAY_PAUSE | radio play state; PLAY_PAUSE when the state is unknown (filled) | BG |

## Rendering notes for Android 4.x

- GradientDrawable 1-px strokes are drawn by HWUI 4.x from a texture at a half-pixel offset and
  smear over two pixels; `Box` (in `Ui.kt`) builds the border as a filled ring path, and dashes as
  filled quads (`Paint.getFillPath` results did not render on 4.1). Verified pixel-exact:
  `#FF9F0A` on one pixel, as in the reference.
- No AndroidX, no Material, no WebP/vector assets at runtime, no native code; one dex.
