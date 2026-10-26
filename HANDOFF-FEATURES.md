Latest continuation, 2026-10-26: File Manager adds in-tree file copy/move with a 64 KiB streaming fallback for providers lacking native copy. Real Android provider moves and digest-identical copies, including a rotation-retained selection, were verified on AA_Player_Test_API_35 / emulator-5556. Final full regression: 106 device tests pass (158.825 seconds), 19 JVM tests pass, lint 0 errors/33 warnings. APK SHA-256: df5cd57aeadcc455d803e00ee37a4143e28720c9f15a430cede38d770886c3d5. Other feature and car/release gates remain open.

Latest continuation, 2026-10-25: File Manager adds New folder to the consented tree; the real Android provider created and listed the folder on the dedicated VM. Final full regression: 106 device tests pass (159.967 seconds), 19 JVM tests pass, lint 0 errors/33 warnings. Final app APK SHA-256: d5a5f4d24fd770f4727b8ba4eca921ba99b905437acfd0863493eafa31a6a183. A News speech instrumentation latch race was corrected. See rebuild/FILE-MANAGER-MILESTONE.md. Remaining module and car/release gates are open.

Latest verification, 2026-10-25: File Manager supports user-granted document-tree browsing, nested navigation, in-folder search, 50-item pages, persisted read access and provider-supported rename/delete. Real ExternalStorageProvider consent, restart, rename and delete were checked on AA_Player_Test_API_35 / emulator-5556. The full suite passed 106 device tests before a plurals-only string change; 4 focused File Manager tests passed on the final APK, with 19 JVM tests and lint 0 errors/33 warnings. Final APK SHA-256: 217e6787fd11683a09d0516ba78e31940019159012c79c8568c43d4c8332a54f. See `rebuild/FILE-MANAGER-MILESTONE.md`. Other feature and car/release gates remain open; earlier counts below are historical.

Latest verification, 2026-10-25: Movies & Series restores source choices and the shared player surface after activity recreation, retaining a paused movie's position without saving source URLs in UI state. Child dialog and controller teardown crashes exposed by rotation are fixed. Full dedicated-VM suite passes 102 tests (154.897 seconds); 19 JVM tests pass; builds/lint pass with 0 errors/33 warnings. Final APK SHA-256: ac3ff1dd7935209000fe75c14cb6ed7925221c97785c55a6d59af631548462fd. Only AA_Player_Test_API_35 / emulator-5556 was used. Process-death, broader provider/car/visual/release gates remain open. See the Movies milestone. Earlier counts below are historical.

# Series implementation note — 2026-10-21

The IPTV series network/parser adapter now has fixture coverage, including lazy requests, ordering, limits and opaque stream resolution. Series storage, browsing UI and playback integration remain incomplete; the IPTV row below retains those requirements. See `rebuild/IPTV-SERIES-MILESTONE.md` (65 device tests and 17 JVM tests passing).

# Feature implementation matrix

Status describes the **new** `rebuild/` project, not the original APK. "Prototype" means a basic route exists but original behavior is not reproduced. IDs and original entry points come from `analysis/dashboard-modules.csv`.

