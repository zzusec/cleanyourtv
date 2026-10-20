# Old TVs: Android 4.4 and 7

The launcher builds down to **Android 4.4 (API 19)**. That is what the two old sets need: the Xiaomi
Mi TV 2 runs 4.4.2 (MIUI TV, not Android TV), the Sony BRAVIA 4K 2015 runs 7.0. Newer TVs keep the
behaviour they had, because every newer API sits behind a `Build.VERSION` check.

## What had to change

| Was | Needs | Now |
|---|---|---|
| `minSdk 26` | — | `minSdk 19` |
| `android:fontFamily="@font/outfit_light"` | API 26 | static instances in `assets/fonts`, applied by `Fonts.java` from `android:tag="font:light"` |
| `android:fontVariationSettings` | API 28 | same: one static file per weight, so Android 7 draws the right weight |
| vector drawables (`@drawable/ic_wifi` …) | API 21 | PNGs per density below 21; original vectors in `drawable-anydpi-v21` on 21+ |
| `android:tint`, `Drawable.setTint` | API 21 | `Tint.set` (a SRC_IN colour filter) and `TintedImageView` |
| `android:drawableTint` | API 23 | `MainActivity.tintPillIcon`, following focus |
| `android:textColor="@drawable/pill_text"` | 21 for a colour selector | moved to `res/color/pill_text.xml` |
| `android:letterSpacing` | API 21 | `values-v21/styles.xml` |
| `Theme.Material.*` | API 21 | `Theme.Holo.NoActionBar.Fullscreen` below 21, Material from 21 |
| `android:elevation` | API 21 | `layout-v21/` copies |
| `NetworkCallback`, `NetworkCapabilities`, `getAllNetworks` | API 21 | `Platform` / `PlatformNet`, loaded only on 21+ |
| `TvInputManager`, `TvInputInfo`, `TvContract` | API 21 | `Platform` / `PlatformTv`, loaded only on 21+ |
| `getLeanbackLaunchIntentForPackage` | API 21 | `Platform.launchIntent` (falls back to `getLaunchIntentForPackage`) |
| `Canvas.drawRoundRect(float…)` and `(RectF…)` | API 21 | `RoundRect` uses the RectF overload, which is API 1 |
| `integer-array` resources | API 21 | constants in `HomeKeyService` |
| `cmd package set-home-activity` | Android 6/7 | `build-and-install.sh` falls back to `pm disable-user` + the HOME intent |

## App grid on a non-Android-TV set

MIUI TV apps and sideloaded APKs often declare only `CATEGORY_LAUNCHER`. The grid now merges that
list with `CATEGORY_LEANBACK_LAUNCHER`; an "empty means fall back" test does not work, because the
launcher itself declares a leanback entry, so the leanback query never comes back empty.

## Per-model notes

`examples/xiaomi-mi-tv-2/` and `examples/sony-bravia-4k-2015/` hold the debloat lists, with what to
keep and what the switch does on each. Both are marked "verify against `pm list packages` first":
the exact package set differs by firmware build, and `pm disable-user` on a missing package is a
harmless "not found".

The Sony 2015's adbd is fragile. It drops after a burst of connections and often does not come back
after a reboot until USB debugging is switched on again on the TV. Batch ADB work, do not run it in
parallel, and use `tools/tv.py` (a separate ADB client) if the stock `adb` binary reports
"No route to host" while port 5555 is open.

## Sony 2015 device check (2026-10-20)

The initial launcher 2.4 APK installed and rendered on the SVP-DTV15 / Android 7.0 device. D-pad navigation,
the network settings entry and the main Sony settings entry were exercised successfully.
The actual stock HOME component is `com.google.android.leanbacklauncher/.MainActivity`, not
`com.google.android.tvlauncher`.

The initial non-disabling attempt with `cmd package set-home-activity` still left HOME resolving
to the stock launcher (priority 2). A successful shell command alone does not prove the default
changed. ADB also became offline during that attempt; the stock launcher remained enabled until
the later, explicitly authorized handover below.

The device screenshots exposed clipped raster icons in that initial APK. The PNGs have since
been re-rendered at each icon's original dp size, and the original vectors restored for API 21+.
Pixel-level regression tests now check all 40 PNGs and the settings gear's transparent centre.
After USB debugging was toggled on the TV, the corrected APK was installed successfully at
15:24 on 2026-10-20. Its on-device APK SHA-256 was read back and matched the local build:
`93d6f1d59293e1c24c94939cb94de5b82c7fc9c3a0cc92072d403d30ffb757a5`.
The launcher was confirmed in the foreground, and a new device screenshot shows complete
top-bar icons. At that stage, the disabled-package list was unchanged and HOME still resolved
to the stock launcher.

