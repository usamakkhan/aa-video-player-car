Latest progress, 2026-10-26: File Manager now copies and moves ordinary files within the selected document tree, with provider-side moves, provider-side copy where available and a streaming fallback. The final full dedicated-VM suite passed 106 tests; 19 JVM tests and lint 0 errors/33 warnings pass. See FILE-MANAGER-MILESTONE.md. Folder actions, multi-root browsing, Android Auto host tests and the remaining roadmap below are still required.

Latest progress, 2026-10-25: The File Manager slice now has document-tree consent, nested browsing, search/paging, persisted read grants and provider-supported rename/delete. The final APK passed 4 focused VM tests after a 106-test full run, 19 JVM tests and lint 0 errors/33 warnings. See `FILE-MANAGER-MILESTONE.md`. Next priorities are completing File Manager actions and broader module parity, then Android Auto host testing once a full signed-in Android Auto app is available on the dedicated VM. The full roadmap below remains open; older progress notes are historical.

Latest verification, 2026-10-25: Movies & Series now supports season/episode paging, embedded or fetched source choices, explicit unsupported-source feedback and direct HTTP(S) playback through the shared Media3 service. Room v12 stores encrypted selected stream URLs and stable video identities/progress, with tested v1-v11 migrations. Full suite: 95 dedicated-VM tests pass (137.654 seconds), 19 JVM tests pass; builds/lint pass with 0 errors/33 warnings. Subsequent source-reselection and stale-dialog callback fixes pass both focused movie UI/service tests (6.728 seconds), including actual source URL rotation. Final APK SHA-256: 36c5acbc5abc40963178f854a78a025dbbc9437a2aae9cac6b7b926435f8b19d. Only AA_Player_Test_API_35 / emulator-5556 was used. Persistent catalog caching, automatic expired-source renewal, player/source dialog restoration, broader lifecycle/format/provider coverage and all other module/car/release gates remain open. See the Movies milestone. Earlier counts below are historical.

# Development plan — AA Video Player Car

Latest continuation, 2026-10-21: `IPTV-SERIES-MILESTONE.md` provides tested lazy series discovery and episode parsing/resolution (65 dedicated-VM tests, 17 JVM tests). Series storage and user-facing navigation remain unfinished. Implement show/episode membership with migrations, transactional refresh, paged show/season navigation, restoration and shared playback next. Preserve series-only accounts and existing live/movie data. The full roadmap below remains required.

Latest progress, 2026-10-21: `IPTV-ACCOUNT-GUIDE-MILESTONE.md` adds authenticated XMLTV fetching and manual fallback to the encrypted Xtream live/movie account core. Room v8, 17 JVM tests and 62 dedicated-VM tests pass. Next Xtream work includes series/seasons/episodes, richer account/category controls, scheduled refresh and provider validation. The full roadmap and other module/release gates below remain required.

Latest progress: `IPTV-EPG-MILESTONE.md` adds the XMLTV programme-guide core and fixes an IPTV playback-policy race. Room v7 and 54 dedicated-VM tests pass, with 17 JVM tests. Next IPTV work includes Xtream and provider integration plus the guide limitations recorded in that milestone. All other module and release gates below remain in scope.

Progress as of 2026-10-13: shared paging, radio, podcast discovery/downloads, IPTV URL/local M3U import, per-channel playback classification and fullscreen have implemented slices with automated verification. Current evidence is `IPTV-FULLSCREEN-MILESTONE.md`: 17 JVM tests and 49 dedicated-VM tests pass; Room v6. Milestones below remain the full roadmap, not completed checkboxes. Next IPTV work is EPG/XMLTV and Xtream/provider integration; Android Auto host, other unfinished modules, visual/accessibility, physical-device and release performance gates remain required.

Prepared 2026-10-12 from the current handoffs, feature matrix, recovery dossier, source inventory, and targeted source review. This is a development roadmap, not a claim that every decompiled dependency or binary has been audited. No application code was changed or tests rerun for this planning pass.

## Baseline and scope

Continue the independent Java/Android Views app in `rebuild/`, retaining Media3, Room, stable module IDs, and existing user data. Original APKs and extracted assets remain reference evidence. Avoid a framework rewrite unless a measured limitation justifies it.

