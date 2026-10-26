Latest continuation, 2026-10-26: File Manager supports file copy/move inside its user-granted tree, including a bounded-memory streaming copy fallback when the provider lacks native copy. Real provider move/copy and rotation-retained selection were verified on the dedicated AA_Player_Test_API_35 / emulator-5556 VM. The final full run passed 106 device tests (158.825 seconds), 19 JVM tests and builds; lint 0 errors/33 warnings. Final APK SHA-256: df5cd57aeadcc455d803e00ee37a4143e28720c9f15a430cede38d770886c3d5. See rebuild/FILE-MANAGER-MILESTONE.md. Earlier counts below are historical.

Latest continuation, 2026-10-25: File Manager now creates folders inside the consented document tree through a validated, background provider call. Creation was manually verified with Android ExternalStorageProvider on the dedicated AA_Player_Test_API_35 / emulator-5556 VM. The final full suite passed 106 device tests (159.967 seconds); 19 JVM tests and app/test builds pass; lint 0 errors/33 warnings. A test-only News speech latch race was fixed after an intermittent failure. Final app APK SHA-256: d5a5f4d24fd770f4727b8ba4eca921ba99b905437acfd0863493eafa31a6a183. See rebuild/FILE-MANAGER-MILESTONE.md; earlier counts below are historical.

Latest verification, 2026-10-25: File Manager now opens a user-granted document tree, browses nested folders with search and 50-item pages, persists the read grant, and offers provider-supported rename/delete with confirmation. The real Android document provider was exercised on the dedicated AA_Player_Test_API_35 / emulator-5556 VM, including restart, rename and delete. The full device suite passed 106 tests before a plurals-only string change; all 4 focused File Manager tests passed on the final APK. Nineteen JVM tests pass; lint reports 0 errors/33 warnings. Final APK SHA-256: 217e6787fd11683a09d0516ba78e31940019159012c79c8568c43d4c8332a54f. Android Auto DHU is installed, but this VM has only the Android Auto stub and cannot establish a projection session yet. See `rebuild/FILE-MANAGER-MILESTONE.md`. Earlier counts below are historical.

Latest verification, 2026-10-25: Movies & Series restores an open source list by refetching it after activity recreation and restores the player surface by attaching to the shared Media3 item without resetting playback. A paused movie retains its position; closing the restored player returns to the source choices. Saved UI state contains the video identity and play intent, not stream URLs. Nested-dialog teardown now closes children before their activity window detaches, and movie controllers bind with application context to avoid a late unbind crash. A restored player does not replace a different item already playing; it offers an explicit Play selected movie action. The full dedicated-VM suite passes 102 tests (154.897 seconds), including rotation and competing-playback checks; 19 JVM tests pass; builds/lint pass with 0 errors/33 warnings. Final APK SHA-256: ac3ff1dd7935209000fe75c14cb6ed7925221c97785c55a6d59af631548462fd. Only AA_Player_Test_API_35 / emulator-5556 was used. Process-death restoration, offline embedded streams, broader provider/format coverage and other module/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

Latest verification, 2026-10-24: Movies & Series now opens installed add-on catalogs, submits declared search/filter fields, pages 50 titles locally, requests subsequent provider batches and opens title descriptions. Catalog/filter/offset/page history restores through refetch after recreation. The adapter tracks raw rows separately from unique titles for correct skip offsets. Full regression: 92 dedicated-VM tests pass (127.201 seconds), plus 19 JVM tests; builds and lint pass with 0 errors/33 warnings. An additional duplicate-row assertion passes in the 3 adapter tests (0.567 seconds), with no production change after the full run. APK SHA-256: 0166cadf18d208a4b3186f60eb27c540602952cc7c7e14d7d96e6b6a2ea4cc9c. Room remains v11. Only AA_Player_Test_API_35 / emulator-5556 was used. Persistent catalog caching, episode/stream selection, Movies playback and broader provider/visual/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

# AA Video Player Car — project handoff

Latest continuation, 2026-10-21: `rebuild/IPTV-SERIES-MILESTONE.md` adds a bounded, lazy Xtream series/episode adapter and opaque episode stream resolution. It is not yet connected to storage or browsing UI. App/test builds, 17 JVM tests and 65 dedicated-VM tests pass; lint 0 errors/33 warnings. Next implement persistent show/episode membership and paged browsing without deleting existing live/movie channels during per-show refresh. All remaining module and release gates stay open. Earlier milestone counts below are historical.

## Testing device restriction — 2026-10-13

The user explicitly requires a separate testing VM. Never install, test, clear data, restart, or otherwise use their `Pixel_9_Pro` / `emulator-5554`. The dedicated VM is `AA_Player_Test_API_35`, normally `emulator-5556`, with private writable storage under `rebuild/.tools/testing/AA_Player_Test_API_35.avd`. Use `rebuild/Test-AndroidVm.ps1 -RunTests`; it checks the AVD identity before installation/testing. Do not run unfiltered `connectedDebugAndroidTest` with the user's emulator connected. See `rebuild/TESTING-VM.md`. Earlier personal-emulator results below are historical.

