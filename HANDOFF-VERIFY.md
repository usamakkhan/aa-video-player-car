Latest continuation, 2026-10-26: The final APK passed 106 dedicated-VM device tests (158.825 seconds), 19 JVM tests and app/test builds; lint reports 0 errors/33 warnings. Real ExternalStorageProvider file move, fallback copy with matching SHA-256 and copy-selection restoration after rotation were checked on AA_Player_Test_API_35 / emulator-5556. Final APK SHA-256: df5cd57aeadcc455d803e00ee37a4143e28720c9f15a430cede38d770886c3d5. The user's Pixel_9_Pro / emulator-5554 was not used. See rebuild/FILE-MANAGER-MILESTONE.md; earlier results below are historical.

Latest continuation, 2026-10-25: After New folder and a test-only News speech latch fix, the final full dedicated-VM suite passed 106 tests (159.967 seconds), 19 JVM tests and builds passed, and lint reported 0 errors/33 warnings. New folder was manually created and listed through the real document provider on AA_Player_Test_API_35 / emulator-5556. Final app APK SHA-256: d5a5f4d24fd770f4727b8ba4eca921ba99b905437acfd0863493eafa31a6a183. Two preceding full runs each had an intermittent unrelated failure, recorded in rebuild/FILE-MANAGER-MILESTONE.md. Only the dedicated VM was used; earlier results below are historical.

Latest verification, 2026-10-25: File Manager full regression passed 106 device tests on dedicated AA_Player_Test_API_35 / emulator-5556 before a plurals-only resource edit. The final APK passed all 4 focused File Manager tests; 19 JVM tests and app/test builds pass, with lint 0 errors/33 warnings. Real document-picker consent, nested browsing, persistence after force-stop, rename and delete were manually exercised on that VM. Final APK SHA-256: 217e6787fd11683a09d0516ba78e31940019159012c79c8568c43d4c8332a54f. The Android Auto DHU binary is installed, but the VM's Android Auto package is a stub; no projection session or host acceptance was verified. See `rebuild/FILE-MANAGER-MILESTONE.md`. Earlier results below are historical.

Latest verification, 2026-10-25: Movies & Series now supports season/episode paging, embedded or fetched source choices, explicit unsupported-source feedback and direct HTTP(S) playback through the shared Media3 service. Room v12 stores encrypted selected stream URLs and stable video identities/progress, with tested v1-v11 migrations. Full suite: 95 dedicated-VM tests pass (137.654 seconds), 19 JVM tests pass; builds/lint pass with 0 errors/33 warnings. Subsequent source-reselection and stale-dialog callback fixes pass both focused movie UI/service tests (6.728 seconds), including actual source URL rotation. Final APK SHA-256: 36c5acbc5abc40963178f854a78a025dbbc9437a2aae9cac6b7b926435f8b19d. Only AA_Player_Test_API_35 / emulator-5556 was used. Persistent catalog caching, automatic expired-source renewal, player/source dialog restoration, broader lifecycle/format/provider coverage and all other module/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

Latest verification, 2026-10-24: Movies & Series now opens installed add-on catalogs, submits declared search/filter fields, pages 50 titles locally, requests subsequent provider batches and opens title descriptions. Catalog/filter/offset/page history restores through refetch after recreation. The adapter tracks raw rows separately from unique titles for correct skip offsets. Full regression: 92 dedicated-VM tests pass (127.201 seconds), plus 19 JVM tests; builds and lint pass with 0 errors/33 warnings. An additional duplicate-row assertion passes in the 3 adapter tests (0.567 seconds), with no production change after the full run. APK SHA-256: 0166cadf18d208a4b3186f60eb27c540602952cc7c7e14d7d96e6b6a2ea4cc9c. Room remains v11. Only AA_Player_Test_API_35 / emulator-5556 was used. Persistent catalog caching, episode/stream selection, Movies playback and broader provider/visual/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

# Current series adapter verification — 2026-10-21

App/test builds, 17 JVM tests and 65 dedicated Android 15 VM tests pass (102.461 seconds); lint 0 errors/33 warnings. APK SHA-256: `f30d90faf6b9add76cef4f7613dfd94f7703927eed7c872fb9f301579fe093f3`. See `rebuild/IPTV-SERIES-MILESTONE.md` for scope. This verifies the series adapter, not series UI/storage or real providers. Testing used only `AA_Player_Test_API_35` / `emulator-5556`. Following results are prior milestones.

