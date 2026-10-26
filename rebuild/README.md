Latest verification, 2026-10-26: File Manager adds in-tree file copy/move. Real provider move and digest-identical streaming copy passed on the dedicated VM; a pending copy survived rotation. The final full run passed 106 device tests (158.825 seconds), 19 JVM tests and lint 0 errors/33 warnings. APK SHA-256: df5cd57aeadcc455d803e00ee37a4143e28720c9f15a430cede38d770886c3d5. See FILE-MANAGER-MILESTONE.md; older counts below are historical.

Latest verification, 2026-10-25: File Manager now browses a consented Android document tree, with nested navigation, search, paging, persisted access and supported rename/delete. The full dedicated-VM suite passed 106 tests before a plurals-only string edit; the final APK passed 4 focused File Manager tests, 19 JVM tests and lint with 0 errors/33 warnings. SHA-256: 217e6787fd11683a09d0516ba78e31940019159012c79c8568c43d4c8332a54f. See `FILE-MANAGER-MILESTONE.md`; earlier counts below are historical.

Latest verification, 2026-10-25: Movies & Series now supports season/episode paging, embedded or fetched source choices, explicit unsupported-source feedback and direct HTTP(S) playback through the shared Media3 service. Room v12 stores encrypted selected stream URLs and stable video identities/progress, with tested v1-v11 migrations. Full suite: 95 dedicated-VM tests pass (137.654 seconds), 19 JVM tests pass; builds/lint pass with 0 errors/33 warnings. Subsequent source-reselection and stale-dialog callback fixes pass both focused movie UI/service tests (6.728 seconds), including actual source URL rotation. Final APK SHA-256: 36c5acbc5abc40963178f854a78a025dbbc9437a2aae9cac6b7b926435f8b19d. Only AA_Player_Test_API_35 / emulator-5556 was used. Persistent catalog caching, automatic expired-source renewal, player/source dialog restoration, broader lifecycle/format/provider coverage and all other module/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

# Latest continuation: series adapter

See `IPTV-SERIES-MILESTONE.md`: bounded, lazy series/episode parsing and opaque stream resolution are implemented; series storage and browsing UI remain next. Build, 17 JVM tests and 65 dedicated-VM tests pass; lint 0 errors/33 warnings. The full app remains in development. Older counts below are historical.

# AA Video Player Car

Independent Android source project based on the feature inventory in `../analysis/REPORT.txt`. Display name: **AA Video Player Car**. Package: `app.aavideoplayercar`.

For continuation, read `../HANDOFF.md`, `../HANDOFF-FEATURES.md`, and `../HANDOFF-VERIFY.md`.

Device tests must use the dedicated `AA_Player_Test_API_35` VM. The user's `Pixel_9_Pro` must not be used. See [testing VM setup](TESTING-VM.md) and run `./Test-AndroidVm.ps1 -RunTests` after building the app and test APKs. Avoid unfiltered connected-device Gradle tests.

## Current implementation

Latest milestone: [Xtream account guide](IPTV-ACCOUNT-GUIDE-MILESTONE.md), following [live/movie accounts](IPTV-XTREAM-MILESTONE.md). Account import, encrypted credentials, playback and authenticated XMLTV guides with manual fallback are implemented; Room is v8. Build, 17 JVM tests and 62 dedicated-VM tests pass; lint 0 errors/33 warnings. Series, scheduled refresh and real-provider validation remain. The following paragraphs describe older milestones.

Latest verified continuation: [XMLTV programme guide](IPTV-EPG-MILESTONE.md), with URL import, channel matching, paged schedules/details, current/upcoming and past navigation, clock-driven Now labels, refresh and recreation. Room is v7. Build, 17 JVM tests and 55 dedicated-VM tests pass; lint 0 errors/33 warnings. The paragraphs below are previous milestones.

Latest verified continuation: [IPTV fullscreen](IPTV-FULLSCREEN-MILESTONE.md), with rotation/recreation and Back/button exit coverage. Build, 17 JVM tests and 49 dedicated-VM tests pass; lint 0 errors/33 warnings. The following paragraphs record earlier milestones.

