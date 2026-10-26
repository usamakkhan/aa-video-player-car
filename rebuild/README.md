# AA Video Player Car

Independent Android source project based on the feature inventory in `../analysis/REPORT.txt`. Display name: **AA Video Player Car**. Package: `app.aavideoplayercar`.

For continuation, read `../HANDOFF.md`, `../HANDOFF-FEATURES.md`, and `../HANDOFF-VERIFY.md`.

## Current implementation

- Dashboard with the recovered 19 entries.
- Browser and YouTube web views, local video/audio/image picker, speed display, vehicle maintenance notes, and settings. YouTube entries use the public website and do not reproduce the original native extractor or bundled FreeTube app.
- Other entries open an explanatory feature page. They are not claimed as implemented.

## Build

Install Android SDK 35 and Gradle 8.11.1 or newer, then run `gradle :app:assembleDebug` in this directory. This workspace has no Android SDK or Gradle installation, so the APK has not been built here.

## Missing for parity

Native YouTube extraction/player, FreeTube bridge and storage, IPTV/Xtream and EPG, radio, podcasts, news, Stremio add-ons, Android Auto host integration, mirroring, optional VLC/TTS modules, and backend licensing/billing need new implementations and integration tests. The supplied APKs do not contain original source, server code, or downloadable module binaries. The original APK signing certificate and publisher services are not reused.
