# Feature checklist

**Tested** = exercised and observed on an emulator (named). **Built** = implemented, not
exercised. **Blocked** = no usable interface; truthful unavailable state shown.
**Unit** = needs the ADAYO AC822X; nothing here was run on the real head unit.

Emulators: **E16** `HeadUnit_API16` (Android 4.1.2, 1024 × 600 mdpi, 256 MB) ·
**E16w** `HeadUnit_1280` (Android 4.1.2, 240 dpi, run at 1280 × 720 and 1920 × 720) ·
**E36** `Pixel_10_Pro_XL` (Android 16, API 36). Comparison screenshots: `docs/screenshots`
(`app-1280` next to `reference`).

## Platform

| Item | Status |
|---|---|
| minSdk 16 (unit is API 17, test AVD API 16), targetSdk 36, v1 + v2 signed (`apksigner --min-sdk-version 16` verifies) | Tested |
| Installs and runs on API 16 and API 36 | Tested E16, E16w, E36 |
| No AndroidX, no native libraries, single dex, APK 2.0 MB | Tested |
| Release build with R8 (0.2 MB), signed from keystore.properties; smoke-tested on API 16 (dashboard, clock, widget values, chooser, apps) | Tested E16 (signed with the debug key for the test) |
| GitHub Actions release workflow (tag `v*` → signed `opendashboard-<version>-release.apk` on the release) | Built; runs once the four signing secrets are set on GitHub |
| Cold start (`am start -W`, TotalTime) | Tested: 127–215 ms E16/E16w, 411–500 ms E36 |
| Memory (`dumpsys meminfo`, total PSS) | Tested: 7.9 MB at 1024 × 600, 10.1 MB at 1920 × 720 |
| Fullscreen: status bar hidden on 4.x (theme), sticky immersive on 19+, cutout padding on 28+ | Tested E16w, E36 |
| Scaling to 1280 × 720, 1920 × 720, 1024 × 600 without clipping | Tested E16w, E16 |
| Unit tests (favorites capacity, vehicle freshness/doors, SVG arcs, all icons parse) + lint (0 issues) | Tested |

## Visual match (reference board → implementation)

| Board | Status |
|---|---|
| Dashboard A, Dashboard B | Tested E16w 1280 (pixel-level comparison) and 1920 |
| Dashboard A · Edit, Dashboard B · Edit | Tested E16w |
| App Dashboard | Tested E16w 1280 (6 columns) and 1920 (9 columns) |
| Clock screen | Tested E16w |
| Settings | Tested E16w, E16 (adds an "Apps & integrations" link) |
| First start | Tested E16w, E16 |
| CanService widget states (tall / wide / clock; live, door open, no data, stale) | Tested E16w |
| Hold a tile, Replace app | Tested E16w (A), E16 (B) |
| App not installed | Tested E16w (B, Files missing) |
| Incoming call, In call | Tested E16w (B) with debug fixtures |
| Widget hidden | Tested E16w (B); A uses the same code, not captured |

## Dashboards and tiles

| Item | Status |
|---|---|
| Header: real time/date, 24 h / 12 h (AM/PM suffix), phone chip, Clock and Settings buttons | Tested E16w |
| React to TIME_TICK / time / timezone / date broadcasts (registered while visible) | Built; minute ticks Tested, timezone change not exercised |
| Layout A: tall widget, 3 × 2 grid, permanent Apps, "N slots free" Add tiles | Tested |
| Layout B: wide widget, 5 tiles, "Free slot" Add tiles | Tested |
| Separate favorites per layout, A 5 / B 4 slots, Apps not counted | Tested (A at 5/5 while B kept its 4) |
| Layout choice persistent | Tested (force-stop + restart) |
| Radio tile launches WELLE | Tested E16w (1920) |
| Android Auto tile launches OpenAuto; accent outline unless Bluetooth reports no phone; no unverifiable readiness text | Tested E16w, E36 |
| Calling tile opens the DIAL / phone app | Built (target resolves to "Phone" on E16, Dialer on E36); Unit: OEM Bluetooth app unknown |
| Files tile → ES File Explorer default; "Not installed" when absent | Tested E16 (absent → Not installed) |
| Apps tile with real app count | Tested |
| Launch failure handled (toast, no crash) | Built |
| Launch targets remappable (Radio, AA, Calling, Files, Vehicle) and "Use default" | Tested E16 |

## App grid and favorites