# Build and verification handoff

## Dedicated VM requirement — 2026-10-13

All future device testing must use `AA_Player_Test_API_35`, not the user's `Pixel_9_Pro`. Build APKs with `:app:assembleDebug :app:assembleDebugAndroidTest`, then run `rebuild/Test-AndroidVm.ps1 -RunTests` from the repository root. This verifies the AVD name on `emulator-5556` and explicitly targets each device operation. Do not use the historical unfiltered `connectedDebugAndroidTest` commands below when the personal emulator is connected. Setup and current results: `rebuild/TESTING-VM.md`.

## Current result — Xtream account guide, 2026-10-21

Final build, 17 JVM tests and 62 dedicated-VM tests pass (102.850 seconds); lint 0 errors/33 warnings. New coverage verifies authenticated XMLTV gzip import, 51-entry paging/recreation, opaque source persistence with no authenticated URL in the input, credential replacement, redirect refusal, manual-source selection/reopen and cache retention when account credentials cannot be decrypted. Room remains v8. App APK SHA-256: `40e6152ce575dfbd829e6a1cf90f0b2236f1399bb3c82556f2ca85f5111690e2`. See `rebuild/IPTV-ACCOUNT-GUIDE-MILESTONE.md`; real-provider and actual key-loss recovery remain unverified. Personal VM untouched.

## Historical result — Xtream live/movie accounts, 2026-10-21

Final build, 17 JVM tests and 61 dedicated-VM tests pass (96.173 seconds); lint 0 errors/33 warnings. Fixtures verify Keystore encryption/tamper and account binding, URL encoding, opaque channel storage, account authentication/expiry/redirect errors, strict JSON/depth/byte/entry/category bounds, cancellation, phone sign-in/refresh/recreation and MP4 playback at saved progress. Failed login preserves stored channels; playlist deletion cascades account rows. Tested migrations v1–v7 reach Room v8, retaining guide data. App APK SHA-256: `6b395e2a51bd4b2b39a587e20e3cead4d5d70c0ee1f3cf593363dd8d98af050d`. See `rebuild/IPTV-XTREAM-MILESTONE.md` for exact scope and unverified provider/series/live-stream/recovery gates. Personal VM untouched.

## Historical result — guide time navigation, 2026-10-13

Build, 17 JVM tests and 55 dedicated-VM tests pass (94.069 seconds); lint 0 errors/33 warnings. Coverage adds exact programme-end filtering, current/upcoming final-page queries, exclusion of past entries with unknown end time, saved Show past state, Now navigation and an actual clock-boundary label update without interaction. The timer redraws only changed labels, preserves scroll position, performs no network/database work and is cancelled on close. App APK SHA-256: `00870e16d7e3417c2226bd9029e75511be5f146ef776bdf2b39d73394b8ea34d`. See `rebuild/IPTV-EPG-MILESTONE.md`. Only the dedicated VM was used.

## Historical result — IPTV XMLTV guide core, 2026-10-13

Build, 17 JVM tests and 54 dedicated-VM tests pass (61.110 seconds); lint 0 errors/34 warnings. Added bounded XMLTV parsing/time-zone/failure fixtures, 10,000-programme storage/paging, transactional rollback, deletion cascades, v1–v6 upgrades to Room v7, HTTP gzip import and 51-programme phone paging/recreation/failed-refresh cache retention. The initial run exposed acceptance of a truncated root and an IPTV resume failure; fixes require a closed XML root and prevent old live checkpoints from becoming VOD progress after a type change. The strengthened playback regression passes. One 10,000-programme parse/import/query measurement was 846 ms on this VM. App APK SHA-256: `759ca100f8b0f158f37f35c50675442d30f2d71c97f54b42ac635275a226d10b`. See `rebuild/IPTV-EPG-MILESTONE.md` for limits. Personal VM untouched.

## Historical result — IPTV fullscreen, 2026-10-13

Final build, 17 JVM tests and all 49 dedicated-VM tests pass (63.191 seconds); lint 0 errors/33 warnings. Synthetic-video instrumentation verifies expanded player dimensions, hidden/restored controls and status bar, portrait/landscape rotation, activity recreation, shared media identity and active playback, Back exit and button exit. Android 26–29 runtime, visual/accessibility and real-provider/car behavior remain unverified. App APK SHA-256: `40f45e1d038e8b7ead5afd0aafcd5ed7101a2413bcb75c57c9507be386cf986c`. See `rebuild/IPTV-FULLSCREEN-MILESTONE.md`.