The latest recorded verification is 8 JVM tests, 12 Android 15 emulator tests, and lint with 0 errors/23 warnings. These are prior milestone results, not new verification. Shared playback and structured maintenance exist; many other routes are prototypes or placeholders. Historical sections of the handoffs contain superseded statements and must not override the newest milestone.

Preserve all 19 modules. Priorities recorded in the handoff are Android Auto first, a personal/test APK, Desktop Head Unit before physical-car testing, and separate outcomes for car audio and parked-video feasibility. Phone playback does not prove car compatibility.

## Ordered milestones

| Order | Work and deliverables | Acceptance gate |
| --- | --- | --- |
| 1 | Reproduce the baseline; reconcile current versus historical handoff statements; establish a source revision and repeatable build/test commands. Record each module's requirements and test cases. | Clean build, existing tests pass, baseline reports and APK tied to the same source revision. |
| 2 | Resolve the Android Auto host prerequisite and test audio discovery, browsing, search, playback controls, notification controls, and disconnect/reconnect. Investigate parked-video capability separately against current platform support. | Actual DHU session evidence for audio; explicit supported/unsupported/blocked result for parked video. No compatibility claim from socket connection alone. |
| 3 | Strengthen shared architecture and playback: consistent queue limits and pagination; live versus resumable media model; separate screen lifecycle, data repositories, network clients and controller code as features expand. Add bounded background work and cancellation. | Regression coverage for large libraries, queue boundaries, audio focus, becoming noisy, permission loss, recreation and process restart; no lost stored progress. |
| 4 | Complete local media: directory-tree imports and refresh, file navigation/actions, paged libraries, thumbnails, image browsing, music albums/playlists/favorites, video subtitles and format handling. | Large-library fixture remains responsive; revoked grants and missing files recover clearly; supported formats, subtitles, rotation and background controls verified. File mutations handle provider failure without reporting false success. |
| 5 | Implement radio using a provider adapter, search/filtering, favorites, station metadata and shared live-stream playback. Add audio library categories for car browsing. | Parser and failure fixtures, persistence/migration tests, real stream smoke tests and phone controls pass; no invalid resume position applied to live radio. Repeat host verification for new categories. |
| 6 | Implement podcasts: discovery adapter, RSS import/subscriptions, episodes, refresh, progress, queue and resumable downloads with storage management. | Malformed/duplicate feeds, interrupted downloads, offline playback, cancellation and episode identity tests pass. |
| 7 | Implement IPTV: M3U first, then Xtream, live/VOD/series categories, favorites, EPG, progress and HLS/DASH playback. Keep provider credentials out of logs. | Bounded parsing on large playlists, time-zone/EPG fixtures, expired credentials, unavailable streams and playback tests pass. |
| 8 | Implement Movies & Series with Stremio-compatible catalog, metadata and stream adapters, search and bounded caching. Define treatment of unsupported torrent-only sources before adding any further engine. | Adapter fixtures and real supported-source checks pass; unsupported sources produce clear outcomes. |
| 9 | Complete Browser and the five YouTube-related modules: bookmarks/history/downloads/fullscreen, distinct Kids/Music/Classic behavior, native YouTube discovery/playback, then FreeTube-compatible storage and bridge work. Verify current integration contracts before selecting dependencies. | Each route meets its own requirement checklist; authentication, navigation, lifecycle and playback failures covered. Bridge origin/input validation and storage-error tests pass before enabling a native bridge. |
| 10 | Complete News with RSS sources, reading state, article display and platform TTS; assess optional offline voices separately. | Feed parsing, speech interruption, lifecycle, missing-engine and offline cases pass. |
| 11 | Complete Settings, Speedometer and Maintenance polish: appearance/language/playback preferences, dashboard reorder, units/accuracy/status, maintenance history usability. Clarify reminders/export as extensions rather than silently claiming them implemented. | Preferences survive upgrades/restarts; location denial/revocation and real-device GPS pass; maintenance data/drafts remain intact. |
| 12 | Implement consent-based screen mirroring with MediaProjection, rotation, stop controls and complete resource cleanup. Assess any gesture-forwarding capability separately. | Consent rejection/revocation, lock/background behavior and repeated start/stop pass on device. Car projection remains a separate host compatibility gate. |
| 13 | Finish adaptive visual design, accessibility, localization and performance validation across all modules; run physical-phone and physical-car release checks. | All applicable module gates pass, measured budgets are met, and unresolved defects/unsupported capabilities are documented. Produce a versioned personal-test release. |

