# Aura Alarm (native)

Privacy-first alarm clock. 100% Kotlin and Jetpack Compose. No WebView, no INTERNET permission, no analytics.

## Status
- Engine: DONE and tested. `tools/run-engine-tests.sh` compiles it against android.jar and runs 84 checks.
- Screens, build 1: WRITTEN, NOT YET COMPILED. Home (clock hero, search, alarm list with glass toggles), the alarm editor
  sheet, floating glass tab bar and add button, a Setup tab with the 7 protection checks and fixes, and the full-screen ring
  screen with Math and Shake challenges and volume-button handling.
- Screens, still to come: World, Timers, Stopwatch, the rest of Setup (bedtime, backup, ring style, volume buttons,
  12/24h, self-test), night clock.

## Liquid glass
The glass comes from Kyant0/AndroidLiquidGlass (io.github.kyant0:backdrop). `glass/` holds that project's example components
(button, toggle, slider, bottom tabs) with the package renamed and the colours changed to the Aura palette, under its
Apache-2.0 licence (third_party/). `ui/AuraApp.kt` shows the pattern: one `layerBackdrop` box holds the scene and the page,
and glass pieces float above it and refract it. Lens refraction needs Android 13, blur needs Android 12.

## Build on GitHub (no PC needed)
`.github/workflows/build.yml` builds on every push to main and on "Run workflow". The APK lands on the repo's Releases page
as `app-debug.apk`, and a log of the build is pushed to the `build-logs` branch. Debug builds use the fixed key in
`app/debug.keystore` (the standard public "android" credentials), so each build installs over the last one.

## Build
Needs JDK 17+ and the Android SDK (platform 37, build-tools 37.0.0). Set `sdk.dir` in `local.properties`.

    ./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk

Toolchain mirrors the glass library: Gradle 9.7.1 (downloaded from GitHub), AGP 9.3.2, Kotlin 2.4.10, Compose 1.12.0,
io.github.kyant0:backdrop 2.0.1. targetSdk stays at 34 on purpose.

## Engine (app/src/main/java/com/killswitch/aura)
| File | What it does |
|---|---|
| Alarm, Store | Alarm model and storage. Same JSON keys and prefs file as the web build, so old alarms load. |
| Scheduler, AlarmReceiver, BootReceiver | setAlarmClock plus a backup 2 min later, bedtime reminder, re-arm on boot, update, clock change, exact-alarm grant. |
| RingService | Foreground service: sound, vibration, wake lock, gradual volume, ring style, wake challenges, timer rings, notification fallback. |
| Timers, Stopwatch | Multiple timers with +1 min / Stop notification buttons. Stopwatch with laps. |
| Challenge | Math (3 sums) and Shake (25 shakes) wake-up challenges, plus the accelerometer binding. |
| WorldClock | 229 curated cities plus every other time zone, search with nicknames, offsets, day labels, converter. |
| Settings, Backup | 12/24h, volume-button action, ring style, bedtime. Backup JSON is compatible with the web build. |
| Perms | The 7 setup checks and their fixes, including the manual Samsung one. |
| ClockWidget, Intents, Screen | Home-screen widget, voice-assistant intents (alarm, timer), night-clock brightness. |

## Signing
Debug builds use ~/.android/debug.keystore. Keep it: Android only updates an app in place when the signature matches.