## Historical result — IPTV playback type, 2026-10-13

Build, 17 JVM tests and 48 dedicated-VM tests pass (51.007 seconds); lint 0 errors/33 warnings. Room v6 preserves existing data and adds per-channel playback policy. Tests cover the settings dialogs, refresh/recreation persistence, default restoration, invalid modes, v5 migration and earlier upgrades to v6. Shared-player fixtures verify forced on-demand HLS resume at 9 seconds and forced-live MP4 ignoring a requested offset. App APK SHA-256: `5e4aebcb1c1a18620d49fa0fd162fbf217dfc642f78062a52f024c2ad5f27f69`. See `rebuild/IPTV-PLAYBACK-TYPE-MILESTONE.md` for scope and limits.

## Historical result — local IPTV M3U documents, 2026-10-13

Build, 17 JVM tests and all 46 dedicated-VM tests pass (46.125 seconds); lint 0 errors/33 warnings. Added actual ContentResolver document reads, size/cancellation/missing-file guards, picker-result routing, recreation and refresh retaining favorites/progress, with failed refresh retaining stored channels. Tests use temporary MediaStore Downloads documents and inject the result callback; system-picker navigation, persisted grants across reboot and real grant revocation are not established. App APK SHA-256: `1a48478384281965a6932ece24d694f0e3408dab60a1893ebd244a6f858b0f4b`. See `rebuild/IPTV-MILESTONE.md`. First device run found a test selector mismatch, corrected to use the actual string resource before the passing run.

## Historical result — IPTV M3U core, 2026-10-13

Build, 16 JVM tests and all 44 tests on dedicated `AA_Player_Test_API_35` pass (44.368 seconds); lint 0 errors/33 warnings. New coverage includes bounded parsing, 10,000-channel persistence/paging, metadata refresh preserving favorites/progress, cascade deletion, explicit v4-to-v5 migration, earlier migrations reaching v5, HTTP import and phone paging/recreation/filtering, synthetic MP4/HLS video through a display surface, VOD resume/live-start policy and controller URI resolution. App APK SHA-256: `5c9646f6c84b27b5ee9ea58812ceaff61d185751c186cb051988ab71fa8f5467`. See `rebuild/IPTV-MILESTONE.md` for measured performance and verification limits. Real-provider/live-edge/car-host compatibility is not established. Older results below are historical.

## Historical result — podcast audio-file downloads, 2026-10-13

Restart verification follow-up passes in separate PIDs (9949 seed, 10088 verify), including completed/paused state, preserved partial bytes, offline playback at stored progress with the source server closed, and cache removal. `Test-AndroidVm.ps1 -RunRestartProbe` reproduces this dedicated-VM check. The normal 40-test suite also passes through the new test runner in 40.131 seconds, with 13 JVM tests and lint 0 errors/32 warnings. App hash is unchanged from the lifecycle follow-up below; test APK hash is `b3d838ca589910c72f5c30e3a5f53c2ffc77dd2a1a1b98d85222859a70638537`. See the download milestone for limits: this is not an active-write crash or automatic/network/reboot recovery result.

Latest lifecycle follow-up: 13 JVM tests and 40 dedicated-VM tests pass (39.679 seconds), with build success and lint 0 errors/32 warnings. Coverage adds exact 50/51-record pagination, clamping after removal, actual activity recreation with retained download page/scroll, dismissal persistence, and confirmed transfers surviving screen replacement with the repository worker blocked. APK SHA-256: `7d915f882fe15ea477fe831b58dd0276a5678b069f75d18abb992c0a36c07dfe`. The following paragraph describes the prior download-core run.

Build, 13 JVM tests and all 39 instrumentation tests on `AA_Player_Test_API_35` pass (34.217 seconds); lint 0 errors/32 warnings. New evidence covers HTTP Range resume with persisted pause state, exact cached bytes, removal, size/URL/adaptive guards, phone download controls, offline playback after closing the HTTP server and changing the feed URL, and unsubscribe cleanup. This is not process-death/reboot/network-transition or adaptive-download validation. App APK SHA-256: `b60c273d59ef806519bbbafbfafb2739769703959f1a5e2a6bf8ad01928310a4`. See `rebuild/PODCAST-DOWNLOAD-MILESTONE.md` for implementation and open issues. Personal VM untouched.