| Item | Status |
|---|---|
| Real launchable apps, alphabetical, real icons (reference glyphs for the role apps) | Tested |
| Refresh on install / remove / update | Tested E16w (OpenAuto removed → Not installed, reinstalled → recovered) |
| Case-insensitive name search, "No app matches" state | Tested |
| All apps / On dashboard filter | Tested |
| Add to dashboard / On dashboard (accent) / Dashboard full (disabled, launching still works) | Tested |
| Counter "Quick open · X of N slots" layout-aware; Dashboard returns to the opening layout (B→Apps→B regression) | Tested |
| "Shown as widget" for CanService, included in On dashboard | Built (no CanService app on emulators) |

## Tile menu, picker, editors

| Item | Status |
|---|---|
| Long press opens the menu without launching | Tested E16w, E16, E36 |
| Mouse right-click opens the menu (button state API 14, context click API 23) | Built; not exercised |
| Replace: real apps, "This tile", others "On dashboard" disabled, in-place replacement, count unchanged, also when full | Tested |
| Remove from dashboard frees the slot immediately | Tested E16 |
| Edit dashboard → editor of the current layout | Tested |
| Editor move left/right (ends disabled), Remove, Reset (this layout + widget only), Done → dashboard | Tested E16 |
| Editor Hide / Show widget, synchronized with Settings | Tested E16w |
| Editor "Add app" / dashboard Add tile → Apps | Built |

## Vehicle widget (CanService)

| Item | Status |
|---|---|
| CanService binding and reads (fuel, range, doors, outside temp) per the supplied catalog | Tested E16 against a test double; Unit: bind permission, door encoding, units unverified |
| Stale on read failure / service loss, recovery on restart, No data when uninstalled or refused | Tested E16 with the test double |
| Raw door values in Apps & integrations for in-car verification | Tested |
| Driver chooses the widget's values (large value, bar, below the bar, 4 tiles); live preview; reset; persisted; only shown values polled | Tested E16 with the test double |
| 12 V battery, coolant, trip | Not provided by CanService (OBD only): listed as "Needs OBD", not selectable |
| No data state: em dashes, explanation, Open CanService (disabled without an app) | Tested |
| Live, door open (names the doors, red), stale (10 s, explicit), recovery | Tested with debug fixtures |
| Per-reading availability; malformed values rejected; unknown doors never "Closed" | Tested (unit tests) |
| Widget visibility persistent; hidden → tiles grow, capacity unchanged | Tested |

## Clock and radio

| Item | Status |
|---|---|
| Clock layout, date, compact vehicle row, quick buttons | Tested |
| Idle → clock after 1 min (5 min, Never options); interaction restarts the timer; vehicle/clock updates do not | Tested (1 min) |
| Idle suppressed during calls, dialogs, editing, other screens | Built |
| WELLE previous / next / play-pause commands | Tested E16, E16w (WELLE logged `command NEXT/TOGGLE/PREV`) |
| Station name, programme text, play state, "Station X of Y" from WELLE | Tested E16 with WELLE + its new `me.ri3d.welle.STATE` broadcast (uncommitted change in `Desktop/Welle`); Unit: needs that WELLE build installed |
| Station logo in the art box and on the Radio tile (WELLE ArtProvider); glyph when none | Tested E16 (web station logos) |
| DAB slideshow picture | Built (WELLE writes it for the provider); needs the DAB tuner |
| Older WELLE without the broadcast: honest "No now-playing info" panel, buttons still sent | Tested |

## Phone and calls

| Item | Status |
|---|---|
| Phone status from Bluetooth (paired vs connected vs unknown vs off) | Tested E16 (no adapter), E36 (nothing paired); Unit unverified |
| Auto-start Android Auto once per connection, not on duplicates/resume, not during calls | Tested E16w with the phone fixture |
| Incoming call detection, caller, answer, decline, mute, end | **Blocked** (no interface); UI Tested with fixtures: incoming card, accept → call bar with monotonic duration, mute state from provider |
| Decline / End buttons, "no commands" card variant, recovered call without duration | Built (fixtures exist), not captured |

## Settings, first start, navigation

| Item | Status |
|---|---|
| Layout A/B with "In use", Edit tiles link | Tested |
| Clock screen 1 min / 5 min / Never | Tested (1 min) |
| Vehicle widget, 24-hour clock, Start Android Auto switches | Tested |
| Accent Amber / Blue / Green / White; status colours unchanged | Tested (Amber, Blue) |
| Defaults: A, 5 min, widget on, 24 h, auto AA on, Amber | Tested (fresh install) |
| First start once; CanService found/not found vs live; quick apps found; Start follows the selection | Tested |
| Back: dialog → screen stack → (launcher: stay / app: to background) | Tested E16w, E36 |
| Home while default launcher → dashboard | Built; Unit: whether the ADAYO firmware allows a third-party Home is unverified |
| Sleep / wake: time and providers refresh on resume / screen-on | Tested E36; E16w emulator hung on screen-off (emulator adbd, not the app) |
