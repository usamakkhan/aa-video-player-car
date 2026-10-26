# AA Video Player Car

Independent Android rebuild in progress, based on analysis of the supplied AA CarPlay Video APKs. The new package is `app.aavideoplayercar`.

The app includes implemented portions of media playback, IPTV, podcasts, radio, news, Movies & Series and File Manager. Feature parity and Android Auto host acceptance remain incomplete. See the [feature matrix](HANDOFF-FEATURES.md) and [development plan](rebuild/DEVELOPMENT-PLAN.md) for the remaining work.

Build instructions are in the [Android project README](rebuild/README.md). Device tests use a separate `AA_Player_Test_API_35` VM; see [testing setup](rebuild/TESTING-VM.md). The 2026-10-26 APK passed 106 VM device tests, 19 JVM tests and lint with 0 errors/33 warnings. See the [verification handoff](HANDOFF-VERIFY.md).

Original APKs, emulator state and generated build output stay outside Git.
