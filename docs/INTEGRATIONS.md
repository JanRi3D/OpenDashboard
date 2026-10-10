# Integrations: interfaces, evidence, blockers

Status words: **verified** (exercised and observed), **built, unverified** (implemented
against a public API, not yet observed on the head unit), **blocked** (no usable interface
known; the UI shows a truthful unavailable state).

## Evidence used

| Source | What it gave |
|---|---|
| Head-unit captures `Downloads/20261003-143028`, `20261003-152941` (getprop, top, logcat, dumpsys window) | ADAYO AC822X, Android 4.2.2 / API 17, armeabi-v7a, 1920 x 720 @ 240 dpi, 88 px OEM status bar; packages `com.adayo.launcher`, `com.adayo.settings`, `com.adayo.service.mcu`, `com.adayo.localadapter.service`, `com.estrongs.android.pop`; MediaTek Bluetooth (`init.svc.mtkbt`); `CanService` log tag |
| `Welle/docs/REPORT.md` | Same device facts (memory 810 MB, Mali-400, OEM source manager holds audio focus) |
| WELLE APK `me.ri3d.welle` 1.0.0 (pulled from `HeadUnit_API16`) + `Desktop/Welle` source | Exported components and command semantics (below) |
| OpenAuto APK `me.ri3d.openauto` 0.2.0 + `Desktop/OpenAuto` source | Exported components (below) |
| `Desktop/My Headunit/README.md` | Installer product name ("ADB Car Manager" design renamed **My Headunit**); ES File Explorer component |
| Emulators `HeadUnit_API16` (4.1.2, 1024 x 600), `HeadUnit_1280` (4.1.2, 1280/1920 x 720 @ 240 dpi, created for this work), `Pixel_10_Pro_XL` (API 36) | Runtime verification |

## 1. Installed apps and launching — verified

- Discovery: `PackageManager.queryIntentActivities(MAIN/LAUNCHER)` on a background thread,
  labels and icons loaded once per refresh (`Apps.kt`). Own package excluded.
- Refresh: dynamic receiver for `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` (+ external-storage
  apps, screen-on). Verified on API 16: uninstalling OpenAuto turned its tile into "Not
  installed" (count 27 → 26); reinstalling recovered it without restarting.
- Launch: explicit component, `FLAG_ACTIVITY_NEW_TASK | RESET_TASK_IF_NEEDED`; failures
  (`ActivityNotFoundException`, `SecurityException`) show a toast, never crash.
- No ADB at runtime.

Default launch targets (each can be remapped in Settings › Apps & integrations):

| Role | Default | Evidence | Status |
|---|---|---|---|
| Radio | `me.ri3d.welle/.ui.PlayerActivity` | WELLE manifest (MAIN/LAUNCHER) | verified on API 16 |
| Android Auto | `me.ri3d.openauto/.LauncherActivity` | OpenAuto manifest (only exported activity) | verified on API 16 (incl. auto-start) |
| Calling | activity resolving `ACTION_DIAL`, else first launcher whose package/label matches phone/dialer | AOSP Contacts on API 16, Google Dialer on API 36 | **unverified on the unit**: its Bluetooth phone app is unknown |
| Files | `com.estrongs.android.pop` launcher (ES File Explorer) | process `.esfm` + `FileExplorerActivity` in the unit's logcat; My Headunit catalog | not installed on the emulators ("Not installed" path verified) |
| Vehicle (CanService) | launchable package matching `can(service|bus|box)` | none found | **blocked** (package unknown) |

## 2. WELLE radio — commands and now playing verified (API 16)

**Commands.** Explicit broadcast to the exported receiver `me.ri3d.welle/.MediaButtonReceiver`,
action `android.intent.action.MEDIA_BUTTON`, extra `Intent.EXTRA_KEY_EVENT`, a DOWN then an UP
`KeyEvent` (`Providers.kt`, object `Welle`). The receiver declares no permission, so an
ordinary app may send it.

