# IPTV M3U core — 2026-10-13

Superseded by [playback-type continuation](IPTV-PLAYBACK-TYPE-MILESTONE.md): explicit channel classification is now implemented, Room is v6 and the suite has 48 passing device tests. Counts and remaining classification work below describe this earlier milestone.

The TV / IPTV dashboard route now opens an implemented playlist screen. Users can name and import an HTTP(S) extended M3U playlist or choose a local M3U document, browse playlists/channels in 50-item pages, search channel or group names, hold a channel to toggle its favorite, filter favorites, refresh, and remove a playlist with confirmation. Inputs, selected playlist, filter and page survive activity recreation. Failed fetch/parse operations leave existing persisted playlists intact.

Local import uses the system document picker and attempts to retain the returned read grant. Refresh reads the saved content URI on a worker, with cancellation and the same 4 MiB bound. Local playlists must contain absolute HTTP(S) channel URLs; relative document paths are skipped. Missing or inaccessible documents report an error without replacing stored channels. Selecting the same URI preserves identity; selecting a different URI creates a separate playlist. Persisted-grant behavior across reboot, actual grant revocation and cloud/virtual document providers still require validation.

Channel selections use the existing shared Media3 service and PlayerView. The service resolves `iptv:` IDs from stored channels and rejects supplied replacement URLs. Playlist entries with positive EXTINF duration use saved viewing progress; entries with zero/negative duration start with the stream default position and do not save live progress. IPTV items are accessible to the local app controller and are not added to the audio/car browse or search catalog. No Android Auto video support is established by this work.

## Parsing and storage

The parser handles UTF-8/BOM, CRLF, double-quoted tvg-id/tvg-name/group-title, commas inside quoted attributes and display names, EXTGRP, and relative HTTP(S) stream URLs resolved against the fetched playlist location. It rejects HLS manifests as channel lists, malformed UTF-8, oversized input and lists without supported channels. Invalid, unsupported and duplicate entries are counted for the import result. Bounds are 4 MiB per document, 16,384 characters per line and 10,000 URL entries. Embedded URL credentials and non-HTTP(S) stream schemes are rejected. Request work is cancellable, runs off the UI thread and uses bounded network concurrency.

Channel identity is scoped to playlist source plus normalized stream URL. Refresh preserves favorites and VOD progress for matching URLs, replaces metadata and removes channels no longer present in the playlist. A refresh cannot resurrect a removed playlist. Source aliases or rotating stream URLs are not merged, so changed URLs do not automatically inherit old favorites/progress. Refresh reads previous state in one bounded query rather than one query per channel.

Room v5 adds playlist/channel tables, an indexed playlist relation and cascading deletion. The explicit v4-to-v5 migration composes with existing v1/v2/v3 migrations; no destructive migration is used. Schema 5 is exported. `tvg-id` is retained for later EPG integration.

Format references: [VideoLAN M3U parser](https://github.com/videolan/vlc/blob/master/modules/demux/playlist/m3u.c) and [HLS RFC 8216](https://datatracker.ietf.org/doc/html/rfc8216/). This is a tested subset of extended M3U behavior, not full provider or playlist-format compatibility.

## Verified result

App/test builds, 17 JVM tests and 46 Android 15 instrumentation tests pass. The final device suite took 46.125 seconds on dedicated `AA_Player_Test_API_35`; the personal emulator was not used. Lint: 0 errors/33 warnings. App APK SHA-256: `1a48478384281965a6932ece24d694f0e3408dab60a1893ebd244a6f858b0f4b`.

Local-document coverage adds stable content-URI identity, rejection of relative local channel paths, actual ContentResolver reads from temporary MediaStore Downloads documents, the size bound, cancellation before reading, missing-file failure, picker-result routing into IPTV, activity recreation, refresh retaining favorites/progress and failed refresh preserving existing channels. Tests inject the picker result; they do not navigate the system picker or establish durable SAF permission grants. Fixtures are removed afterward.

Coverage includes parser attributes/relative URLs/identity/rejections/bounds, a 10,000-channel import and final-page query, favorites/progress preservation, removal and no-resurrection semantics, v4 upgrade retaining podcast progress, prior migration paths to v5, HTTP gzip import, 51-channel phone paging/recreation/favorite filtering, and real MP4/HLS playback through the shared service with a display surface. The video test verifies decoded width, VOD resume, ignored live start offsets, controller URL protection and exclusion from the audio search catalog.

Synthetic H.264 video fixtures are generated locally from FFmpeg's test pattern and packaged only in the test APK. The HLS fixture is a finite VOD manifest used to test segmented transport and live-tagged start policy; it does not prove real live-edge behavior. No third-party TV footage or stream service is bundled. The fixture README records generation details.

One measured run parsed/imported 10,000 channels in 645 ms; counting and reading the last page took 13 ms on this VM. These are single-run measurements, not a physical-device benchmark or a latency percentile guarantee.

## Remaining IPTV and app gates

Xtream, EPG/XMLTV, richer category/series navigation, logo loading, explicit live/VOD overrides, provider-required headers/authentication, DRM, catch-up, playlist alias/URL renewal, fullscreen and visual/accessibility polish remain. Many providers label VOD with duration -1; those entries currently follow the live policy until explicit classification support is added. Real-provider streams, live-edge behavior, remote DASH and device codec/network failures require further tests. Playlist removal/refresh during active playback, rapid action races and lifecycle interruption coverage need expansion.

The other module gaps, podcast download recovery/adaptive gates, Android Auto/DHU and physical-car requirements remain open. The full app goal is not complete.