| ID | Dashboard entry | Current state | Original behavior / next implementation boundary |
| ---: | --- | --- | --- |
| 16 | YouTube | Prototype: public mobile WebView | Native NewPipe/Innertube discovery, Media3 source selection, subtitles, history and playback. See `ld4.e`, `n30`, `e40`, `r22`. |
| 5 | Web Browser | Prototype: HTTPS address/search, WebView history and saved state | Bookmarks, persistent browsing history, downloads, fullscreen and runtime web testing remain. See `cj0.g`, `i71`. |
| 6 | TV / IPTV | M3U URL/local import, Xtream live/movie/series accounts with encrypted credentials, series browsing/resume and authenticated guides, paging/search/favorites, refresh/removal, shared playback, live/VOD override, fullscreen and XMLTV time navigation/manual fallback | Richer series/category/account controls, provider headers, larger/scheduled/local-file guides and mapping, durable document grants, visual/accessibility and real-provider/car gates remain. See `rebuild/IPTV-ACCOUNT-GUIDE-MILESTONE.md`; original entry points `uw.u`, `nj3`, `kj3`. |
| 18 | Movies & Series | HTTP add-on adapter, encrypted registrations/stream selections, catalog search/filter paging, bounded saved manifest/catalog/metadata fallback, title/season/episode/source UI, shared MP4 playback/resume, bounded same-source HTTP expiry renewal and activity-recreation player/source restoration | Process-death restoration, broader source renewal cases, filter suggestions, artwork and real-provider verification remain. Torrent/YouTube/header-dependent sources are classified as unavailable; external links require user action. See `p10.d`, `jy2`, `g10`. |
| 1 | File Manager | Partial: SAF tree grant, nested browsing, search, 50-item pages, persisted read access, open, new folder, file copy/move within the tree, provider-supported rename/delete | Folder-level copy/move, cross-provider copy, multi-root browsing, thumbnails, richer actions, larger-provider performance and car-host checks remain. See `rebuild/FILE-MANAGER-MILESTONE.md` and `wb4.f`. |
| 2 | Video Player | Partial: Media3 shared player, imported library, queue and saved progress | Video formats, thumbnails, subtitles, document-tree import and full lifecycle tests remain. See `x10.h`. |
| 3 | Photo Gallery | Prototype: one picked image | Gallery navigation, thumbnails and image actions. See `zp` cases 22/5. |
| 9 | Radio | Core: Radio Browser search/country filter, pages, favorites, shared live playback, controller library integration | HTTP fixture playback and persistence/migration/UI tests pass. Real-station/HLS coverage, Radio Garden, artwork and provider renewal remain. See `rebuild/RADIO-MILESTONE.md`. |
| 10 | Music | Partial: Media3 service, imported library, queue, seek/resume and background session | WAV playback/controller tests pass; album browsing, playlists, focus/noisy tests and lyrics remain. See `fy.o`, `kq1`, `il1`. |
| 11 | Speedometer | Prototype: GPS km/h with foreground cleanup and permission/provider feedback | Units, real GPS, accuracy and permission-revocation tests remain. See `pc4.g`. |
| 13 | Podcast | Core: discovery/subscriptions, paged episodes, refresh/unsubscribe, streaming, progressive audio-file downloads and offline playback with progress | Controlled HTTP/Range/cache, phone download UI and offline playback tests pass. Adaptive downloads, process/network lifecycle, remaining download UX, scheduled refresh, artwork and real-provider/car verification remain. See `rebuild/PODCAST-DOWNLOAD-MILESTONE.md`. |
| 17 | News | RSS/Atom source import, bounded cache, search/unread paging, article reader, refresh/removal, read-state/recreation and platform speech controls implemented | Publication-time display and dedicated-VM TTS/focus/active-speech lifecycle checks pass. Source editing, noisy interruption, offline voices and broader provider/visual/accessibility validation remain. See `ly.e`, `w22`, `cd4`. |
| 8 | Settings | Partial: columns, move-to-first/reset order, last-module startup and app settings link | Appearance, language, playback and integration settings remain. See `l00.h`. |
| 12 | Screen Mirror | Unimplemented | User-consented MediaProjection, virtual display, cleanup and optional accessibility gesture forwarding. See `a00.f`, `ScreenMirrorService`, `hs4`. |
| 14 | YouTube Kids | Prototype: public WebView | Original WebView behavior and car/phone integration. See `jd4.d`. |
| 4 | YouTube Web | Prototype: public mobile WebView | Original uses bundled local FreeTube, Vue/Shaka, native bridge and separate file storage. See `zp` case 7, `e41`, `a41`; `analysis/javascript-bridges.csv`. |
| 15 | YouTube Music | Prototype: public WebView | Original WebView plus authenticated Innertube support. See `zp` case 14, `am3`. |
| 19 | YouTube Classic | Prototype: public mobile WebView | Original mobile YouTube WebView integration. See `gd4.c`. |
| 20 | Vehicle Maintenance | Core implemented/tested: structured add/edit/delete, dates/mileage/due values, notes, draft restoration, legacy import | Visual/device QA and large-history performance remain; no scheduled reminders. See `rebuild/MAINTENANCE-MILESTONE.md`. |

## Shared work outside the dashboard

| Area | Current state | Evidence and target |
| --- | --- | --- |
| Dashboard registry | Implemented foundation; JVM and emulator smoke tests pass | Stable IDs/default order; robust `module_order`, `last_module_id`, `open_last_module`, `home_grid_columns` persistence. Full drag/drop parity remains. |
| Media engine/session | Shared Media3 service implemented; WAV, queue, seek, resume, two-controller and legacy-browser tests pass | HLS/DASH configured but network formats, notification controls and interruption tests remain. |
| Android Auto | Media manifest/service foundation; legacy browser protocol passes; host acceptance unverified | DHU installed; emulator has only AndroidAutoStubPrebuilt. Projection, templates and parked video remain unimplemented/unverified. |
| Local data | Room v14 encrypted movie add-on registrations/video selections and progress, movie source identity and bounded catalog cache, News sources/articles, series membership, media/progress, maintenance, radio, podcasts, IPTV policy/guide cache and encrypted Xtream accounts; tested v1–v13 upgrade paths and legacy-note import marker | Independent exported schemas; further feature tables/migrations still required. Original Room v13 is reference only. |
| FreeTube storage/bridge | Absent | Separate NeDB files, document-tree mapping, 30 annotated `a41` bridge methods and native events; see dossier and `javascript-bridges.csv`. |
| Licensing/billing | Absent | Requires a new authorized product/backend policy. Original signer checks and publisher endpoints cannot be reused as proof of entitlement. |
| Optional VLC/TTS | Absent | Dynamic features/model pack not supplied. See `analysis/optional-components.json`; select independently licensed replacements only if needed. |