### Authorized default-home handover

After the user explicitly approved disabling the stock launcher with tested recovery, a
TV-local detached watchdog was rehearsed with ADB disconnected. It re-enabled the stock
package, restored its HOME preference, launched it and checked the actual foreground.
A second rehearsal disabled the stock launcher, verified the new HOME and Sony settings,
then disconnected ADB and successfully let the TV restore the disabled stock launcher.

The final switch disabled only `com.google.android.leanbacklauncher` for user 0; its system
APK and data remain intact. The new preferred HOME is `com.gathin.tvhome/.MainActivity`.
The disabled-package list changed only by that stock package. The actual HOME key, a fresh
launcher process, the new launcher's Settings pill, Home from settings, and a standby/wake
cycle were verified. The stored preference has `mAlways=true`; the stock package has
`installed=true` and `enabled=3` (disabled by the user).

The final watchdog was disarmed only after those checks passed. It is temporary handover
protection, not a permanent boot-time recovery service. No firmware, root access, system
partition changes, uninstallation, or general debloat list was involved.

A subsequent guarded system restart at 17:36 on 2026-10-20 preserved the new default HOME
and foreground launcher. An unplug/replug start remains untested. This TV's ADB can require
local debugging and authorization to be renewed afterward. Recovery after confirmation still
requires working ADB; there is no verified physical-remote rescue path.
Do not uninstall the new launcher while the stock launcher is disabled. The Xiaomi Mi TV 2
and Android 4.4 runtime remain unverified on physical hardware.

### Launcher 2.5: hiding and restoring icons

Hold OK on an app tile and choose Hide icon; App info is still available in that menu.
Hold OK on Settings, open Launcher settings, then Hidden apps to select and restore icons.
This only filters tiles, dock entries and module cards. It never disables or uninstalls a
package. User choices survive launcher restarts, in-place APK updates and layout/theme resets;
theme/configuration `hidden_apps` remains a separate filter.

The release build, lint, 61 JVM tests (including 10 visibility regressions) and 18 Python
tests passed. On the Sony, a TV-local 600-second rollback was armed before installing 2.5
(versionCode 6), signed with the same key as 2.4. The updated version, default HOME and actual
foreground were checked. A sustained D-pad OK press opened the menu without activating its
first row on release. App info, hiding a test tile, process-restart persistence and in-place
reinstall persistence were exercised successfully.

ADB went offline again before the restoration dialog could be exercised. Reconnecting the
target and an independent Python ADB handshake both failed; USB debugging must be toggled
locally before completing that check and restoring the test tile. The temporary rollback
was not disarmed while verification was incomplete. Its final outcome still needs to be read
back from the TV. Do not count this as complete on-device hide/restore acceptance.
The API 21 emulator could not start this time because the host had insufficient free disk
space; earlier 2.4 emulator results do not establish 2.5 runtime compatibility.

### Compact home spacing and Back navigation (2026-10-20)

The grid's top padding now has a 120dp maximum instead of consuming all spare vertical space.
It still shrinks to the available height and never becomes negative. Three geometry regressions
cover tall, short and overflowing grids; all 64 JVM tests, 18 Python tests, the release build
and lint pass (lint still reports existing warnings).

The spacing APK was installed at 18:04 under temporary rollback protection. After ADB
authorization was renewed, its APK was read back and matched the local release SHA-256:
`0f56aa43e9abe7a2472d322a6a21b0e2f54d73b810b436ebfa52038bc700aaef`.
A fresh screenshot confirms the compact layout. The stock launcher remains disabled and
HOME resolves to this launcher. No rollback watchdog process is running.

The user confirmed that the physical remote's Home button opens this launcher. Back is not
globally remapped: it keeps application navigation and dialogs intact, and returns to this
HOME when the application exits. Device checks traversed Date/Time settings back through
the Sony settings panels to this launcher. Opening Galaxy Kiwi from its launcher tile,
pressing Back on its home page and confirming its own exit dialog also returned here.
Applications can handle Back themselves; their exit confirmations are not bypassed, and
these checks do not establish the behavior of every installed application.

The accessibility manager reported `services:{}` during these checks. A saved
`HomeKeyService` setting is not proof that the service is bound; neither verified return
path depends on it. No global Back interception or additional APK installation was needed.
