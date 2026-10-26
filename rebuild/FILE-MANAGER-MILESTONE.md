# File Manager milestone — 2026-10-25

## Implemented

- A separate File Manager dashboard route requests a user-selected folder through Android's document-tree picker and persists the granted read/write flags. The previous tree grant is released only after a replacement grant succeeds.
- The selected tree can be browsed through nested folders. The screen supports Back/Up, refresh, in-folder search, and local pages of 50 items. Provider queries are bounded to 10,000 rows and report truncation.
- Files open through Android's document viewer. Rename and delete are offered only when the provider advertises support and the app holds a write grant. Delete requires confirmation; mutations run off the UI thread.
- New folder uses `DocumentsContract.createDocument` within the selected tree when the app holds a write grant. The folder name is validated before the provider call.
- Folder selection and browsing state survive activity recreation. The persisted folder grant was also checked after force-stopping and restarting the app.

## Verification

- Full Android 15 dedicated-VM suite: 106 device tests passed in 155.668 seconds before a strings-only plurals edit.
- Final APK: 4 focused File Manager device tests passed in 1.451 seconds; 19 JVM tests passed; app/test builds passed; lint reported 0 errors and 33 warnings.
- Final APK SHA-256: `217e6787fd11683a09d0516ba78e31940019159012c79c8568c43d4c8332a54f`.
- Manual test with the real Android ExternalStorageProvider on `AA_Player_Test_API_35` / `emulator-5556`: grant `Documents/AAFileTest`, browse a nested folder, rename a test file, force-stop/restart and verify the folder remains accessible, then delete the file after confirmation. A final sample file and screenshot were restored for preview at `.tools/testing/file-manager-preview.png`.
- All device commands explicitly targeted `emulator-5556`. The user's `Pixel_9_Pro` / `emulator-5554` was not used.

## Continuation verification — 2026-10-25

- New folder was created through the File Manager UI in the real `AAFileTest` document tree. It appeared in the refreshed list beside the existing folder and file.
- The final app APK SHA-256 is `d5a5f4d24fd770f4727b8ba4eca921ba99b905437acfd0863493eafa31a6a183`. App/test builds and 19 JVM tests pass; lint remains 0 errors/33 warnings.
- A full 106-test VM run passed in 159.967 seconds after fixing a test-only News speech latch race. Earlier full runs each had one intermittent test failure (Movies dialog focus, then News speech latch); the focused Movies rerun passed 4/4. The final full run passed both suites. This does not establish absence of all runtime bugs.

## Copy and move continuation — 2026-10-26

- Files advertise Move when the provider supports it. The user selects Move, browses within the granted tree and taps Paste here; `DocumentsContract.moveDocument` receives both the source and target parent URIs. Moving to the same parent is disabled.
- Copy uses provider-side `copyDocument` when supported. For ordinary files without native copy support, it streams through a 64 KiB buffer into a newly created document and removes the destination if the copy fails. Partial and virtual documents are excluded from this fallback.
- Pending copy/move selection survives activity recreation and is cleared when a different tree is selected. Navigation and tree replacement are disabled while a mutation is running.
- On the separate `AA_Player_Test_API_35` / `emulator-5556` VM, the real ExternalStorageProvider moved `sample.txt` into `Nested`, then copied it back to the selected root. The fallback copy and source had identical SHA-256 digests. A pending copy survived rotation, and pasting it into `CreatedByApp` again produced a digest-identical file.
- Final app APK SHA-256: `df5cd57aeadcc455d803e00ee37a4143e28720c9f15a430cede38d770886c3d5`. The final full device suite passed 106 tests in 158.825 seconds; 19 JVM tests passed; app/test builds and lint passed with 0 errors/33 warnings.

## Remaining work

File Manager still needs folder-level copy/move, cross-provider copy, multi-root browsing, thumbnails, richer document actions and larger-provider performance/visual/accessibility checks. It is a partial implementation of the original module.

The official Desktop Head Unit package is installed in the Android SDK and its binary starts. The dedicated VM has only the `com.google.android.projection.gearhead` stub (`1.2.542030-stub`), so no projection handshake or host acceptance was verified. Android's [Desktop Head Unit guidance](https://developer.android.com/training/cars/testing/dhu) requires a full updated Android Auto app and initial Google Play sign-in on Android 10+ before a DHU session can be established. Retest when those prerequisites are available on the dedicated VM; do not use the user's personal emulator.
