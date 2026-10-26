# Podcast subscription and playback milestone — 2026-10-12

## Implemented scope

Dashboard Podcast (ID 13) now opens a subscription screen. Users can add an HTTP(S) RSS or Atom feed URL, browse subscriptions and audio episodes in 50-item pages, refresh a selected feed, and unsubscribe with confirmation. Feed selection, page and URL input survive recreation. Existing cached subscriptions and episode lists load without fetching the network. Subscriptions are committed after a successful fetch and parse; navigation or screen closure cancels pending network work and ignores stale callbacks.

An episode selection uses the shared Media3 service and queues the displayed page. Player controls support playback, seek and resume. Podcast IDs use a separate `podcast:` namespace. The service resolves stored URLs rather than supplied controller URLs, saves listening progress, resumes it when requested by ID, and resets completed items to the beginning through the existing progress policy. Radio retains its separate live-stream behavior.

The media-browser root now exposes Music, Radio favorites and Podcasts. The Podcasts folder contains subscriptions, with episodes under each subscription. Library search includes podcast episodes after music and radio results. These are protocol-level capabilities; Android Auto host acceptance remains unverified.

## Persistence and parsing

Room schema v4 adds `podcast_feeds` and `podcast_episodes`, an indexed feed relationship, and cascading removal of a subscription's episode records. The explicit v3-to-v4 migration composes with the existing v1/v2 upgrade paths. Refresh updates metadata while retaining episode identity, listening position and previously stored episodes that no longer appear in a publisher's current feed. A refresh of a removed subscription cannot recreate it; resubscribing explicitly can.

Episode identity is scoped to its subscription and uses a supplied GUID/Atom ID when present, otherwise the enclosure URL. URL-only identity cannot deduplicate an episode whose publisher changes its URL and supplies no stable ID. Redirect aliases and equivalent differently spelled feed URLs are not yet merged.

The parser supports RSS audio enclosures and Atom enclosure links, namespaces, inherited XML base URLs, escaped/nested titles, publication dates, and basic iTunes durations. It rejects non-feed roots, incomplete XML, DTDs/external entities, excessive nesting and oversized documents. Invalid/non-audio enclosure entries are skipped. Inputs are bounded to 4 MiB, 2,000 feed items and bounded field lengths; exceeding a document/item limit fails explicitly instead of silently truncating the stored feed.

References checked during development: [RSS 2.0 specification](https://www.rssboard.org/rss-specification) and [Atom specification, RFC 4287](https://www.rfc-editor.org/rfc/rfc4287). Support is intentionally described by the tested subset above, not as complete feed-specification conformance.

## Network and lifecycle

The document fetcher accepts HTTP/HTTPS URLs without embedded credentials, validates redirect targets, follows at most five redirects, handles gzip with a decompressed-size limit, and enforces connect/read timeouts plus an elapsed-response deadline. It does not send authentication cookies. Requests use a descriptive application User-Agent. Two network workers with a bounded waiting queue run separately from database work. Cancellation interrupts queued work and disconnects the active request.

HTTP remains permitted for public media/feed sources, as introduced for radio. This is not an authenticated/private-feed implementation. Feed bodies and episode descriptions are not rendered as executable web content.

## Verification

Final build, unit, lint and instrumentation commands passed. Totals: 13 JVM tests and 28 Android 15 emulator tests, no failures/errors/skips; lint 0 errors/31 warnings. APK identity is recorded in `../HANDOFF-VERIFY.md`.

New instrumentation covers:

- RSS identity, duplicate GUIDs, escaped titles and publication dates; Atom namespaces, nested titles, relative URLs, and rejected enclosures.
- Malformed/incomplete XML, external entities, invalid URLs and document limits.
- Refresh preserving progress and archived episodes, database reopen, cascading unsubscribe, and prevention of refresh resurrection.
- Exported-schema v3 migration retaining existing radio favorites and local media progress. Earlier v1/v2 migration tests now run through v4.
- Real loopback HTTP redirects, gzip, missing endpoints, redirect loops, unsafe redirect schemes, cancellation before connection and decompressed-size limits.
- Media-browser podcast folders/search and actual HTTP audio playback with saved progress, queue clearing/reload and a forged controller URI.
- Phone subscription, input validation, 51-episode paging, recreation, Back navigation and metadata refresh without duplication.

Testing caught Android's XML parser accepting an incomplete document without throwing; an explicit document-completeness check now rejects it. A migration fixture also needed to tolerate omitted empty index lists in exported schemas. Both regressions were corrected before the successful run.

## Remaining podcast and app work

Podcast discovery/search providers, downloads and download metadata, offline playback, scheduled refresh/conditional requests, artwork, richer episode details, playback speed/sleep timer, import/export, feed URL canonicalization and publisher redirect renewal remain. Refresh currently notifies the subscription root, but active per-feed media-browser subscriptions still need explicit episode-list invalidation. The player status label and layout need visual/accessibility review. Cancelling an in-flight subscribe during recreation retains the typed URL but does not automatically resume that subscription request.

Real publisher feeds, real remote codec/redirect combinations, notification interactions, large-history performance and physical devices/car hosts remain to be exercised. These tests use controlled fixtures and do not establish broad provider compatibility, visual approval, feature completeness or absence of all bugs. The full 19-module app goal remains active.