## Current podcast download continuation — 2026-10-13

Superseded in overall test counts by the IPTV core: 16 JVM and 44 dedicated-VM tests pass, lint 0 errors/33 warnings. See `rebuild/IPTV-MILESTONE.md` for the new module's exact scope.

Completed/explicitly paused downloads now have a separate passing two-process verification for persisted state, partial bytes and offline resume. Normal suite remains 40 passing device tests. Active-transfer interruption and automatic/network/reboot recovery remain distinct open gates.

Lifecycle follow-up: 40 dedicated-VM tests now pass alongside 13 JVM tests and lint 0 errors/32 warnings. Download paging, scroll/dialog restoration across activity recreation and confirmed-action dispatch are fixed and tested. Process death/reboot and network changes remain separate pending gates.

Progressive audio downloads use Media3's persistent index and app-private cache with pause/resume/removal, network preference and storage limits. Offline playback survives publisher URL changes in the controlled fixture. Build, 13 JVM tests and 39 dedicated-VM tests pass, lint 0 errors/32 warnings. See `rebuild/PODCAST-DOWNLOAD-MILESTONE.md` for explicit scope, known UX edge cases and pending lifecycle/adaptive gates.

## Historical podcast discovery continuation — 2026-10-13

Discovery searches Apple Podcasts metadata and subscribes through publisher feed URLs. Invalid searches remain editable; result mode and open dialog drafts restore. Build, 13 JVM tests and 35 tests on the dedicated VM pass, with 0 lint errors/32 warnings. See `rebuild/PODCAST-DISCOVERY-MILESTONE.md`. Downloads/offline storage are the next podcast implementation gap; other module and car-host gates remain open.

## Historical podcast continuation - 2026-10-12

Podcast URL subscriptions and streaming are implemented. The media-browser root includes Podcasts with per-feed episode folders; episode progress survives refresh and controller reload. Build, 13 JVM tests and 28 emulator tests pass; lint 0 errors/31 warnings. See `rebuild/PODCAST-MILESTONE.md`. This remains partial pending discovery, downloads, real-provider/device tests and other listed gates.

## Current radio continuation - 2026-10-12

Radio Browser core is implemented. Music and Radio favorites are separate media-browser folders; search includes favorites. Build, 13 JVM tests and 20 emulator tests pass; lint 0 errors/31 warnings. See `rebuild/RADIO-MILESTONE.md`. Radio remains partial until the listed provider, real-stream, visual and car-host gates are covered.

## Current library continuation - 2026-10-12

Music/video phone lists now use bounded 50-item pages. Queue construction preserves selected items within the service limit; oversized browse/search requests advance consistently using an effective 500-item page. Build, 13 JVM tests and 13 emulator tests pass; lint 0 errors/30 warnings. The 520-item test verifies page 11 playback. Legacy unpaged car browsing, large-library benchmarks, and car-host verification remain open. See `rebuild/LIBRARY-MILESTONE.md` and `rebuild/DEVELOPMENT-PLAN.md`.

## Suggested implementation sequence

Latest maintenance continuation (2026-10-12): build, 8 JVM tests, 12 emulator tests pass; lint 0 errors/23 warnings. Migration, legacy-note preservation and editor validation/recreation/save/edit are covered. See `rebuild/MAINTENANCE-MILESTONE.md`. Full app scope and car-host verification remain open.

Latest 2026-10-12 result: build, 3 JVM tests and 9 emulator instrumentation tests pass; lint 0 errors/20 warnings. See `rebuild/PLAYBACK-MILESTONE.md` for exact scope, APK hash, discovered/fixed defects and car-host limitations. Android Auto remains the user's first priority; the stub-host limitation does not block independent feature work.

2026-10-11 verification: debug build and lint pass (13 warnings), three registry unit tests and two Android 15 emulator tests pass. All 19 route controls open/return; this does not validate network playback or the unfinished integrations. Media picker routes now have their own screen, MIME-based previews and recreation state; full libraries/background sessions remain absent.

1. Buildable baseline, package identity, dashboard IDs, navigation and state restoration.
2. Local features: media library/player, photo browser, file navigation, maintenance database, settings and speedometer.
3. Network adapters: IPTV, radio, podcast, news and Stremio with fixtures, failures and persistence.
4. YouTube native/web paths and bridge contracts, with service and content policy review.
5. Media session, Android Auto host routes and projection/mirroring with device tests.
6. Billing, licensing and optional downloadable modules using newly owned services/assets.
