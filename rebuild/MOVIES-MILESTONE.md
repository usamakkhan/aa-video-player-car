# Movies & Series — add-on adapter foundation, 2026-10-23

## Source and player restoration — 2026-10-25

`TitleDialog` saves an open source list's provider video ID, season/page and the player's local media ID/play intent. Source options are refetched after activity recreation; no stream URL enters saved UI state. If the player was open, `MoviePlayerDialog` attaches to the existing shared item without preparing or seeking it again. If no item remains, it resolves the saved movie identity and restores the prior play/paused intent. If another item is active, it leaves that item alone and shows an explicit Play selected movie action. Closing a restored player reopens its source list. Parent dialogs now dispose child windows synchronously before activity teardown, and the movie controller uses application context to avoid a late service-unbind crash.

The dedicated VM's full 102-test suite passes (154.897 seconds), including source-list recreation, paused movie position, restored player dismissal and an unrelated active playback item. Builds, 19 JVM tests and lint pass with 0 errors/33 warnings. Final APK SHA-256: `ac3ff1dd7935209000fe75c14cb6ed7925221c97785c55a6d59af631548462fd`. Only AA_Player_Test_API_35 / emulator-5556 was used. Process-death behavior and real-provider/format coverage remain unverified.

## Selected-source renewal — 2026-10-25

Room v14 adds provider video type/ID and a digest of the selected source's name/description plus its provider index. The explicit v13-to-v14 migration preserves older saved videos and their progress; those records acquire renewal identity when the user reselects a source. The URL remains encrypted. The playback service recognizes HTTP 401, 403, 404 and 410 on a selected movie item, makes one bounded network renewal attempt, and checks the configured add-on still identifies as the saved provider. A matching direct stream with a changed URL replaces the stored source while retaining the same movie identity and resume position. If matching is ambiguous or unavailable, the player keeps its error so the user can choose a source explicitly. Other network, format and DRM errors do not trigger renewal.

The dedicated Android VM passed the full 100-device-test suite (146.5 seconds), including a real 403 response followed by provider URL rotation, MP4 rendering and position recovery. A v13 migration fixture preserves the movie cache and old video progress. All 19 JVM tests pass; build and lint pass with 0 errors/33 warnings. Final APK SHA-256: `81b56a2682bf21254558770be56af913c93c93fbef4e9329e94fa135f8deedf7`. Testing used only AA_Player_Test_API_35 / emulator-5556. Source/player dialog restoration, broader renewal and real-provider validation remain open.

## Bounded offline browse cache — 2026-10-25

Room v13 adds a `movie_cache` table with an explicit v12-to-v13 migration and add-on cascade deletion. The repository worker saves normalized manifest capabilities, catalog batches and detailed metadata after successful requests. A failed request reads the matching snapshot and the catalog status labels it as a saved copy. Keys include the local add-on identity, a digest of its configured endpoint, manifest version for catalog/metadata, catalog identity, filters and offset. Raw configured URLs and filter text are absent from cache keys. Snapshots omit streams and source URLs; offline browsing does not imply offline playback.

The cache is bounded to 32 entries, 8 MiB total and 1 MiB per entry; oversized results remain available in memory from the network but are not persisted. Add-on removal cascades to its cache. Provider failure, catalog reopen and metadata fallback pass on the dedicated VM. The full suite passes 96 device tests (141.45 seconds), 19 JVM tests, and lint with 0 errors/33 warnings. The final manifest-version key and explicit v12 migration assertion pass two focused device tests (4.83 seconds); cache pruning, size rejection and cascade deletion pass a third focused test (0.335 seconds). Final APK SHA-256: `83e3c8ed7078c7ff1c98443e2d9e9842655aebaf6c004265bb864d55976339ec`. Only AA_Player_Test_API_35 / emulator-5556 was used. Automatic signed-source renewal, source/player restoration, offline streams and real-provider validation remain open.