## Historical result — podcast discovery, 2026-10-13

App/test builds, 13 JVM tests, and lint pass (0 errors/32 warnings). Dedicated `AA_Player_Test_API_35` VM: 35 instrumentation tests pass in 25.661 seconds, including discovery dialogs, HTTP feed subscription/unsubscribe, retry, stale-response suppression and search-draft restoration. No testing used the user's personal VM. App APK SHA-256: `0eb863d5bfebd38a9698c4b29dffc251592ed084595aafb55e70dca84f2d29a1`. See `rebuild/PODCAST-DISCOVERY-MILESTONE.md` for exact scope and remaining gates. Older counts below are historical.

## Historical result - podcast subscription/streaming core, 2026-10-12

Final `assembleDebug`, `testDebugUnitTest`, `lintDebug`, and `connectedDebugAndroidTest` pass. XML reports: 13 JVM tests and 28 Android 15 emulator tests on Pixel_9_Pro, zero failures/errors/skips. Lint: 0 errors/31 warnings. APK SHA-256: `c8a12dcef6c5c9c2e5164919a447d81973316ec9425b75423c750cea95345919`.

New coverage includes RSS/Atom parsing, incomplete XML/entity/URL rejection, stable episode IDs, archived-episode and progress preservation, unsubscribe semantics, v3-to-v4 migration, real loopback HTTP redirects/gzip/failure/size bounds, streamed episode resume and controller URI validation, and the phone subscription/51-episode paging/recreation/refresh flow. Existing v1/v2 migration paths now reach v4. See `rebuild/PODCAST-MILESTONE.md` for implementation and limits. These controlled fixtures do not establish real publisher compatibility, visual approval or Android Auto host acceptance. Older results below are historical.

## Current result - radio core, 2026-10-12

Final `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `connectedDebugAndroidTest` pass. XML reports show 13 JVM tests and 20 Android 15 emulator tests, zero failures/errors/skips. Lint has 0 errors/31 warnings (22 inline text, 1 plural candidate, and the existing dependency/tool/target, JavaScript, exported-service and icon categories). APK SHA-256: `52c152ce61d642fa9d650027dc257406c6bfac7e62646c54fda8da9889f27df1`.

Coverage adds parser rejection/bounds, favorites/reopen/removal, bounded station cache, v2-to-v3 upgrade preserving prior data, radio UI restore/validation/refresh, library folders/search and real HTTP fixture playback without track resume or controller URL override. Existing v1 migration now reaches v3. Both configured directory endpoints returned HTTP 200 to a separate host-side BBC search. This does not establish real-station device playback, remote HLS/DASH, visual approval or car-host acceptance. See `rebuild/RADIO-MILESTONE.md` for exact scope and transport decisions. Prior results below are historical.

## Current result - library paging and queue bounds, 2026-10-12

Final build, 13 JVM tests and 13 Android 15 emulator tests pass with zero failures/errors/skips. Lint has 0 errors and 30 warnings (22 inline-text warnings, plus dependency/tool/target, JavaScript, exported-service and icon warnings). The new 520-item regression covers service browse/search beyond 500, phone page navigation and actual playback from page 11. See `rebuild/LIBRARY-MILESTONE.md` for behavior and remaining limits. APK SHA-256: `d6c5d8f1b2b5db96bb2209b70e3073d4ac7273460cbc1f488d2efa60008b4931`. No car-host or visual-layout verification is claimed; AndroidAutoStubPrebuilt was rechecked and remains the installed host. Earlier totals below are historical.

## Current result — structured maintenance, 2026-10-12

Build, 8 JVM tests and 12 Android 15 emulator tests pass; lint 0 errors/23 warnings. Explicit Room v1-to-v2 migration preserves existing media/progress. Legacy maintenance notes import exactly once; editor validation, draft restoration, save/edit and duplicate prevention pass. Current APK SHA-256: `b362294433cb364c6bf9e857a3644d7996452f62d4891670379bcb78798ef55b`. See `rebuild/MAINTENANCE-MILESTONE.md` for scope/limits. Results below are earlier milestones, not the current totals.

## Latest result — 2026-10-12

The shared Media3/Room milestone passes build, 3 JVM tests and 9 Android 15 emulator instrumentation tests; lint has 0 errors and 20 warnings. See `rebuild/PLAYBACK-MILESTONE.md` for test scope, current APK hash, regression fixes and remaining gates. Android Auto host UI/video are unverified: only AndroidAutoStubPrebuilt is installed. The previous foundation results below are historical. Git was initialized during the latest pass; no commit or remote exists.

## Build prerequisites

The source is at `rebuild/`. The 2026-10-11 development pass found Android Studio, SDK 36.1/build tools 36.0.0, Gradle 9.3.1 and a running Pixel 9 Pro Android 15 emulator. The wrapper is now included and a debug APK was built and installed by the instrumentation test task. The workspace still has no Git metadata.

Use JDK 17+ (validated here with Android Studio JBR 21), Android SDK platform 36.1/build tools 36.0.0, and an Android device or emulator. Configure `ANDROID_HOME` or ignored `local.properties`. From `rebuild/`, run:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\Test-AndroidVm.ps1 -RunTests
```

