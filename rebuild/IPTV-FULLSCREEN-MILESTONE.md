# IPTV fullscreen — 2026-10-13

The IPTV screen has a Fullscreen button and an always-visible Exit fullscreen button. Fullscreen hides playlist controls and the activity header/Home button, expands the existing PlayerView to the available space, hides the keyboard/system bars and permits temporary system-bar reveal by swipe. Back exits fullscreen before navigating out of the channel list. Exiting restores the embedded player and surrounding controls.

The screen saves fullscreen state with its playlist/page/filter state. Rotation and activity recreation reconnect the view to the shared Media3 service; fullscreen does not create another player or force landscape orientation. Screen teardown restores the window bars. Android 30+ uses WindowInsetsController; Android 26–29 has a guarded legacy UI-flag path. The previous system-bar behavior is restored on exit. A narrowly scoped lint suppression documents that the stored value was returned directly by the controller getter and lost its IntDef annotation when stored.

The instrumentation fixture plays synthetic H.264 video over a local HTTP server, checks decoded video, expands the player, rotates to landscape, recreates the activity, exits using Back and then repeats entry/exit with the button. It checks the same media identity and active playback, controls returning, and system status-bar visibility. It does not establish frame-perfect continuity, visual polish, accessibility, physical-device rotation or Android 26–29 runtime behavior.

Final app/test builds, 17 JVM tests and 49 dedicated Android 15 VM tests pass (63.191 seconds). Lint: 0 errors/33 warnings. App APK SHA-256: `40f45e1d038e8b7ead5afd0aafcd5ed7101a2413bcb75c57c9507be386cf986c`. Only `AA_Player_Test_API_35` was used; the personal emulator was untouched. The first passing run preceded the final system-bar restoration change; the final run includes that change and visibility assertions.

Xtream, EPG/XMLTV, richer browsing, authentication, durable file grants, real-provider playback and Android Auto/physical-car gates remain open. Fullscreen is phone UI functionality, not evidence of car video support.