## Episode/source selection and shared playback — 2026-10-25

Verification: full suite 95 tests passed (137.654 seconds), JVM suite 19 passed. Final source-reselection/stale-dialog changes and the URL-rotation assertion subsequently passed both focused movie UI/service tests (6.728 seconds). Builds/lint pass, 0 errors/33 warnings. APK 36c5acbc5abc40963178f854a78a025dbbc9437a2aae9cac6b7b926435f8b19d. Testing used only AA_Player_Test_API_35 / emulator-5556. The full run preceded the final focused changes; .tools/testing/movie-playback-final.txt records their results.

`TitleDialog` displays season selection (including specials/season zero), pages episodes in groups of 50, and opens provider stream choices. Embedded streams are honored, including an explicitly empty list; absent embedded streams use the declared stream resource. Torrent, YouTube and header-dependent/unsupported sources have explicit unavailable labels. External website links launch only on selection. Title/season/page identities are saved for catalog restoration; player/source dialog restoration and broader lifecycle verification remain open.

Selecting a direct HTTP(S) source now opens `MoviePlayerDialog`, a full-screen Media3 PlayerView controlling the existing PlaybackService. Each explicit selection resolves the current stored stream, including refreshed URLs for the same video identity. A closed or replaced source dialog cannot open a late player callback. The service controls playback and notification/session state; closing the view releases its controller without stopping shared playback. Select a source again to reopen the player. Background/rotation UX needs further work.

Room v12 adds `movie_videos` with an explicit v11-to-v12 migration and cascading add-on ownership. Video identity is derived from local add-on ID, type and provider video ID; it does not depend on a selected stream URL or quality. The selected URL is encrypted with a video-specific authenticated scope. Source replacement preserves stored progress; the shared service resolves stored IDs, ignores supplied controller URLs, saves progress and resets completed progress. Movie videos are excluded from audio browse/search, and resolution requires a local controller. There is a 5,000-video storage limit. This is stored selection/progress data, not the still-pending catalog/metadata cache.

Stream discovery happens before storage on the network executor; playback resolves the latest selected encrypted URL on the repository worker without a network lookup. Expired URLs require reopening sources and selecting again. Automatic signed-URL renewal, cross-add-on resolution, torrent engines, custom headers, subtitles, richer playback controls and real-provider validation remain open. No universal codec/DRM support is claimed.

New tests cover season zero, 51-episode paging, empty embedded streams, unsupported torrent feedback and MP4 playback through the actual source/player UI. A service fixture covers MP4 dimensions, seeking, progress, source URL rotation, identity retention and metadata without plaintext URLs. A v11 migration fixture preserves the encrypted add-on record. The initial service fixture omitted a video surface and timed out waiting for video dimensions; it now attaches a PlayerView as the existing IPTV test does. Final verification is recorded in the current handoff.

## Catalog browser — 2026-10-24

Verification: builds, 19 JVM tests and 92 dedicated-VM tests pass (127.201 seconds); lint 0 errors/33 warnings. An additional raw-row-versus-unique-row assertion subsequently passes with all 3 adapter tests (0.567 seconds). Production APK remained unchanged after the full run: 0166cadf18d208a4b3186f60eb27c540602952cc7c7e14d7d96e6b6a2ea4cc9c. Room v11. Only AA_Player_Test_API_35 / emulator-5556 was used. No real-provider or visual approval is claimed.

Browse selected opens `CatalogDialog`, fetching the saved add-on manifest using its decrypted URL on workers. The full-screen dialog lists movie/series catalogs, offers declared extras as search/filter text fields with required-field validation, loads bounded provider results, and displays 50 titles per local page. Next requests a further batch only when the current batch ends and the provider declares `skip`. Previous returns within the current batch or fetches a prior offset. Offset history is bounded to 1,000 entries; only the current batch of up to 2,000 unique titles is retained in memory.

