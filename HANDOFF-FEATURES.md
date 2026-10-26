# Feature implementation matrix

Status describes the **new** `rebuild/` project, not the original APK. "Prototype" means a basic route exists but original behavior is not reproduced. IDs and original entry points come from `analysis/dashboard-modules.csv`.

| ID | Dashboard entry | Current state | Original behavior / next implementation boundary |
| ---: | --- | --- | --- |
| 16 | YouTube | Prototype: public mobile WebView | Native NewPipe/Innertube discovery, Media3 source selection, subtitles, history and playback. See `ld4.e`, `n30`, `e40`, `r22`. |
| 5 | Web Browser | Prototype: fixed Google WebView | Address/search UI, bookmarks, history, downloads and navigation. See `cj0.g`, `i71`. |
| 6 | TV / IPTV | Unimplemented | M3U/Xtream playlists, categories, live/VOD/series, EPG, favorites, progress, playback. See `uw.u`, `nj3`, `kj3`. |
| 18 | Movies & Series | Unimplemented | Stremio-compatible add-ons/catalog/metadata/streams, cache and unsupported torrent handling. See `p10.d`, `jy2`, `g10`. |
| 1 | File Manager | Prototype: document picker | Directory navigation, document-tree grants, files and folder actions. See `wb4.f`. |
| 2 | Video Player | Prototype: one picked URI in VideoView | Media library, formats, playback history, subtitles, resume and media session. See `x10.h`. |
| 3 | Photo Gallery | Prototype: one picked image | Gallery navigation, thumbnails and image actions. See `zp` cases 22/5. |
| 9 | Radio | Unimplemented | Radio Browser/Radio Garden discovery and stream playback. See `vz.z`, `kz`, `dm`. |
| 10 | Music | Prototype: one picked URI in VideoView | Audio library, queue, background playback, lyrics. See `fy.o`, `kq1`, `il1`. |
| 11 | Speedometer | Prototype: GPS km/h | Units, accuracy, lifecycle and presentation tests. See `pc4.g`. |
| 13 | Podcast | Unimplemented | Search, RSS subscriptions, episodes, progress, download metadata. See `cz.l`, `ha2`. |
| 17 | News | Unimplemented | RSS sources, reading UI and speech support. See `ly.e`, `w22`, `cd4`. |
| 8 | Settings | Prototype: app settings link | Appearance, language, module order, playback and integration settings. See `l00.h`. |
| 12 | Screen Mirror | Unimplemented | User-consented MediaProjection, virtual display, cleanup and optional accessibility gesture forwarding. See `a00.f`, `ScreenMirrorService`, `hs4`. |
| 14 | YouTube Kids | Prototype: public WebView | Original WebView behavior and car/phone integration. See `jd4.d`. |
| 4 | YouTube Web | Prototype: public mobile WebView | Original uses bundled local FreeTube, Vue/Shaka, native bridge and separate file storage. See `zp` case 7, `e41`, `a41`; `analysis/javascript-bridges.csv`. |
| 15 | YouTube Music | Prototype: public WebView | Original WebView plus authenticated Innertube support. See `zp` case 14, `am3`. |
| 19 | YouTube Classic | Prototype: public mobile WebView | Original mobile YouTube WebView integration. See `gd4.c`. |
| 20 | Vehicle Maintenance | Prototype: unstructured saved notes | Service records with dates, odometer, due date/mileage and notes. See `ic4.h` and `analysis/database-schema.sql`. |

## Shared work outside the dashboard

| Area | Current state | Evidence and target |
| --- | --- | --- |
| Dashboard registry | Labels only | Preserve IDs, default order, custom `module_order`, `last_module_id`, `home_grid_columns`; see `analysis/REPORT.txt` and `dashboard-modules.csv`. |
| Media engine/session | Absent | Original Media3 HLS/DASH/progressive selection and metadata/session routing; see `analysis/REPORT.txt` media section. |
| Android Auto | Absent | Legacy projection service plus AndroidX car-template route and media service are separate paths; see original manifest and dossier. Verify host acceptance on test host. |
| Local data | Activity preferences only | Room v13 DDL in `analysis/database-schema.sql`; design migrations and do not assume recovered schema implies working migration behavior. |
| FreeTube storage/bridge | Absent | Separate NeDB files, document-tree mapping, 30 annotated `a41` bridge methods and native events; see dossier and `javascript-bridges.csv`. |
| Licensing/billing | Absent | Requires a new authorized product/backend policy. Original signer checks and publisher endpoints cannot be reused as proof of entitlement. |
| Optional VLC/TTS | Absent | Dynamic features/model pack not supplied. See `analysis/optional-components.json`; select independently licensed replacements only if needed. |

## Suggested implementation sequence

1. Buildable baseline, package identity, dashboard IDs, navigation and state restoration.
2. Local features: media library/player, photo browser, file navigation, maintenance database, settings and speedometer.
3. Network adapters: IPTV, radio, podcast, news and Stremio with fixtures, failures and persistence.
4. YouTube native/web paths and bridge contracts, with service and content policy review.
5. Media session, Android Auto host routes and projection/mirroring with device tests.
6. Billing, licensing and optional downloadable modules using newly owned services/assets.