Milestone 2 is the first integration priority. Its full-host prerequisite may remain blocked by the environment; milestones 3 onward can proceed without treating the car milestone as complete. Recheck car browsing/playback after each new audio source. Do not postpone all car testing until the final release.

Visual consistency and accessible controls are part of every feature milestone; milestone 13 is the final cross-app review, not the first UI review.

## First implementation batch

1. Run the existing build, unit tests, lint and available emulator tests. Capture the actual baseline and distinguish historical documentation.
2. Recheck full Android Auto/head-unit-server availability. If still unavailable, record the exact prerequisite and continue independent implementation.
3. Fix the queue boundary: a 500-item phone list plus a newly selected item can currently produce 501 entries while the service rejects queues larger than 500. Add a targeted regression test.
4. Define consistent page-size behavior. The current service caps returned pages at 500 while computing offsets using the caller's requested page size; cover requests above the cap and ensure items are not silently skipped.
5. Replace the phone library's fixed first-500 view with incremental loading and a bounded queue policy. Preserve selected-item identity and progress.
6. Add interruption and revoked-document-access tests before expanding playback sources.
7. Define persisted source kind, playable content type and live/resume behavior independently of MIME type. This is necessary for radio and podcast streams without misclassifying HLS as video or local music.
8. Implement the first radio slice end to end: search, save a favorite, play through the existing service, stop, restart and recover favorites. Verify the chosen provider's current API before implementation.

## Architecture rules

- One Media3 player/service owns playback. Phone and car clients control it; individual feature screens do not create competing players.
- MainActivity coordinates navigation; expanding features own their UI state, lifecycle and repository contracts.
- Separate provider response models from persisted entities. Use stable media IDs and resolve them through the repository rather than trusting arbitrary controller URIs.
- Keep database/document/network work off the main thread. Separate network work from serialized database operations so a slow provider does not stall local progress saves.
- Bound response sizes, parsing work, caches, queues and retries. Cancel stale searches and discard callbacks for closed screens.
- Export every Room schema and add explicit migration tests from existing supported versions. Never use destructive migration to simplify development.
- Reuse shared loading, empty, offline, permission-denied and retry states, while retaining source-specific error explanations.

## Validation and efficiency

For every milestone, add tests for meaningful behavior and failure paths, then run the affected checks and the established regression suite. Use recorded network fixtures for repeatability and separate live-service smoke checks. A successful build does not establish a working provider or a correct UI.

Measure release-build startup, frame rendering, memory, library-query latency, stream startup, battery use and APK size on a named reference device. Establish measured baselines first. Proposed initial budgets are local UI response within 100 ms at the 95th percentile and less than 10% regression in repeatable startup/query benchmarks; revise with recorded device evidence rather than presenting these as achieved results. Network-dependent timings must record connection and server conditions.

Use at least a 10,000-item library fixture and repeated screen/open-close and playback cycles to expose unbounded memory growth. Verify large images with sampled decoding instead of directly loading full-resolution images on the UI thread. Add visual review for small screens, landscape, large text, contrast and touch targets when authorized UI access is available; existing instrumentation is not visual approval.

Before each deliverable, update the feature matrix, verification report and main handoff with exact implemented scope, test evidence, APK identity, limitations and next work. Feature status should distinguish implementation, automated verification, device verification and car-host verification.

## Dependencies and release boundaries

- A full Android Auto installation with a working head-unit server is required for the currently planned DHU gate. The last handoff reports only a stub on the emulator.
- Current provider contracts, terms and library licenses need verification when implementing radio, podcast search, IPTV, Stremio and YouTube adapters; the decompiled reference is not evidence of current API availability.
- Original optional VLC/TTS binaries are absent. Assess codec/voice requirements, then select independently available components and test missing/download/install/failure states if needed.
- Billing/licensing remains tracked outside the first personal-test APK. A commercial release requires a separately defined owned product/backend, authorized test accounts and entitlement tests; original publisher identity/endpoints cannot substitute for this.
- No schedule is promised before the host and provider feasibility checks. Size work after each milestone's acceptance criteria and external dependencies are resolved.

The full app is complete only when all 19 module requirements have explicit outcomes and all applicable release gates pass. Unsupported or blocked capabilities remain visible; placeholders, passing smoke tests and a generated APK do not close them.