The adapter now returns the number of provider rows consumed separately from deduplicated title rows, so repeated IDs do not incorrectly reduce the next server offset. Selecting a title fetches matching metadata where supported and shows a selectable, scrollable description; providers without metadata use the catalog preview. Network errors re-enable navigation, with the current loaded batch retained for refresh/detail failures. A new filter submission clears the previous query's results before fetching, avoiding mislabeled stale results.

The installed add-on, catalog identity/type, submitted filters, remote offset, local page and prior offset history restore after recreation. Restoration refetches manifest and results; it is not offline caching. Detail-dialog state and unsubmitted filter drafts are not yet restored. Closing cancels active requests and ignores stale callbacks. Filter option suggestions, episode/stream selection, durable bounded caching, artwork, real providers and visual/accessibility review remain unfinished.

The new phone fixture exercises 51 results, local paging, recreation on page two, a subsequent provider batch, return to the prior batch, search, search restoration and full title-description retrieval. Earlier sections below describe previous stages.

## Phone add-on management — continuation

Verification: 91 dedicated-VM tests pass in 117.769 seconds. A subsequent resource-only count-label correction removes a new plural warning; final builds/lint pass with 0 errors/33 warnings, and all 3 Movies/dashboard tests pass again in 6.325 seconds. JVM suite: 19 passing tests. Final APK: 2681a69f14213cfdddae3f598e27953d8ea7004eece96206f274a8c6531b6730. Only AA_Player_Test_API_35 / emulator-5556 was used. The full 91-test run preceded the wording-only change; final UI output is in .tools/testing/movies-final-ui.txt.

Dashboard ID 18 now opens `MoviesScreen` instead of the unavailable screen. Users can install a manifest URL, select a saved add-on, refresh its metadata, replace its configured URL while preserving its local identity, and confirm removal. Successful installation clears the input. The selected registration survives recreation; configured URL drafts are masked, excluded from autofill and deliberately not placed in saved-instance state. Installed URLs remain encrypted in Room.

Network work runs on a bounded executor with cancellation and stale-result checks; registration/database work uses the repository worker. Failed requests retain stored registrations and re-enable management. The screen explicitly states that catalog browsing and playback are still in development. A phone fixture covers install, recreation, successful/failed refresh, URL replacement and removal. No catalog cache, title browsing, artwork, stream selection or Movies playback is claimed in this stage.

Next connect catalog discovery and search/pagination, bounded cached title/episode details and shared-player stream selection. The earlier registration/adapter-only sections below are historical.

## Persistent registrations — continuation

Final builds, 19 JVM tests and 90 dedicated-VM tests pass (114.937 seconds); lint 0 errors/33 warnings. APK SHA-256: 211362f44c71bf753d219f552e2a0fab8f67544b00938e8aa2f5565e3ec412bc. Only AA_Player_Test_API_35 / emulator-5556 was used. All v1-v10 upgrade fixtures pass. An initial test compilation failure from using RoomDatabase as AutoCloseable was corrected to explicit finally/close before this successful run.

Room v11 adds `movie_addons` through an explicit v10-to-v11 migration. `MovieAddonDao` stores a maximum of 50 registrations with bounded labels/provider IDs/version fields and transactional updates. A refresh of a deleted ID returns without recreating it. The adapter and registration layer are still not connected to the dashboard; catalog/metadata caches and phone UI remain next.

`AddonRegistration` stores configured manifest URLs using the existing Android Keystore AES-GCM helper, with a Movies-specific authenticated-data prefix plus the random local registration ID. Ciphertext cannot be copied to a different registration. The shared key remains device-bound; loss of the key requires re-entry of the configured URL. Provider ID, display name, version and update time are stored as non-secret metadata. Manifest URLs and their configured path tokens are never used as local IDs or display labels.

