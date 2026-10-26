# Development review — 2026-10-11

Historical foundation review. The 2026-10-12 continuation in `PLAYBACK-MILESTONE.md` supersedes the VideoView, absent-service, absent-database and absent-Git descriptions below. Full scope remains open.

## Scope and evidence

Read all three root handoffs, every original rebuild source/configuration file, the reverse-engineering dossier, dashboard and semantic class maps, database DDL, optional-component contracts, and verification summaries. Cross-checked dashboard IDs, ordering, and preference keys against `analysis/jadx/sources/defpackage/st.java`. Inventoried the entire workspace by path: the largest groups are 9,660 JADX files (sources plus resources), 1,157 extracted artifacts, 75 web-module files, 72 decoded-split files, and multiple fallback-decompilation sets. These are primarily reference material and dependencies, not new application source. This was not a line-by-line semantic audit of every decompiled library or native binary.

Recomputed SHA-256 for all four input APKs: all match `analysis/apk-inventory.json`. Original evidence remains unchanged. No Git metadata exists in this workspace. Historic analysis scripts and large-output references contain paths from another machine; their earlier success does not establish that those scripts can run unchanged here.

## Findings and decisions

1. The prototype used display labels for routing and a different dashboard order. Introduced an immutable typed registry preserving all 19 recovered IDs/default order. Saved-order parsing drops unknown/duplicate tokens and appends missing modules, so damaged or stale preferences do not hide features.
2. Navigation always jumped home, leaked old WebViews until activity destruction, and discarded state on rotation. Added WebView history handling, explicit Home, home Back exit, screen cleanup, and saved route/WebView/media state. Settings persist layout, order and optional last-module startup.
3. Media picker routes lacked their own screens, and File Manager tried to play every document as video. Added explicit Choose a file screens, MIME-based preview dispatch, unsupported-type feedback, and persisted URI permission requests. This is still a picker/preview, not a library or file manager.
4. Location continued while the app was backgrounded; denied permission produced no useful follow-up. Updates now stop on pause/navigation and restart on resume. Requests include coarse and fine permission together, and the screen distinguishes missing permission, disabled GPS and missing speed.
5. The handoff's claim that SDK/Gradle were absent was stale. Found Android Studio JBR 21, Gradle 9.3.1, AGP 9.1.0 and platform 36.1. Pinned those build tools, generated the wrapper, compiled against SDK 36.1 while retaining target 35/minimum 26, and added unit/instrumentation test tasks. Tool compatibility reference: https://developer.android.com/build/releases/agp-9-1-0-release-notes .

## Remaining architecture work

The registry is separate, but MainActivity still owns most screen logic. Extract browser, local-media, speedometer and maintenance screens behind lifecycle-aware interfaces as those features expand. Next useful feature slice is structured maintenance storage with schema/migration tests, preserving the old notes; then a media repository and Media3 foreground session with queue/resume controls. Keep platform adapters and external-service clients separate from screen state.

Do not import the recovered Room identity hash into a new independently designed database or assume its v13 DDL supplies migrations. FreeTube's NeDB files are a separate storage system. Its recovered write-error and directory-key bugs are documented behavior to avoid, not parity targets.

External modules remain placeholders. Native YouTube and FreeTube are still public-site fallbacks. IPTV, Stremio, radio, podcasts and news need fixtures and independent adapters; car-host and mirror work needs separate device/host tests. Optional VLC/TTS binaries and publisher server implementations remain absent.

## Known limits

- English strings remain inline; localization and adaptive layouts need more work.
- Dashboard supports 2–4 columns and move-to-first ordering, not the original full drag/drop and automatic 3–6 column behavior.
- VideoView is a foreground prototype; codecs, background sessions, playlists, subtitles, full process-relaunch media recovery and media permission revocation need dedicated tests.
- ImageView loads selected images directly; bounded thumbnail decoding and large-image handling are still needed.
- WebView has HTTPS-only top-level navigation, no native JavaScript bridge, and disabled file/content access; downloads, bookmarks, fullscreen video and authentication flows are not implemented or verified.
- Speed is km/h only. Real GPS, approximate-only grants and provider/permission revocation require device coverage.
- Maintenance remains unstructured preferences; no database migration is claimed.
- Compilation/lint and route smoke tests do not establish content playback, live service compatibility or Android Auto support.

Build/test results and artifact location are maintained in `../HANDOFF-VERIFY.md`.
