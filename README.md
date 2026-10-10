# OpenDashboard

Dashboard and launcher for the Android car head unit (ADAYO AC822X, Android 4.2.2 / API 17,
1920 × 720). Two layouts with a CanService vehicle widget and quick tiles for WELLE (radio),
OpenAuto (Android Auto), the phone app and a file manager, an app grid, a clock/radio screen
and its own settings. Built from the "OpenDashboard · 1280 × 720 head unit" design canvas.

Plain Kotlin on platform Views and Canvas: no AndroidX, no native code, one dex, about 2 MB.
Runs on Android 4.1 (API 16) through 16.

- Integrations, evidence and blockers: [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md)
- Android 4.2 hand-off (colours, sizes, type, states, icons): [docs/HANDOFF.md](docs/HANDOFF.md)
- Feature checklist: [docs/CHECKLIST.md](docs/CHECKLIST.md)
- Screenshots (app next to the reference boards): [docs/screenshots](docs/screenshots)

## What works and what does not (short)

Working: both dashboards, editors, app grid with favorites (5 slots in A, 4 in B, stored per
layout), replace/remove/reorder, missing-app recovery, clock screen with idle switching,
settings, first start, launching and remapping of every quick app, WELLE previous/next/play
commands with now-playing display (needs the WELLE build that sends `me.ri3d.welle.STATE`),
Bluetooth phone status (public API), automatic Android Auto start. Next turn from Android Auto
on the Android Auto tile and the clock screen (needs the OpenAuto build that sends
`me.ri3d.openauto.NAV`; seen with a real phone while standing, not yet while driving).

Vehicle data from the head unit's CanService (catalog in docs/CANSERVICE-CATALOG.md) is
implemented and tested against a test double; the driver chooses which values the widget shows
(Settings › Widget values). It still needs a check in the car. Blocked: call detection and call commands. What is needed to finish each is in
docs/INTEGRATIONS.md.

## Build

| Tool | Version |
|---|---|
| JDK | 21 (Android Studio's JBR) |
| Gradle | 9.5.0 (wrapper) |
| Android Gradle Plugin | 9.3.3 (built-in Kotlin) |
| compileSdk / targetSdk / minSdk | 36.1 / 36 / 16 |

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`, copied to
`dist/opendashboard-1.1.0-debug.apk`. Signed with v1 (JAR, what Android 4.x checks) and v2.

### Release

`./gradlew :app:assembleRelease` builds the release APK with R8 (about 0.2 MB instead of 2 MB;
the debug-only demo fixtures are not in it). It is signed when a `keystore.properties` file
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) sits next to `settings.gradle.kts`;
without it the release build stays unsigned. The file and keystores are ignored by git. Updates
must be signed with the same key, so keep it backed up.

GitHub Actions (`.github/workflows/release.yml`, the same workflow as OpenAuto and Welle): pushing
a tag `v*` (or running it by hand) tests, lints and builds the signed release, uploads it as a
workflow artifact and attaches `opendashboard-<versionName>-release.apk` to the GitHub release.
The keystore comes from the repository secrets `KEYSTORE_BASE64` (the `.jks` file, base64),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. My Headunit can then offer updates with the
source `github:JanRi3D/OpenDashboard`.

## Install

Over ADB (USB or Wi-Fi):

```bash
adb install -r dist/opendashboard-1.1.0-debug.apk
```

Or from the phone with My Headunit ("APK from phone" on the Car screen). OpenDashboard asks for no
permission on Android 4.x; on Android 12+ it asks for "Nearby devices" (Bluetooth phone status).

## Use it as the Home launcher

OpenDashboard declares the HOME category, so it can replace the car's launcher:

1. Press the head unit's Home key (or `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME`).
2. In "Complete action using", pick **OpenDashboard** and **Always**.
3. To go back to the stock launcher: Android Settings › Apps › OpenDashboard › Clear defaults
   (or uninstall).

If the firmware never shows the chooser, the OEM launcher (`com.adayo.launcher`) is locked in;
OpenDashboard still works as a normal fullscreen app (start it from the OEM launcher). As the
launcher, Home always returns to the dashboard and Back on the dashboard does nothing.

## Demo fixtures (debug builds only)

Release builds contain no demo code. Debug builds have a receiver that reproduces the
reference's sample states, clearly labelled "Demo data · not live":

```bash
adb shell am broadcast -a me.ri3d.dashboard.DEMO -n me.ri3d.dashboard/.DemoReceiver --es vehicle live --es phone connected --es radio on
```

| Extra | Values |
|---|---|
| `vehicle` | `live` (14 °C, 62 %, 480 km, 12.6 V, 88 °C, 23.4 km), `door` (rear left open), `stale`, `nodata` |
| `phone` | `connected`, `paired`, `disconnected`, `none` |
| `call` | `incoming`, `incoming-noctl`, `active`, `active-noctl`, `none` |
| `radio` | `on` (Station name, Preset 3 of 6), `off` |
| `hide` | comma-separated packages treated as not installed (e.g. `me.ri3d.welle`) |
| `mode` | `off`: back to the real providers |

With the fixtures every one of the 22 boards can be reproduced; the dialogs (hold, picker,
missing app) are opened by long-pressing / tapping tiles.

## Project layout

```
app/src/main/java/me/ri3d/dashboard/
  Hub.kt            Application + the single shared state (prefs, apps, providers, navigation)
  Model.kt          Role, Prefs, Favorites, VehicleState, phone/call/radio state + control interfaces
  Apps.kt           launchable-app index, role targets, categories, launching
  Providers.kt      Bluetooth phone status, WELLE media-button commands
  MainActivity.kt   screens, overlays, idle timer, time receivers, Back/Home
  Dashboard.kt      header, tiles, layouts A/B, hold menu, picker, missing app, call UI
  VehicleWidget.kt  tall / wide / clock presentations
  Screens.kt        clock, apps, settings, integrations, editors, first start
  Ui.kt Icons.kt SvgPath.kt   palette, scaling, drawing (crisp on Android 4.x HWUI)
app/src/debug/      DemoReceiver (fixtures)
app/src/main/assets/fonts   Geist / Geist Mono static instances (SIL OFL)
```
