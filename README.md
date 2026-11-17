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

`.github/workflows/build.yml` builds and tests pull requests, and builds `main` on every push or manual workflow run. The optimized
APK is signed with a persistent private release key stored only in GitHub Actions secrets:
`AURA_KEYSTORE_BASE64`, `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, and `AURA_KEY_PASSWORD`.

If those secrets are not configured, CI generates a temporary key for the emulator test and deliberately does not publish a
release. Once the private key is configured, the APK lands on the repo's Releases page as `AuraAlarm.apk` (plus `mapping.txt`,
only for decoding crash traces). Build logs are pushed to the `build-logs` branch for non-PR runs.

The emulator test installs the candidate APK cleanly, sets a real alarm, checks that the MAIN alarm rings on time, and checks
that alarms survive a restart (simulated and real). It no longer claims to test an in-place update from the old public debug
key: Android rejects updates signed by a different key. An emulator is not a Samsung or another OEM phone, so this verifies
Android platform behavior, not each manufacturer's extra battery-management layer.

## Build loop (for an assistant with a sandbox)
`tools/ci/cycle.sh "message"` pushes the project to KillswitchYT/aura-alarm, waits for the Actions build (about 3 minutes)
and prints the Kotlin errors or BUILD SUCCESSFUL. `tools/ci/read.sh` re-reads the last run. Both need GH_TOKEN, or a file
/tmp/.gh_env containing: export GH_TOKEN='github_pat_...'. The sandbox cannot reach Google Maven or Maven Central, so it cannot
run Gradle itself. GitHub does the compiling.

## Build
Needs JDK 17+ and the Android SDK (platform 37, build-tools 37.0.0). Set `sdk.dir` in `local.properties`.

    ./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (this is the app; assembleDebug still works for a local debug build)

Toolchain mirrors the glass library: Gradle 9.7.1 (downloaded from GitHub), AGP 9.3.2, Kotlin 2.4.10, Compose 1.12.0,
io.github.kyant0:backdrop 2.0.1. targetSdk is 36; compileSdk and Build Tools are 37.

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

Release signing is deliberately separate from the debug key. Generate a private PKCS#12 keystore on a trusted device and keep
the file out of Git:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore aura-release.p12 -alias aura-release -keyalg RSA -keysize 3072 -validity 10000
```

Add these repository Actions secrets:
- `AURA_KEYSTORE_BASE64`: the one-line Base64 encoding of `aura-release.p12` (`base64 -w 0 aura-release.p12` on Termux/Linux).
- `AURA_KEYSTORE_PASSWORD`: the keystore password.
- `AURA_KEY_ALIAS`: `aura-release`.
- `AURA_KEY_PASSWORD`: the key password.

For a local signed build, set `AURA_KEYSTORE_PATH`, `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, and `AURA_KEY_PASSWORD`
in the environment before running `./gradlew assembleRelease`. Never commit the keystore or its passwords.

**Signing-key migration:** APKs signed with the new private key cannot update installations signed with the old public debug key.
Those installations must be uninstalled before installing the new release, which removes app data unless it is backed up first.
The old key is retained only in the immutable backup/history for rollback; it is no longer used for new release builds.
