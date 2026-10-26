# Build and verification handoff

## Build prerequisites

The source is at `rebuild/`. Current host checks found Java and Node, but no `gradle`, `adb`, Android SDK or `android.jar` in the expected locations. No APK was produced. The repository also has no Gradle wrapper or Git metadata.

Use a machine with JDK 17, Android SDK platform 35/build tools, Gradle 8.11.1+ and an Android device or emulator. From `rebuild/`, run:

```powershell
gradle wrapper --gradle-version 8.11.1
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n app.aavideoplayercar/.MainActivity
```

Those commands are instructions for the next developer; they have not been run here. If Gradle or Android plugin compatibility differs on the build host, use the error output to adjust and record the final known-good versions. Do not claim build success until an APK exists and installs.

## Checks already completed

- New package name agrees across Java, Gradle and manifest resolution; display name is in `strings.xml`.
- Manifest and strings XML parse successfully.
- MainActivity braces are balanced.
- Original APKs were not modified by the rebuild work. The earlier reverse engineering dossier reports archive extraction, hash and signature checks in `analysis/verification.json` and `analysis/signature-verification.json`.

These checks do **not** establish Java compilation or runtime correctness.

## First smoke test after build

1. Launch the new app on an unlocked phone; verify dashboard renders and each of the 19 labels responds.
2. Open Browser and each YouTube WebView route on a connected network; record any site refusal, media policy or playback failure. Test Back navigation behavior explicitly.
3. Pick and play a local video/audio document; pick an image. Repeat after process restart and permission revocation.
4. Deny and grant location permission; verify speedometer displays an explanatory state, then speed on GPS input. Check foreground/background cleanup.
5. Save two maintenance notes, restart, and verify ordering/persistence.
6. Open every unimplemented route and verify the app remains stable and returns home.
7. Capture `adb logcat`, screenshots, Android version, device model, APK hash and exact source revision/zip for every result.

## Parity verification when features are added

Track each item as pass/fail/not tested in `HANDOFF-FEATURES.md` or a test report, with device and build ID:

- Dashboard defaults, reorder, persisted last module, theme/language and process restart.
- Media formats, seek/resume, HLS/DASH/progressive selection, subtitles, buffering/error paths, background controls and media metadata.
- Web/FreeTube storage read/write/error paths, document tree relocation, bridge completion and lifecycle events. The dossier records two confirmed isolated storage defects in the original JS; avoid reproducing them.
- IPTV import, Xtream credentials, live/VOD/series, EPG, favorites and malformed stream handling.
- Radio, podcast, RSS/news, Stremio catalog and unsupported torrent-only item handling.
- Permission denial/grant/revocation for location, media, microphone, projection and accessibility as applicable.
- Android Auto connection, host acceptance, templates, media keys and disconnect/reconnect using a compatible test host.
- Screen mirror consent, rotation, gesture handling, stop and cleanup.
- Billing and license states only with authorized test accounts and backend fixtures: pending, cancellation, expired, offline and successful purchase.
- Optional-module missing/install/failure cases if those modules are recreated.

## Evidence limits and release gate

The original app's prior runtime trace reached its launcher Activity, but the captured UI was the phone lock screen. It does not validate original feature behavior. The new project has no install/run evidence at all yet. Source equivalence cannot be proven without the original buildable source; behavior should be compared with authorized on-device tests and documented outcomes.

Before calling a release feature complete, require: repeatable clean build, install and launch, a completed 19-module matrix, storage migration tests, permission/lifecycle tests, car-host tests where claimed, service fixtures for external/back-end flows, and a review of licensing and third-party assets. Keep the exact original APKs as immutable evidence rather than shipping them inside the new product.