Latest continuation: [IPTV playback type](IPTV-PLAYBACK-TYPE-MILESTONE.md). Per-channel Live / On demand / Follow playlist settings survive refresh; Room v6 retains existing data. Build, 17 JVM tests and 48 dedicated-VM tests pass, lint 0 errors/33 warnings. The paragraphs below describe earlier milestones.

Latest milestone: [IPTV M3U core and local documents](IPTV-MILESTONE.md). URL/document import, paged channels, search/favorites, refresh/removal and shared MP4/HLS playback are implemented. Local refresh retains favorites/progress and a missing document leaves stored channels intact. Room is v5 with tested upgrade paths from v1–v4. Build, 17 JVM tests and 46 dedicated-VM tests pass; lint 0 errors/33 warnings. Persisted document grants, Xtream, EPG, provider/visual/car gates remain open. The following paragraphs describe prior milestones.

The separate download restart probe now passes; use `./Test-AndroidVm.ps1 -RunRestartProbe` after building both APKs. Completed/paused state, partial bytes and offline listening progress survive the tested process replacement. The normal 40-device-test suite still passes. Active-transfer crash and automatic/network/reboot recovery remain pending.

Download lifecycle follow-up: 40 dedicated-VM tests and 13 JVM tests pass, lint 0 errors/32 warnings. Download pagination, dialog/page/scroll restoration and confirmed transfers across screen closure are verified. See the updated [download milestone](PODCAST-DOWNLOAD-MILESTONE.md).

Current verification: [Podcast download milestone](PODCAST-DOWNLOAD-MILESTONE.md), 2026-10-13. Progressive audio downloads, pause/resume/removal and offline playback are implemented. Build, 13 JVM tests and 39 dedicated-VM tests pass; lint 0 errors/32 warnings. Download lifecycle, adaptive and UX follow-ups are listed in the milestone. The paragraphs below describe preceding milestones.

Latest verification: [Podcast discovery milestone](PODCAST-DISCOVERY-MILESTONE.md), 2026-10-13. Name/country discovery, subscription dialogs, error correction and search-draft restoration are implemented. Build, 13 JVM tests and 35 dedicated-VM tests pass; lint has 0 errors/32 warnings. The following paragraph describes the preceding core milestone.

Latest verification: [Podcast milestone](PODCAST-MILESTONE.md), following the [Radio milestone](RADIO-MILESTONE.md), following the [Library paging milestone](LIBRARY-MILESTONE.md), following the [Maintenance milestone](MAINTENANCE-MILESTONE.md) and [shared playback milestone](PLAYBACK-MILESTONE.md). See the [development plan](DEVELOPMENT-PLAN.md) for remaining work. Music/video use one Media3 library service with queues and saved progress. Room v4 stores media, structured maintenance, radio stations and podcast subscriptions/episodes, with tested v1/v2/v3 upgrade paths. Podcast URL import, refresh and streaming preserve listening progress. Radio Browser search and saved favorites share the playback service. Android Auto host acceptance remains unverified; the emulator has only a stub installation.

- Dashboard with recovered stable IDs/default order, saved columns, move-to-first ordering and last-module startup.
- Browser and YouTube web views, local video/audio/image picker, speed display, structured maintenance records and settings. YouTube entries use the public website and do not reproduce the original native extractor or bundled FreeTube app.
- Other entries open an explanatory feature page. They are not claimed as implemented.

## Build

Use JDK 17 or newer (validated with Android Studio JBR 21), Android SDK 36.1 and build tools 36.0.0. Set `ANDROID_HOME` or your ignored `local.properties` SDK path. The included wrapper pins Gradle 9.3.1; AGP is 9.1.0.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\Test-AndroidVm.ps1 -RunTests
```

Testing uses the dedicated `AA_Player_Test_API_35` Android 15 VM. APK: `app/build/outputs/apk/debug/app-debug.apk`. See `../HANDOFF-VERIFY.md` for milestone-specific results and remaining work. The script verifies the AVD identity before installing or testing.

## Missing for parity

Native YouTube extraction/player, FreeTube bridge and storage, IPTV/Xtream and EPG, complete radio-provider support, podcast discovery/downloads, news, Stremio add-ons, Android Auto host integration, mirroring, optional VLC/TTS modules, and backend licensing/billing need new implementations and integration tests. The supplied APKs do not contain original source, server code, or downloadable module binaries. The original APK signing certificate and publisher services are not reused.
