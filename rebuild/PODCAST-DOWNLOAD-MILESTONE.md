# Podcast audio-file downloads — 2026-10-13

## Implemented behavior

Hold an episode to download it for offline listening. The Downloads button opens a paged list with saved-byte totals, transfer states, play, pause, resume, retry and removal actions. Downloads default to unmetered networks; the list includes a persisted preference to permit other networks. One transfer runs at a time. Admission is limited to 200 downloads; network reads enforce 1 GiB per episode and a 2 GiB total cache budget, with a 100 MiB real free-space reserve. Download data stays in app-private, non-backed-up storage.

The shared player reads completed downloads from the cache and retains normal streaming for other media. It resolves requests by stored episode identity, preserving listening progress and rejecting a controller-supplied replacement URL. A completed download retains its original enclosure URL/cache key even after a feed refresh changes that URL. Retrying a failed transfer also retains its original request; remove it first to fetch a changed enclosure. Downloads-screen playback re-resolves the selected source while retaining the current position. Unsubscribe triggers orphan-download removal; manager initialization also checks for orphaned records.

## Architecture

`PodcastDownloads` owns a Media3 download index and non-evicting cache, separate from Room v4 subscription/progress storage. No Room migration was needed. `PodcastDownloadService` handles background transfer commands with a dataSync foreground service and platform scheduler. The playback data source reads the same cache without writing streaming media into it. Network/file work and index queries are kept off the screen thread; the downloads list refreshes every two seconds while open.

Implementation follows the [Android Media3 download integration guidance](https://developer.android.com/media/media3/exoplayer/downloading-media). The existing Media3 dependencies supply the download components. Notification/channel and scheduler declarations are in the manifest. The singleton retains only application context; the free-space check deliberately does not reclaim other applications' caches for optional downloads.

## Verification

App and test APK builds pass. JVM tests: 13 passing. Lint: 0 errors/32 warnings. Latest device tests: all 40 pass in 39.679 seconds on `AA_Player_Test_API_35` (`emulator-5556`). The user's personal emulator was not used. Raw output: `.tools/testing/instrumentation.txt`.

New coverage verifies a real HTTP transfer, paused-state persistence through a fresh index instance, pausing a partial transfer and resuming with a nonzero Range request, byte-for-byte cache reads without an upstream source, file removal, oversized response rejection, unsafe/adaptive request rejection, and phone long-press/download/list/removal controls. A playback-service test closes the source server, changes the publisher URL, and verifies actual playback from the saved audio with listening-position resume and controller URI protection. Unsubscription cleanup is verified too.

Testing caught mixed-case MIME classification and a dialog dismissal callback that invalidated the following episode-actions dialog. Both were fixed. The pause test waits for the transfer worker to finish flushing after the persisted STOPPED state before checking cache spans.

App APK SHA-256: `7d915f882fe15ea477fe831b58dd0276a5678b069f75d18abb992c0a36c07dfe`.

The lifecycle follow-up fixes the empty page at exact multiples of 50, clamps pages after removals, restores download dialog/page/scroll state through activity recreation, and preserves scroll during periodic status refreshes. Episode-option dialogs are also saved. The new fixture covers 50/51 records, immutable/bounded pages, removal of the last page, repeated actual activity recreation, retained scroll and explicit dialog dismissal. Confirmed downloads now dispatch directly from the confirmation action using a request prepared off the UI thread; a regression blocks the repository worker and closes/replaces the screen while verifying that the confirmed transfer still completes.

## Remaining gates and limits

### Process restart follow-up — 2026-10-13

The two-phase restart probe passes: seed PID 9949, verification PID 10088. The host force-stops the app between phases and checks that no app PID remains. In the new process, the completed download and paused transfer state persist, partial-cache byte counts match, explicit pause is retained when the download service starts, and the saved episode plays from disk at its stored 9-second position with the source server closed. Cleanup removes both completed and partial cached bytes. The normal suite subsequently passes all 40 tests in 40.131 seconds; JVM tests remain 13 passing and lint remains 0 errors/32 warnings.

Run `Test-AndroidVm.ps1 -RunRestartProbe` to reproduce. Logs: `.tools/testing/restart-seed.txt` and `restart-verify.txt`. Test APK SHA-256: `b3d838ca589910c72f5c30e3a5f53c2ffc77dd2a1a1b98d85222859a70638537`; app APK remains the hash above. The custom test runner delegates ordinary test runs to AndroidJUnitRunner. This proves persisted completed/explicitly paused state and offline resume across process replacement; the probe explicitly starts the download service during verification. It does not establish interruption during an active write, automatic restart after force-stop, network recovery, or reboot recovery.

This milestone downloads progressive audio files. HLS/DASH/adaptive downloads are explicitly rejected; their offline track/manifest support remains to implement. Abrupt process death during an active transfer, automatic transfer recovery, emulator/device reboot, network loss/recovery, metered transitions, scheduler restrictions and Android 15 foreground-service timeouts still need runtime tests. Android 13+ notification permission UX and drawer visibility remain to be implemented/verified; foreground service execution is not proof of drawer visibility.

Full storage-cap stress tests, cache-loss recovery, failed-download retry after publisher changes, rapid enqueue/remove/unsubscribe races, visual/accessibility review and physical-device behavior remain. Episode-option restoration is implemented but has not yet received its own recreation test. These are open follow-up items, not claims of complete download parity.

Podcast scheduled/conditional refresh, richer details/artwork, speed/sleep controls, import/export and per-feed media-browser invalidation remain. Other feature modules and Android Auto/DHU/physical-car gates remain open. The full app goal remains active.