Build/test tasks were executed using the installed Gradle 9.3.1 distribution (the same version pinned by the generated wrapper). AGP is 9.1.0, compile SDK 36.1, target SDK 35, minimum SDK 26. Initial offline resolution failed due to missing cached Kotlin dependencies; the normal network build succeeded. ADB and Gradle needed execution outside the sandbox for native/cache access.

## Verified result — 2026-10-11

- `:app:assembleDebug`: PASS. APK at `rebuild/app/build/outputs/apk/debug/app-debug.apk`.
- APK SHA-256: `34193b3f741fbc29b11bfa180655621b269cf2ce45d09f000cde64203a58499e`.
- `:app:testDebugUnitTest`: PASS, 3 tests, zero failures/errors/skips. Tests cover original IDs/order, corrupted preferences and order round trips.
- `:app:lintDebug`: PASS, 0 errors, 13 warnings. Remaining warning categories: older target/tool version, JavaScript enabled, missing app icon, and inline English text. No lint baseline or suppressions were added.
- `:app:connectedDebugAndroidTest`: PASS, 2 tests, zero failures/errors/skips, `Pixel_9_pro(AVD) - 15`, serial `emulator-5554`. Installs and launches the new app, opens all 19 routes and returns home, checks saved reorder/column values, recreates a media-picker screen and checks Back returns home.
- JVM HTML report: `rebuild/app/build/reports/tests/testDebugUnitTest/index.html`.
- Emulator HTML report: `rebuild/app/build/reports/androidTests/connected/debug/index.html`.
- Emulator XML and per-test logcat: `rebuild/app/build/outputs/androidTest-results/connected/debug/`.
- Lint report: `rebuild/app/build/reports/lint-results-debug.html`.
- Recomputed all four original APK hashes: match `analysis/apk-inventory.json`.

Tests exercise view actions programmatically, not touch geometry or gesture navigation. No screenshot review, actual media playback, WebView network success, location signal, physical phone or car-host tests were completed. Instrumentation setup resets only the new app's dashboard preferences on the test device.

## Checks already completed

- New package name agrees across Java, Gradle and manifest resolution; display name is in `strings.xml`.
- Manifest and strings XML parse successfully.
- MainActivity braces are balanced.
- Original APKs were not modified by the rebuild work. The earlier reverse engineering dossier reports archive extraction, hash and signature checks in `analysis/verification.json` and `analysis/signature-verification.json`.

The earlier structural checks alone did not establish compilation/runtime correctness. The new build and emulator tests above now provide limited runtime evidence; they do not establish feature parity.

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

The original app's prior runtime trace reached its launcher Activity, but the captured UI was the phone lock screen. It does not validate original feature behavior. The new project now has emulator install/route evidence, with the limits listed above. Source equivalence cannot be proven without the original buildable source; behavior should be compared with authorized on-device tests and documented outcomes.

Before calling a release feature complete, require: repeatable clean build, install and launch, a completed 19-module matrix, storage migration tests, permission/lifecycle tests, car-host tests where claimed, service fixtures for external/back-end flows, and a review of licensing and third-party assets. Keep the exact original APKs as immutable evidence rather than shipping them inside the new product.
