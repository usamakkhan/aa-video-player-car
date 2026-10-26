# AA Video Player Car — project handoff

Updated 2026-10-11 (America/Los_Angeles).

## Objective and present result

The request is to create a differently named Android app with the behavior of the supplied **AA CarPlay Video** APK set. The chosen new display name is **AA Video Player Car** and the new package/application ID is `app.aavideoplayercar`.

The work is **in progress**. `rebuild/` is an independent source project and a small functional prototype, not a complete copy or a verified APK. Its home screen lists all 19 recovered modules. Several routes have basic Android implementations; others explicitly display an unfinished message. Do not describe this project as feature complete or Android Auto compatible.

## Where everything is

| Path | Purpose |
| --- | --- |
| `rebuild/` | New Android Gradle source project. Start here for implementation. |
| `rebuild/app/src/main/java/app/aavideoplayercar/MainActivity.java` | Current one-file UI and route implementation. |
| `rebuild/app/src/main/AndroidManifest.xml` | Current permissions and launcher declaration. |
| `rebuild/app/build.gradle.kts` | SDK, package, and version configuration. |
| `HANDOFF-FEATURES.md` | Module-by-module gap map and suggested work order. |
| `HANDOFF-VERIFY.md` | Build prerequisites, checks, device test plan, and acceptance criteria. |
| `analysis/REPORT.txt` or `analysis/REPORT.html` | Main reverse engineering dossier and limitations. |
| `analysis/dashboard-modules.csv` | Confirmed dashboard IDs, names, and original entry points. |
| `analysis/semantic-class-map.csv` | Inferred roles for obfuscated native classes. |
| `analysis/database-schema.sql` | Recovered Room database v13 schema. |
| `analysis/javascript-bridges.csv` | Recovered web/native bridge signatures. |
| `analysis/web-inventory.json`, `analysis/web-routes.csv` | FreeTube routes, components and direct bridge calls. |
| `analysis/optional-components.json` | Missing downloadable VLC/TTS components and known interfaces. |
| `analysis/signature-verification.json`, `analysis/verification.json` | Original artifact integrity checks. |
| `base.apk`, `split_config.*.apk` | Original evidence. Keep immutable. |

The dossier references large supplementary outputs under a `C:\Users\Abuzar\...` path from its earlier analysis environment. That path is not present in this workspace by default. Treat the local `analysis/` files as available evidence and check whether any external output is actually accessible before depending on it.

## Original app, as established by evidence

- Package `com.aacarplayvideo.app`, display name AA CarPlay Video, version 1.2 (202); four supplied APKs: base, ARM64, English and xxhdpi splits.
- Native Android dashboard/controller, separate Android Auto paths, media service/session, and bundled FreeTube web application. The artifacts establish Android Auto integration, not Apple CarPlay support.
- The 19 dashboard entries and original IDs are in `analysis/dashboard-modules.csv`. Default order: `[16,5,6,18,1,2,3,9,10,11,13,17,8,12,14,4,15,19,20]`.
- Native Room database is version 13 with 21 app tables. FreeTube also stores separate NeDB-style files. These are separate data systems.
- The original has Play licensing, billing, publisher license/trial endpoints, and a certificate identity check. The new app does **not** reproduce these services or possess the original signing identity. Do not copy secrets or simulate entitlement as valid.
- Downloadable `vlcplayer`, `premiumtts`, and `ttsmodel` artifacts are absent. Their interfaces and call sites are traced, but their behavior cannot be recovered from this APK set.
- An original-device launch command succeeded, but UI capture landed on the phone lock screen. No visible dashboard, playback, car-host, or network behavior was verified on device.

Read `analysis/REPORT.txt` for detailed code evidence and explicit confidence limits. Decompiled Java/Smali and formatted JavaScript are reference material, not original buildable source.

## New project state

The Gradle project declares Android application plugin 8.9.2, `compileSdk`/`targetSdk` 35, `minSdk` 26, version 0.1. Its manifest contains only internet and coarse/fine location permissions plus one launcher activity. There is no car service, media-browser service, accessibility service, projection service, billing, database, dependency injection, or test suite yet.

`MainActivity.java` currently provides:

1. A two-column dashboard containing 19 labels.
2. Generic WebView routes for browser and five YouTube-related labels. The native YouTube and local FreeTube implementations are **not** reproduced.
3. Android document picker for local video, audio, images and arbitrary files; VideoView/ImageView display after selection. The file-manager route is a picker, not a file browser. Audio also uses VideoView and has no music library.
4. GPS speed in km/h after runtime location permission, with no unit setting or car display.
5. Simple maintenance text notes in Activity preferences. The original structured service records and Room database are not implemented.
6. A settings page with package/version and link to Android app settings.
7. An unfinished message for all remaining routes.

Known prototype limitations: module IDs/order are not modeled in code; WebView routes may be blocked or limited by the sites; back press always returns home rather than navigating WebView history; no persistence of media state, no background media session, no car host, no offline handling, and no automated tests. The code was checked for consistent package naming, balanced braces, and parseable manifest/resources XML. It was **not compiled or run** because Android SDK and Gradle were absent from the host.

## Recommended architecture for continued work

Split the current single Activity into a dashboard registry with stable original module IDs, typed feature screens, data repositories, a playback service, and platform adapters. Keep phone UI, Android Auto integration, and web/FreeTube bridge as separate layers. Define service interfaces for external catalog/stream providers and use controlled fixtures before relying on live APIs. Plan a Room schema and migrations independently from the FreeTube file storage contract. Record every behavior as implemented, partially implemented, or unverified in `HANDOFF-FEATURES.md`.

Do not port obfuscated class names or decompiler artifacts directly into the new architecture. Where exact original behavior matters, confirm JADX conclusions against the dossier's Smali evidence and with runtime tests. The original build configuration, server implementation, signing keys, and optional module bytes are unavailable.

## Immediate next actions

1. Set up Android SDK 35 and Gradle 8.11.1+, add a Gradle wrapper, compile and install the current project; fix any compiler/runtime issues before expanding scope.
2. Run the baseline smoke tests in `HANDOFF-VERIFY.md`; capture screenshots and logcat for the new app.
3. Replace the dashboard string switch with stable module IDs and the recovered default order, then implement navigation and preference persistence.
4. Build local media, browsing and maintenance as full independent modules; add persistence and lifecycle/media-session behavior.
5. Implement external and car integrations in the order and with the evidence listed in `HANDOFF-FEATURES.md`.
6. Validate each integration on an unlocked Android device and a compatible Android Auto test host. Update the feature matrix with results and proof.

An exact behavioral copy cannot be asserted from static APK recovery alone. A complete parity claim requires working source/build, authorized service fixtures, and the test matrix to pass on actual devices and hosts.
