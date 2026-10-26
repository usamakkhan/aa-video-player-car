# Podcast discovery milestone — 2026-10-13

Podcast search now uses the Apple iTunes Search API to find shows by name and country. Results display title and publisher; selecting a show opens its feed URL and subscription confirmation, with an optional link to its Apple Podcasts page. Confirming fetches the publisher's RSS/Atom feed through the existing bounded network client and imports episodes into Room. Playback continues through the shared Media3 service.

The adapter requests at most 50 results, validates feed URLs, filters non-podcast/invalid entries, deduplicates feeds, and accepts store links only on Apple's HTTPS podcast/iTunes domains. It has a 20-query in-memory cache with a ten-minute lifetime and spaces network request starts at least three seconds apart. Failed responses are not cached. These limits follow the published [Apple Search API guidance](https://performance-partners.apple.com/search-api). No preview media or artwork is copied from directory responses.

Screen state retains the query, country and result mode. Open search-dialog drafts are saved separately from the last submitted query, so restoring a draft does not submit it. Invalid input keeps the dialog open for correction. Navigation cancels requests and ignores stale callbacks. The directory adapter is injectable for deterministic device tests without changing production endpoints.

## Verified result

App/test APK builds, 13 JVM tests and lint pass. Lint reports 0 errors and 32 warnings (the previous 31 plus a test-dependency version warning). All 35 instrumentation tests pass on the dedicated `AA_Player_Test_API_35` Android 15 VM, serial `emulator-5556`, in 25.661 seconds. The user's personal emulator was not used. Raw device output is in `.tools/testing/instrumentation.txt`.

Seven discovery tests cover directory URL encoding/validation, bounded parsing and immutable deduplicated results, cache expiry/eviction/rate limits/failure retries, real loopback HTTP JSON, the phone search-to-subscribe-to-unsubscribe path with an actual HTTP feed, restored result state, retry after provider failure, ignored late responses after navigation, and invalid-input/draft restoration. Dialog actions use Espresso; asynchronous list readiness is checked on the main thread. Existing playback, migration, feed, radio and dashboard regressions also pass.

App APK SHA-256: `0eb863d5bfebd38a9698c4b29dffc251592ed084595aafb55e70dca84f2d29a1`.

## Remaining scope

This establishes controlled provider fixtures and screen-state restoration, not live-provider device compatibility or visual approval. Earlier host-side live directory HTTP success is narrower evidence. Downloads, offline playback/storage management, conditional/scheduled refresh, artwork, richer episode details, speed/sleep controls, import/export, URL canonicalization and per-feed media-browser invalidation remain. Android Auto/DHU and physical-car gates remain open. The full 19-module app goal is not complete.
