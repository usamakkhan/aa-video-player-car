# Dedicated Android testing VM

Created 2026-10-13 at the user's request. Their existing Pixel_9_Pro emulator is excluded from all future app testing.

- AVD: `AA_Player_Test_API_35`
- OS: Android 15 / API 35, Google APIs, x86_64
- Hardware profile: Pixel 7, 1080 x 2400, 3 GB RAM, 4 virtual CPU cores
- Accelerator: Windows Hypervisor Platform
- Test port: 5556; adb serial: `emulator-5556` (always verify AVD name)
- Private writable disk/config: `D:\GIT\com.aacarplayvideo.app\rebuild\.tools\testing\AA_Player_Test_API_35.avd`
- AVD registry: `C:\Users\gaura\.android\avd\AA_Player_Test_API_35.ini`
- Logs: `rebuild/.tools/testing/`

The VM uses the installed read-only Android system image and its own new writable data disk. No personal VM data was copied. It runs headless with snapshots disabled for command-line test use. It also appears in Android Studio Device Manager.

From the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\rebuild\gradlew.bat -p rebuild :app:assembleDebug :app:assembleDebugAndroidTest
.\rebuild\Test-AndroidVm.ps1 -RunTests
```

Omit `-RunTests` to boot/verify only. The script refuses installations if the serial belongs to another AVD. Every adb mutation explicitly selects the test serial. Never use unfiltered `connectedDebugAndroidTest`, `adb install`, or device data/reset commands while the personal emulator is connected.

For the separate persisted-download process restart check, run `./rebuild/Test-AndroidVm.ps1 -RunRestartProbe` from the repository root after building both APKs. It seeds completed/paused fixtures, rechecks the VM identity, force-stops only the test app on that VM, confirms its process is absent, and verifies in a new process. The probe cleans up its own records/cache on success. `-RunTests -RunRestartProbe` runs both checks. Logs are `restart-seed.txt` and `restart-verify.txt` under `.tools/testing`.

The registered test runner is `app.aavideoplayercar.DownloadRestartProbe`; without a `phase` argument it delegates to the standard AndroidJUnitRunner. With `phase=seed`, `verify` or `cleanup`, it runs the separate restart fixture. These phases are not counted as JUnit test cases. The runner exists only in the test APK.

Command-line tools were downloaded from Google's official Android distribution, SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`, and installed at `C:\Users\gaura\AppData\Local\Android\Sdk\cmdline-tools\19-testing`. AVD creation emitted missing devices.xml warnings for installed system-image packages; the selected Pixel 7 profile was created and booted successfully. Emulator microphone initialization is unavailable on this host; microphone testing is not established.

Android Auto/DHU compatibility, visual inspection, real publisher streams, and physical-car behavior require separate validation.

## Verification — 2026-10-13

Fresh VM boot completed; app and test APKs installed successfully. All 32 instrumentation tests passed in 27.022 seconds on this dedicated VM. Coverage includes dashboard, maintenance, playback service, radio, podcast feeds and directory parsing/cache behavior. Raw output: `.tools/testing/instrumentation.txt`. Tested app APK SHA-256: `fad9e445e7f60023b3eac3cbcf0c4997bbadbba7bf91555c2608f1a0060a8913`. This APK includes directory discovery work newer than the podcast core milestone; it does not establish live directory UI or full feature completion.