| Command | Key | WELLE behaviour (source: `MediaButtonReceiver.java`, `RadioService.java`) |
|---|---|---|
| Previous station | `KEYCODE_MEDIA_PREVIOUS` | `RadioService.prev()` = previous entry of the current source's station list (wraps) |
| Next station | `KEYCODE_MEDIA_NEXT` | `next()` = next entry (wraps); not presets |
| Play / pause | `KEYCODE_MEDIA_PLAY_PAUSE` | `togglePlay()` |

**Now playing.** Added to WELLE (`Desktop/Welle`, uncommitted): `RadioService.publishState()`
sends the sticky broadcast `me.ri3d.welle.STATE` whenever something a dashboard shows changes,
at service start and on service stop (`playing=false`). Extras: `source` (`dab`/`web`),
`station`, `text` (DLS or stream title), `status`, `playing`, `state`
(`idle`/`loading`/`playing`/`error`), `index` and `count` (1-based position in the list that
next/previous step through), `preset` (1-based slot, 0 = none), `art` + `artVersion`.

**Station pictures.** `art` is a content URI served by WELLE's new read-only `ArtProvider`
(authority `me.ri3d.welle.art`): `/slide` (last DAB slideshow picture, written to WELLE's cache)
or `/logo/<station id>` (the logo WELLE stores, sanitised file name). WELLE picks the same
picture its player shows: slideshow if enabled and received, else the logo. OpenDashboard
decodes it off the main thread (bounded to ~264 px) and shows it in the clock screen's art box
and the Radio tile; no picture → radio glyph. Observed: radioeins and WDR logos, glyph for the
station without a logo. DAB slideshow not observed (no tuner on the emulator). Permission
`android.permission.BROADCAST_STICKY` (normal) in WELLE's manifest. Sticky, because Android 4.x
has no MediaSession and the dashboard may start after the radio.

OpenDashboard registers for it while WELLE is the Radio target and shows source, station,
programme text, "Station X of Y" (or "Preset P · Station X of Y") and the real play/pause
state on the clock screen and the Radio tile.

Observed on `HeadUnit_API16` with the user's web stations: Next → "Rock Antenne", stream title,
"Station 5 of 7", pause icon; Play/pause → play icon; Previous → "Bayern 3", "Station 4 of 7";
WELLE's `files/events.log` shows `command NEXT/TOGGLE/PREV` for each press.

Older WELLE builds (e.g. the one currently on the head unit) send no state: the panel then says
"No now-playing info from WELLE yet", the buttons still work and show "Sent to WELLE". Update
WELLE on the unit (My Headunit) to get the display. A sticky broadcast survives until reboot:
if WELLE is killed without stopping, the last state stays visible (its service is
START_STICKY while playing and republishes when it restarts).

## 3. OpenAuto — connection status not available; next turn built, unverified

Only `LauncherActivity` is exported; `ProjectionService` and `ProjectionActivity` are not.
"Wireless · ready" from the design is therefore never shown; the tile says "Tap to start" (or
"Connect your phone" when Bluetooth reports no phone).

**Next turn.** Added to OpenAuto (`Desktop/OpenAuto`, uncommitted): it declares Android Auto's
navigation status channel (type IMAGE, 256 × 256 arrows, as openDsh/openauto does) and passes
every status, turn and distance event on as the sticky broadcast `me.ri3d.openauto.NAV`
(`ConnectionManager.onNavigation`). Extras: `status` (`active`/`rerouting`/`inactive`), `road`,
`maneuver` and `direction` (aasdk ManeuverType/ManeuverDirection), `exit` (roundabout),
`image` (Maps' PNG arrow), `meters`, `seconds`, `distance` (displayed value × 1000) and `unit`
(aasdk DistanceUnit). It sends `inactive` when a session starts and ends.

OpenDashboard (`Providers.kt`, object `OpenAutoNav`) shows the turn while a route runs, unless
Settings › Next turn is off: the Android Auto tile becomes the arrow, distance and road, and on
the clock screen the turn takes the vehicle values' place. Without a picture it draws a
straight/left/right/U-turn arrow from the maneuver. Screens re-render only when the shown
distance, road or arrow changes.

Test without a phone (no picture through `am`):

```bash
adb shell am broadcast -a me.ri3d.openauto.NAV --es status active --es road Hauptstrasse --ei maneuver 4 --ei direction 1 --ei meters 352 --ei distance 350000 --ei unit 1
```

Observed with a real phone (Samsung SM-S948B, head-unit server over Wi-Fi, OpenAuto on
`HeadUnit_1280`, 2026-10-10): the channel opens, status active, turn "Richtung Fontanestraße"
(DEPART, direction unspecified) with a 1739-byte PNG, repeated every second unchanged; one
distance event of 0 m (shown as no distance, like Maps). No other message types arrived. Maps'
picture is a white arrow on opaque black: OpenDashboard turns brightness into opacity and draws
it in the accent (`turnIcon`). Not yet observed: the countdown while driving, later maneuvers,
rerouting. The broadcast is readable by any app on the unit (street names of the route). If OpenAuto is
killed mid-route the last turn stays until its next session.

**Map picture.** Not built: the map is the projection's video stream, decoded into OpenAuto's
own surface; showing it in a dashboard tile would mean a second decoder or passing a surface
between apps, plus forwarding touch.

## 4. CanService vehicle data — built against the supplied catalog; protocol verified with a test double

Interface (from the owner's signal catalog, [CANSERVICE-CATALOG.md](CANSERVICE-CATALOG.md)):
package `com.adayo.canservice`, bind action `com.adayo.can.canservice.action`, binder
descriptor `com.adayo.canproxy.binder.service.ICanboxInterface`. Indexed read: interface token,
`writeInt(signal ID)`, `transact(code, flags 0)`, `readException`, `readFloat` (NaN = failure).
Implementation: `CanService.kt` (bound for the app's lifetime with `BIND_AUTO_CREATE`, all
binder calls on a background thread, results applied on the main thread).

The driver chooses what the widget shows (Settings › Widget values, the editor's "Values"
button, or holding the widget): a large value, the bar (percentages only), the line below the
bar and four tiles; layout B shows the first three and tiles 1–2, the clock screen the first
three. Defaults: outside temperature, fuel, range, doors, cabin temperature, speed, odometer.

| Value | Signal | Encoding used |
|---|---|---|
| Outside temperature | climate child binder (tx 1), child tx 4, ID 16 | °C, −60…80 accepted (label inferred in the catalog) |
| Cabin temperature | climate ID 38 | °C, −40…85 |
| Climate set temperature | climate ID 17 | °C, 0…40, one decimal |
| Fuel level | BodyDetailsInfo (tx 6) 13 `FUEL_LEVEL_VALUE` | 0–100 % (catalog) |
| Battery charge (hybrid/EV) | BodyDetailsInfo 108 `BMS_SOC` | 0–100 % (NaN on a car without one) |
| Range | BodyDetailsInfo 12 `IC2_STATUS_ENDURMILEAGEVALUE` | km, 0–3000 |
| Speed | standalone tx 11 (no ID) | km/h, 0–300 |
| Odometer | BodyDetailsInfo 10 `ODOMETER_VALUE` | km (the source app prefers OBD for it) |
| Doors, boot, bonnet | BodyDetailsInfo 7 driver (front left, LHD), 6 passenger, 5 rear left, 4 rear right, 3 boot, 2 bonnet | **assumed** 0 = closed, 1 = open; anything else = unknown |
| 12 V battery, coolant, trip | not offered by CanService (OBD only in the source app) | listed as "Needs OBD", not selectable |

Polling: no callback is documented, so signals are read every 2 s while an OpenDashboard screen
is visible (`setActive` from `onResume`/`onPause`), and only those the widget currently shows,
plus the doors (they drive the door-open warning whatever the tiles show). A failed read (NaN)
keeps the previous value, which turns **Stale** after 10 s.

Lifecycle: if CanService dies, Android rebinds when it restarts; if the binding is lost for
good (force-stop), a watchdog rebinds after 30 s; if the package is missing or refuses the
bind (`SecurityException`), the values are cleared and the widget shows **No data** with the
reason. Settings › Apps & integrations shows the link state and the **raw door values** (e.g.
`FL 0 · FR 0 · RL 1 · …`) so the door encoding can be checked in the car.

Verified on `HeadUnit_API16` with a throwaway test double (package `com.adayo.canservice`
answering exactly as the catalog describes, built in the scratchpad and uninstalled
afterwards): binding after install via the package broadcast, all reads (BodyDetailsInfo 13,
12, 7…2 and climate 16 through the child binder), live values (61 %, 472 km, 13.5 °C → "14 °C"),
door open → red "Rear left", closed again → "Closed"; process killed → Android restarted it and
polling resumed; force-stop → **Stale** after 10 s → watchdog rebind → **Live**; uninstalled →
**Stale** → **No data** ("CanService isn't installed on this head unit").

With the value choice, the test double also answered speed (tx 11), odometer, cabin and set
temperature; the log showed only the shown values and the doors being read.

Still to verify in the car: that an ordinary app may bind (no permission on the service), the
door encoding and driver-door side, the units of range/odometer/speed, and the climate IDs
(16 outside, 38 cabin, 17 set temperature), which the catalog marks as inferred.

## 5. Phone connection — built, unverified on the unit

Public API only (`Providers.kt`, object `Bluetooth`): `BluetoothAdapter` state, bonded devices
(major class PHONE or unknown), `ACTION_ACL_CONNECTED/DISCONNECTED`, plus
`getProfileConnectionState(HEADSET/A2DP)` at start. Permission `BLUETOOTH` (normal, ≤ API 30)
and `BLUETOOTH_CONNECT` (runtime, API 31+).

Android 4.x cannot ask "is this device connected?", so a paired phone at start-up is shown as
**Phone paired** (connection unknown) until an ACL event arrives; **Phone connected** appears
only after an observed connection; **Phone disconnected** after an observed disconnect. Other
states: No phone paired, Bluetooth off, No Bluetooth, Phone status unknown (no permission).

Verified: API 16 emulator (no adapter → "No Bluetooth"), API 36 emulator (adapter, nothing
paired → "No phone paired", AA tile switches to "Connect your phone"). Not verified: whether the
unit's MediaTek stack in hands-free role raises the framework ACL broadcasts.

Auto-start Android Auto: fires once per observed not-connected → connected event, only when
the setting is on, onboarding is done, no call is ringing or active, and not within 30 s of the
previous start. Verified with the debug phone fixture on API 16: first connect launched
OpenAuto; a duplicate connect did not; a connect during an active call did not; a disconnect
followed by a new connect launched it once.

## 6. Calls — blocked

No public API on API 17 for the hands-free (HFP client) role: `BluetoothHeadsetClient` is a
hidden API from 5.0, and `TelephonyManager` describes the unit's own (absent) modem. The OEM
Bluetooth phone app and any broadcast/AIDL it offers are unknown. Detecting a call would not
prove that answering, declining, muting or hanging up is permitted.

Production: `Hub.callControl = null`, no call UI appears, Settings › Apps & integrations says
calls stay in the head unit's phone app, and the Calling tile opens that app.

Ready boundary: `Hub.setCall(CallState)` (stamps the duration on the confirmed INCOMING →
ACTIVE transition with `elapsedRealtime`; a call recovered as already ACTIVE shows no
duration) and `CallControl` (`canAnswer/canDecline/canEnd/canMute` + commands; mute is shown
only when the provider reports it). The incoming card falls back to "Answer on the phone app
or wheel" + "Phone app" when commands are unverified. Debug fixtures exercise both variants.

Needed: the package of the unit's Bluetooth phone app (`dumpsys window` while it is open),
its manifest (exported receivers/services/permissions) and logcat during a real incoming
call, answer, mute and hang-up.

## 7. Home launcher — built; Back behaviour verified on API 36

HOME + DEFAULT intent filter, `singleTask`. Home while OpenDashboard is the launcher returns to
the dashboard; Back at the root is consumed only when OpenDashboard is the default Home,
otherwise the task moves back. Package-visibility `<queries>` include HOME (without it, API
30+ resolved Home to OpenDashboard itself and swallowed Back; fixed and re-verified). Whether
the ADAYO firmware lets a third-party Home replace `com.adayo.launcher` is unverified.
