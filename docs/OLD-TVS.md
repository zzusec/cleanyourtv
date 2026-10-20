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

Although this firmware advertises `cmd package set-home-activity`, requesting the new default
still left HOME resolving to the stock launcher (priority 2). A successful shell command alone
does not prove the default changed. The stock launcher was kept enabled, and no debloat list
was applied. ADB became offline again during verification, so a forced handover was not attempted.
The original launcher was verified in the foreground before that disconnect.

The device screenshots exposed clipped raster icons in that initial APK. The PNGs have since
been re-rendered at each icon's original dp size, and the original vectors restored for API 21+.
Pixel-level regression tests now check all 40 PNGs and the settings gear's transparent centre.
The corrected APK builds and passes lint and tests, but has not yet been installed on this TV
because ADB remains offline.

This is not a completed default-launcher replacement or a reboot test. The Xiaomi Mi TV 2 and
the Android 4.4 runtime remain unverified on physical hardware.
