# Shared playback milestone — 2026-10-12

## Objective retained

Build the full 19-module AA Video Player Car app, with efficient operation and systematic defect prevention. User priority is Android Auto first, a personal/test APK, DHU before a physical car, and working car audio plus a separate parked-video feasibility result. This milestone does not complete that objective or establish a bug-free app.

## Implemented

- Media3 1.11.1 ExoPlayer in one `MediaLibraryService`, with HLS/DASH extensions, audio focus, becoming-noisy handling, wake management and Media3 foreground notification/session support.
- Phone music/video screens connect using `MediaController` and `PlayerView`; navigation releases the controller/view instead of stopping the shared player. The Stop playback action is explicit.
- Room 2.8.5 database `aa-player.db`, schema version 1 exported under `app/schemas/`. Media rows preserve document URI, stable ID, display title, MIME, added time, progress, duration and last-played time. No destructive migration fallback.
- File imports validate readable documents, request durable grants and deduplicate by URI-derived ID. Phone library selection creates a queue; seek/progress state is checkpointed every five seconds while playing and at significant events.
- Media browser audio root, paginated children, title search, media-ID resolution and catalog change notifications. External clients cannot request video entries or replace a catalog item's URI. Session authorization runs in `onConnect`, not `onGetSession`: the latter receives a placeholder identity during legacy binding.
- Android Auto media manifest declarations and legacy browser service interface. This is media integration, not original-style dashboard projection.
- Local Git initialized. Original APKs and analysis outputs remain unchanged and excluded from the source index, along with generated outputs, local tools and signing files. No remote or commit was created.

## Verification

Command from `rebuild/`, with Android Studio JBR configured as `JAVA_HOME`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

PASS: debug build, 3 JVM tests, 9 instrumentation tests on Pixel 9 Pro / Android 15, lint with 0 errors and 20 warnings. No lint baseline hides warnings.

Instrumentation covers all dashboard routes, settings/recreation, audio browse/search/pagination, real generated WAV playback, queue next/seek, progress persistence, two-controller synchronization, activity recreation without playback restart, URI override rejection, media-ID resume, completed-track progress reset, and legacy platform MediaBrowser discovery. Test fixtures are generated locally and their catalog rows are removed during cleanup.

The legacy test initially failed and exposed authorization at the wrong lifecycle callback. Moving validation to the actual connection callback fixed it. Completed-track progress handling also now uses the saved duration when transition callbacks omit duration.

APK: `app/build/outputs/apk/debug/app-debug.apk` (7,173,455 bytes).
SHA-256: `167441f35fa24dbdf28d1bd35699fd6a387de913acacb62c182d55c733cd7786`.
Reports: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, and `app/build/reports/lint-results-debug.html`.

After the test runner cleaned up its APKs, the debug APK was explicitly installed again (`adb install -r`: Success) and launched (`am start -W`: Status ok, COLD, TotalTime 2364 ms, WaitTime 2427 ms). This single emulator sample is a baseline for optimization, not a performance pass or a real-device measurement.

## Car-host evidence and limitations

Google repository package `extras;google;auto`, stable DHU 2.0, downloaded to ignored `.tools/dhu`; archive SHA-1 verified against published repository metadata: `680418d5aca256cce151eb7f9527294e95b6bb8a`.

The emulator package `com.google.android.projection.gearhead` is version `1.2.542030-stub`, code path `/product/priv-app/AndroidAutoStubPrebuilt`. DHU headless mode connected to forwarded TCP port 5277 but produced no verified projection/session handshake. The DHU process was exited. A full Android Auto installation and running head-unit server on a compatible phone/emulator are required before car-host acceptance can be tested. A connected socket is not evidence that the app appears in Android Auto.

Computer Use access to the emulator window was not approved. No visual screenshot/layout check was completed, and alternate screenshot methods were not used to work around that rejection.

## Remaining work, in priority order

1. Full Android Auto/DHU setup and host discovery/control tests; parked-video feasibility remains separate and unproven.
2. Local-library UX: document-tree imports, search/filter, scalable paging beyond the current first 500 rows, deletion/favorites/playlists, cover art, and real video/subtitle/format fixtures. The service also caps a browse response at 500 items; large legacy libraries need a browsable hierarchy.
3. Playback tests for audio focus loss, headset disconnection, notification actions, service/process death, external-client rejection, missing/revoked files, failure recovery, background video policy and real-device media formats. HLS/DASH dependencies are present but network playback is untested.
4. Benchmark cold/warm launch, scrolling, memory, long playback and battery usage. Background database access and bounded progress writes are implemented, but no broad efficiency claim is verified yet.
5. Room maintenance records with lossless legacy-note migration; bounded gallery thumbnails and a real document-tree file manager.
6. Radio, podcasts, IPTV/Xtream/EPG, Stremio, browser storage/downloads, native YouTube, FreeTube bridge, news/TTS, speedometer/settings and mirroring, as listed in `../HANDOFF-FEATURES.md`.
7. UI visual review, resources/localization, accessibility, app icon, remaining lint warnings and full release regression matrix. Billing and unavailable optional packages remain tracked outside the first personal-test release.

Reference APIs: https://developer.android.com/media/media3/session/serve-content and https://developer.android.com/media/media3/session/background-playback .