Updated 2026-10-21 (America/Los_Angeles).

Latest verified milestone: `rebuild/IPTV-ACCOUNT-GUIDE-MILESTONE.md`, following `rebuild/IPTV-XTREAM-MILESTONE.md`. Xtream live/movie import, encrypted credentials, opaque channel/guide references, refresh, shared-player movie playback and authenticated XMLTV guide fetching are implemented. The guide supports credential replacement, manual-source fallback, recreation and failure cache retention. Room v8 retains tested v1–v7 upgrades. Final build, 17 JVM tests and 62 dedicated-VM tests pass; lint 0 errors/33 warnings. Series, scheduled guide refresh, richer provider/account behavior and real-provider/car-host verification remain. Continuation paragraphs below describe earlier milestones.

Latest verified continuation: `rebuild/IPTV-EPG-MILESTONE.md`. XMLTV URL import, exact tvg-id matching, paged local-time schedules/details, saved-guide refresh and dialog recreation are implemented. The guide defaults to current/upcoming programmes, has a saved Show past option and Now button, displays cache age and updates Now labels every 30 seconds without network/database work. Room v7 retains tested v1–v6 upgrades. Build, 17 JVM tests and 55 dedicated-VM tests pass; lint 0 errors/33 warnings. A live-to-on-demand progress race is also fixed. Provider, larger-guide, automatic-refresh, visual/accessibility and car-host gates remain; following continuation paragraphs are historical.

Latest verified continuation: `rebuild/IPTV-FULLSCREEN-MILESTONE.md`. IPTV fullscreen expands the shared PlayerView, restores after rotation/recreation, and returns to the playlist through Back or Exit fullscreen. Final build, 17 JVM tests and 49 dedicated-VM tests pass; lint 0 errors/33 warnings. Runtime validation is Android 15 only; visual/accessibility, older Android and car-host gates remain. The continuation paragraphs below are earlier milestones.

Latest continuation: `rebuild/IPTV-PLAYBACK-TYPE-MILESTONE.md`. IPTV now supports per-channel Follow playlist / Live / On demand settings, retained across refresh and recreation. Room v6 adds the setting without destructive migration. Build, 17 JVM tests and 48 dedicated-VM tests pass; lint 0 errors/33 warnings. Shared-player fixtures verify forced VOD resume and forced live start policy. The following milestone paragraph predates this continuation.

Latest verified milestone: `rebuild/IPTV-MILESTONE.md`, including local M3U document import. Build, 17 JVM tests and 46 dedicated Android 15 VM tests pass; lint 0 errors/33 warnings. IPTV supports HTTP/document M3U import, paged channels, name/group search, favorites, refresh/removal and shared MP4/HLS playback with separate live/VOD progress behavior. Local refresh preserves favorites/progress and missing documents leave stored channels intact. Actual persisted-grant revocation/reboot and system-picker navigation remain unverified. Room v5 adds IPTV through a non-destructive migration; previous modules remain covered. Xtream/EPG and other IPTV gaps, podcast recovery/adaptive work, the full feature matrix and car-host gates remain open. See the milestone and `rebuild/DEVELOPMENT-PLAN.md` for remaining work.

## Objective and priorities

Latest verification follow-up: the separate download restart probe passes across different app PIDs, preserving completed/paused download state, partial bytes and offline playback progress. Ordinary regression remains 40 device tests and 13 JVM tests passing. Active-transfer crashes, automatic recovery and network/reboot transitions remain unverified; see `rebuild/PODCAST-DOWNLOAD-MILESTONE.md` and the `-RunRestartProbe` option in `rebuild/Test-AndroidVm.ps1`.

Build an independent Android app named **AA Video Player Car**, package `app.aavideoplayercar`, covering the original app's 19 modules with efficient operation and systematic defect prevention. The full goal is still active. Do not describe the prototype as feature complete or bug-free.

The user chose Android Auto first, a personal/test APK, Desktop Head Unit testing first, and working car audio plus a separate parked-video feasibility result. Do not substitute phone-only completion for that objective. A host setup limitation currently blocks car-host validation, while independent development can continue.

## Current implementation

