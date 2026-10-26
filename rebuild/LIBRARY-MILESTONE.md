# Library paging and queue bounds — 2026-10-12

## Implementation

- Phone music/video libraries now offer Previous/Next navigation over 50-item pages, with a count and current page indicator. Only the displayed page is retained by the screen. Items past the former first-500 cutoff are reachable.
- Page loading remains off the UI thread. Navigation is disabled during requests, stale results are discarded, and database errors produce feedback. A page beyond the current last page is clamped when loading.
- Selecting an item builds a queue from the displayed page. A shared queue helper bounds the queue at 500 and always includes the selected item, including newly imported items outside the displayed list. Selection within an already-playing item retains its existing queue.
- Service browse/search requests use the same capped page size for SQL limits and offsets. For requested sizes above 500, the effective size is 500; subsequent page indices advance by 500 rather than skipping records. Invalid/overflowing page requests are rejected.
- No database schema change or migration is needed.

## Verification scope

Five new JVM tests cover selection outside a full queue, selection beyond the first queue window, an empty library, contiguous oversized pages and invalid/overflowing pagination.

One new Android instrumentation test inserts 520 ordered audio fixtures, verifies browse and search at the 500-item boundary, navigates the actual phone library to page 11, and plays item 500 through the shared service. Fixtures are removed afterward. Existing playback, dashboard and maintenance tests remain in the suite.

Exact final results and APK hash are recorded in `../HANDOFF-VERIFY.md` after verification completes.

## Remaining scope

- This is bounded page navigation, not whole-library automatic queue continuation. Page position is not yet restored across screen recreation; the playback service continues independently.
- Legacy hosts that ask for an unpaged list still receive at most 500 children. A hierarchical car library or additional compatible host paging is needed for full large-library discovery by those clients.
- Offset paging assumes a stable library during traversal. Future background scanning needs snapshot/keyset semantics or explicit invalidation refresh to avoid shifted rows.
- The 520-item functional fixture is not a 10,000-item performance benchmark or a visual-layout approval. Accessibility/layout review and measured device performance remain open.
- The emulator still reports `/product/priv-app/AndroidAutoStubPrebuilt`, version `1.2.542030-stub`; no full Android Auto host session is established.
- Radio, podcasts and the other unfinished modules remain in the full development plan. This milestone does not complete the app or prove absence of defects.