Registration operations must run on the serialized repository worker after a manifest fetch completes on a network worker. Adding the same normalized URL reuses its registration; explicitly replacing a URL retains the local ID. Network/manifest failures occur before persistence, preserving the old registration. Database reopen, duplicate registration, URL rotation, swapped-ciphertext rejection, deletion/late refresh and limits have dedicated fixtures. A v10 migration fixture preserves a read News article, alongside existing prior-version migration fixtures.

This adds registration storage only. Catalog/metadata caching, key-loss UI, manifest configuration requirements, add-on management screens and player integration remain unfinished. Older scope statements and verification counts below describe the preceding adapter-only stage.

`movies/AddonClient.java` implements the HTTP Stremio manifest/catalog/meta/stream protocol foundation. It is not yet connected to dashboard ID 18, persistent storage or playback. This is implementation progress toward the existing Movies & Series requirement, not a complete module.

The adapter retains configured manifest path prefixes, encodes resource identifiers and catalog extra values, validates declared required/supported filters, and applies resource-specific type/ID-prefix rules. Catalog ID prefixes are intentionally exempt from resource matching. Catalogs support caller-supplied search, skip and other declared extras; UI controls for provider option lists still need implementation. Metadata retains episode identities, season zero and the distinction between absent embedded streams (request them) and an explicitly empty stream list.

Results distinguish direct HTTP(S), external-browser, torrent, YouTube and unsupported sources. Torrent playback has no engine and must display an explicit unavailable outcome. YouTube resolution and custom-header streams are not implemented here. Archive/Usenet and other source forms remain unsupported. External URLs must only open on user action. HTTP classification establishes a valid transport URL, not codec, DRM or playback compatibility. Artwork is retained as a validated URL but never fetched by this adapter.

Requests reuse the cancellable 4 MiB/30-second document fetcher. Redirects are rejected because configured paths may carry credentials. Error messages do not include endpoints, provider payloads or underlying exceptions. JSON must be strict, single-root, at most 32 levels deep and have no duplicate object fields. Limits include 100 catalogs/resources, 2,000 catalog results, 10,000 metadata videos and 500 streams. Invalid responses fail without a partial result; duplicate title/episode IDs retain the first entry. No unbounded cache or automatic retry is introduced.

## Next implementation steps

1. Persist add-on registrations and bounded catalog/metadata caches with explicit Room migrations. Protect configured URLs as secrets; never expose them through car browse metadata or logs. Define refresh, removal and transactional failure retention.
2. Replace the unavailable dashboard route with add-on management, catalog selection, declared filters, paged results, title/season/episode details and stream selection. Preserve navigation across recreation and explain unsupported source outcomes in resources.
3. Resolve selected streams through stored media identities and the shared playback service, retaining title/episode progress without trusting arbitrary controller URLs. Handle changing signed stream URLs by resolving again when needed. Keep video out of car audio browsing.
4. Verify a supported real add-on, controlled video playback, error/offline states, lifecycle, cache bounds, accessibility and performance. Add-on configuration pages, richer metadata/artwork, subtitles, cross-add-on fallback and provider options remain part of later work.

## Protocol references

- [Official Stremio protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)
- [Manifest capabilities](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md)
- [Metadata and episodes](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md)
- [Stream source forms](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md)

These primary references were checked on 2026-10-23. They describe the protocol, not proof of working providers or media playback in this app.

## Verification

App/test builds, 19 JVM tests and 87 dedicated-VM tests pass (113.711 seconds); lint reports 0 errors/33 warnings. Three new adapter tests cover manifest resource matching, required/unsupported filters, encoded endpoints, duplicate catalog entries, metadata identity/episodes, explicit stream kinds, invalid URLs, strict/deep/oversized JSON and cancellation. APK SHA-256: 64db927144aa23f077caf89a84f799ef9dd57c9ee1c9a627875f38f849158f10. Room remains v10. Only AA_Player_Test_API_35 / emulator-5556 was used. No real provider, UI or Movies playback result is claimed.