- Typed registry preserves all 19 original IDs/default order. Preferences persist columns, move-to-first ordering and optional last-module startup.
- Browser/YouTube routes use public WebViews with address/search input, history navigation and saved state. Native YouTube extraction and bundled FreeTube are not implemented.
- Media3 1.11.1 shared library service owns ExoPlayer, queues, audio focus and playback notification/session support. Phone views use MediaController/PlayerView. HLS/DASH extensions are included. HTTP WAV station and podcast playback/resume are verified with loopback fixtures; real-station and remote HLS/DASH playback remain unverified.
- Room 2.8.5 database `aa-player.db` has a media library/progress table and structured maintenance records. Schema v5 adds IPTV playlists/channels after v4 podcasts and v3 radio stations; explicit v1-to-v2-to-v3-to-v4-to-v5 migrations preserve prior data. Legacy maintenance notes are imported transactionally once, preserved exactly, with unknown dates/mileage left unknown.
- Maintenance supports add/edit/delete, service and due dates/mileage, date overdue labels, validation, incremental list loading, and draft restoration on recreation. It does not schedule reminders.
- Gallery/file routes remain pickers/previews. Speedometer remains GPS km/h with permission/provider feedback and foreground cleanup.
- Radio Browser search/favorites now play through the shared service; full radio parity remains unfinished. Podcast discovery, URL subscriptions, streaming and progressive downloads/offline playback are implemented; download lifecycle/adaptive gates remain. IPTV M3U core is implemented; Xtream/EPG and other provider/UX gates remain. Stremio, news, mirroring, native YouTube and FreeTube remain unfinished. See `HANDOFF-FEATURES.md` for individual status.

## Architecture and build

Java 17 source, Android Views, AGP 9.1.0, wrapper Gradle 9.3.1, compile SDK 36.1, target 35, minimum 26. JBR 21 from Android Studio is available on this machine. SDK is at `C:/Users/gaura/AppData/Local/Android/Sdk`; ignored local.properties configures this path.

Start in `rebuild/app/src/main/java/app/aavideoplayercar/`. MainActivity hosts routes; ModuleRegistry defines IDs; playback/ owns the shared service/controller UI; data/ owns Room; maintenance/ owns the record editor and pure validation; radio/ owns directory networking and station UI; podcast/ owns feed parsing and subscription UI; network/ contains the bounded document fetcher. Radio permits public HTTP streams; directory requests stay HTTPS and WebView mixed content is explicitly blocked. Keep feature screens separate as they expand. Database/document-provider operations run off the UI thread.

Run from `rebuild/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\Test-AndroidVm.ps1 -RunTests
```

APK is `rebuild/app/build/outputs/apk/debug/app-debug.apk`. Read `HANDOFF-VERIFY.md` and the latest milestone report for exact test scope and hashes. Local Git is initialized; no commit or remote exists. Root APKs, analysis outputs, generated files, machine paths and signing files are excluded from the source index.

## Android Auto evidence and blocker

Manifest declares media support and the MediaLibraryService plus legacy media browser service interface. Emulator tests exercise legacy browser discovery and shared media controls. This does not prove Android Auto host acceptance.

DHU 2.0 is downloaded and checksum-verified under ignored `rebuild/.tools/dhu`. Emulator `emulator-5554` (Pixel 9 Pro / Android 15) contains only `AndroidAutoStubPrebuilt`, version `1.2.542030-stub`. DHU reached the forwarded socket but no projection handshake was verified. Full Android Auto and an enabled head-unit server on a compatible phone/emulator are required. Parked video, original-style projection and physical-car compatibility are unverified.

Computer Use access to the emulator was not approved during the playback milestone. No visual-layout approval or screenshot verification is claimed.

## Reference evidence

- `analysis/REPORT.txt`: reverse-engineering dossier and explicit confidence limits.
- `analysis/dashboard-modules.csv`, `semantic-class-map.csv`: module IDs and recovered class roles.
- `analysis/database-schema.sql`: original Room v13 DDL, reference only; new schema/migrations are independent.
- `analysis/javascript-bridges.csv`, `web-inventory.json`, `web-routes.csv`: FreeTube/native bridge evidence. Its NeDB files are separate from Room.
- `analysis/optional-components.json`: missing VLC/TTS downloadable implementations and model data.
- `rebuild/PLAYBACK-MILESTONE.md`: playback implementation and test history.
- `rebuild/DEVELOPMENT-REVIEW.md`: earlier foundation analysis (historical).

Original APKs are immutable evidence. All four hashes matched their inventory at the playback milestone. Decompiled Java/Smali/JS are reference material, not original buildable source. Some large-output links and analysis scripts reference an earlier `C:/Users/Abuzar/...` environment; check availability before relying on them. No original publisher keys, entitlement services or signing identity are part of the new app.

## Next work

1. Complete Android Auto/DHU setup and host testing when the prerequisite is available; keep audio and parked-video outcomes distinct.
2. Expand local media/file/gallery features: tree imports, thumbnails, scalable paging, playlists/favorites/subtitles and recovery tests.
3. Extend radio/podcast cores with real-provider tests, podcast discovery/downloads, richer metadata and media-browser refresh notifications; then IPTV/Xtream/EPG and Stremio adapters with fixtures and failure tests.
4. Complete browser, native YouTube/FreeTube, news/TTS, settings, speedometer and consent-based mirroring.
5. Validate performance, layouts/accessibility, media formats, storage upgrades, permission/lifecycle failures and physical-car behavior. Track optional VLC/TTS and billing separately from the first personal-test release, without dropping them from the overall feature inventory.
