# Radio development milestone — 2026-10-12

## Implemented

Radio (dashboard ID 9) now has station-name search, optional two-letter country filtering, 50-result directory pages, favorite browsing, save/remove actions, and live playback through the existing Media3 service. Search starts only after the user requests it; opening Radio loads saved favorites without a directory request. Input, mode and page survive recreation; directory results are refetched on restoration.

Station actions offer Play live and Save/Remove favorite. Shared pause/resume/stop controls and playback status follow the existing service, including playback started from other screens. Leaving Radio releases its controller and requests but does not stop background playback.

Room schema v3 adds `radio_stations` with station identity, resolved stream URL, title, country, codec, HLS flag, favorite state and refresh time. Existing media and maintenance tables are unchanged. Explicit v2-to-v3 migration joins the existing v1-to-v2 path. Metadata refresh preserves favorite state. Nonfavorite station records are bounded to 100; favorites and the most recently selected station are retained.

Radio IDs have a separate `radio:` namespace. The service resolves their stored URLs, rejects arbitrary supplied controller URLs, and never saves or restores a track position for radio. HLS format is separate from audio classification. The audio library root now contains Music and Radio favorites folders; service search includes favorites after local music. External trusted clients can resolve saved favorites, while the phone can also play an unsaved search result.

## Network behavior

Implementation reference: [Radio Browser API](https://docs.radio-browser.info/), checked during development. The client identifies itself with `AA-Video-Player-Car/0.1`, searches over HTTPS, uses resolved URLs where valid, and tries a second endpoint on directory failure. Both `de1.api.radio-browser.info` and `all.api.radio-browser.info` returned HTTP 200 with BBC station results in a host-side smoke check.

Requests have connect/read timeouts, an elapsed-response deadline, a 1 MiB response bound, bounded result counts, and cancellation. Network work uses two threads with a bounded pending queue, separate from the database worker. Stale screen results are discarded. Malformed IDs, duplicate stations, non-HTTP(S) URLs and embedded URL credentials are rejected; displayed country/codec metadata is bounded.

Public radio streams may use HTTP. The app now permits cleartext transport for those streams; the directory client remains HTTPS-only and WebView explicitly rejects mixed content while retaining HTTPS-only navigation. This transport choice must be considered when adding authenticated providers: credentials must not be sent to cleartext endpoints.

## Verification

The final build/test totals and APK hash are recorded in `../HANDOFF-VERIFY.md` after the final verification run. New coverage includes:

- Malformed, oversized and unsafe directory response fixtures; invalid queries and cancellation before connection.
- Favorite refresh, removal, reopen persistence and bounded transient-cache behavior.
- Actual exported-schema v2 migration preserving media progress, maintenance records and import markers. The existing v1 upgrade test now migrates through v3.
- Phone favorite loading, query restoration, validation and favorite-list refresh.
- Music/radio browser folders, radio search, and actual HTTP playback from a local fixture server, including a forged request URI and ignored 9-second start position.

The first HTTP fixture failed because its server bound IPv6 but the URL addressed IPv4. The fixture now explicitly binds `127.0.0.1`; the network playback test passed afterward. This was a test transport setup defect, not a reason to bypass stream or controller validation.

## Remaining work

This is core Radio Browser support, not completed radio parity. Radio Garden discovery, provider server discovery/rotation, provider click accounting, artwork, richer filters, background metadata refresh, station URL renewal, and broader real-station/device playback coverage remain. M3U/PLS links unresolved by the directory may fail; dedicated playlist resolution remains to be added. Actual remote HLS playback, outages during an established stream, notification UX and car-host acceptance are not established by the loopback WAV test.

Directory HTTP error/timeout handling exists but does not yet have deterministic transport-level fault injection tests. Host-side API success does not prove every device/provider path. No visual screenshot approval is claimed.

Legacy clients requesting one unpaged Music or Radio favorites list still receive at most 500 entries. Full large-library discovery on those clients remains open. The full app goal, remaining modules, performance benchmarks and physical-car gates remain active.
