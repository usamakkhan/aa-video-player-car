# News module — parser foundation, 2026-10-21

## Publication dates and active-speech verification — 2026-10-23

The reader displays known publication timestamps in the device locale and time zone, omitting unknown dates. The long-reader fixture verifies the displayed timestamp and 1,600-pixel scroll restoration. It now starts actual platform speech before moving the activity to CREATED, then verifies STOPPED status and a disabled Stop button after returning to RESUMED; speech does not automatically restart.

A new shared-service integration test plays a WAV fixture through MediaBrowser, starts actual platform News speech, observes transient-audio-focus suppression, stops speech and verifies media resumes. This tests Android focus and player state, not audible output quality or headphone disconnection.

Final builds, 19 JVM tests and 84 dedicated-VM tests pass (114.002 seconds); lint reports 0 errors/33 warnings. APK SHA-256: 24ed5284d8c5cbf9f4a2b890aee22de6fe517e1763f8141e69ba4f65512d3c9d. Only AA_Player_Test_API_35 / emulator-5556 was used. Source editing, noisy interruption, offline/diverse-engine behavior, real feeds, visual/accessibility and car/hardware validation remain. Older results below are historical.

## Runtime verification — 2026-10-23

The dedicated VM's platform TTS adapter initialized and completed the short utterance, logging `platform_speech_result=READY`. Log evidence is saved under `.tools/testing/news-speech-probe.txt`. This establishes the engine completion callback on this VM, not audible quality, offline synthesis or cross-engine compatibility. No TTS settings were changed.

The long-reader fixture restores a 1,600-pixel scroll offset after activity recreation, and checks that pause/resume leaves the reader's speech state stopped with Stop disabled. It does not yet prove interruption of an actively speaking engine by backgrounding. A prior run failed after resume because Espresso selected the unfocused activity root; all reader-specific assertions now select the dialog explicitly.

Final app/test builds, 19 JVM tests and 83 dedicated-VM tests pass (112.270 seconds); lint 0 errors/33 warnings. App APK is unchanged: `0f7d66006674a4d853d314bb987df6686c917124029b33415837b2fbe99e03f6`. Tests used only `AA_Player_Test_API_35` / `emulator-5556`. Actual shared-player focus interaction, active-speech pause/noisy interruption, audible quality and visual/accessibility/hardware/car validation remain open. Earlier counts and runtime-unknown statements below are historical.

## Read-aloud continuation

The article reader now has Read aloud / Stop controls and speech status. It uses Android TextToSpeech with the device locale and speech audio attributes. Long title/body text is split into bounded chunks without splitting surrogate pairs, with one utterance queued at a time. Stale callbacks are ignored after stop, replacement or close. Engine initialization/language failure, a 15-second initialization delay, rejected speech requests and denied audio focus display an unavailable message.

Speech requests transient audio focus immediately before speaking, stops on focus loss (including duck requests), abandons focus on completion/stop, and stops when audio becomes noisy or MainActivity pauses. Reader dismissal/recreation shuts down its engine and receiver. Returning to the reader does not resume speech automatically. The manifest declares TTS service visibility for modern Android. No background speech service or speech-position persistence is implemented.

Two JVM tests cover chunk boundaries, Unicode, empty input and bounds. Two deterministic device tests cover sequential utterances, stale callbacks, completion, focus loss/denial, engine failure and close-before-initialization. Existing reader recreation tests also exercise creation/cleanup of the platform adapter, but audible synthesis, actual focus interaction with the shared player, noisy broadcasts and diverse engine/language availability still need dedicated runtime checks. The first full run passed the new tests but failed an existing podcast restored-dialog assertion because Espresso selected the unfocused activity root. That assertion now explicitly targets the dialog. Final app/test builds, 19 JVM tests and 81 dedicated-VM tests pass (119.075 seconds); lint 0 errors/33 warnings. APK SHA-256: 0f7d66006674a4d853d314bb987df6686c917124029b33415837b2fbe99e03f6. Only the dedicated VM was used; earlier counts below are historical.

Implementation follows Android's [TextToSpeech API](https://developer.android.com/reference/android/speech/tts/TextToSpeech) and [audio focus guidance](https://developer.android.com/media/optimize/audio-focus). The selected system engine controls synthesis and may require installed voice data or connectivity; no offline-voice guarantee is made. Earlier sections below describe pre-speech stages.

## Source management and reader continuation

News dashboard ID 17 now opens `NewsScreen`. It imports user-entered HTTP(S) RSS/Atom URLs, lists saved sources, refreshes/removes a selected source, and pages articles in batches of 50. Headline search and unread filtering use Room queries. Networking runs on a bounded executor with cancellation and stale-result checks; database work uses the repository worker. Failed fetches/parses/cache-limit checks retain cached articles and restore navigation. All-invalid nonempty feeds cannot erase a cache.

Opening an article marks it read and shows selectable plain text in a scrollable dialog. A missing body has a headline-only explanation. Mark unread updates the database; Open original launches a validated HTTP(S) link only on user action. Source, page, query/filter, unread mode, reader article and scroll offset are saved across recreation. Closing cancels work and dismisses dialogs; a guard prevents duplicate reader opens while a database lookup is pending. Source removal asks for confirmation and cascades cached articles.

The phone fixture imports 51 articles, pages, opens a reader, recreates it, marks unread, filters/searches, closes as read, refreshes while retaining state, rejects malformed XML with cached rows still usable, and removes the source. An initial test selector matched both the deletion title and button; the corrected fixture selects the confirmation button by its Android ID. Final app/test builds, 17 JVM tests and 79 dedicated-VM tests pass (119.573 seconds); lint 0 errors/33 warnings. APK SHA-256: 8297b72ff7c4b1472248375fffee21dce994742cce7a72ae4ed20b966ebcffeb. Only the dedicated VM was used.

Platform TTS is not implemented yet. Next add read-aloud/stop controls, engine/language failure feedback, chunking, audio focus and lifecycle cleanup, with instrumentation. Published-time display, source editing, long-body scroll restoration and broader failure/accessibility/visual/provider checks also remain. The full app goal and other module/car/release gates remain open. Earlier sections below describe intermediate stages.

## Storage continuation

Room v10 adds saved News sources and articles, with an explicit v9-to-v10 migration and exported schema. `NewsDao` provides source pages, date-ordered article pages, title search, an unread filter, read/unread updates, and source deletion with article cleanup. Snapshot replacement preserves read flags for IDs still present, removes entries no longer in the feed, and does not recreate a deleted source on late refresh. This is a current-feed cache, not a permanent archive; an article removed from the feed and later reintroduced has no retained read history.

Refresh is transactional. Duplicate rows, wrong source membership, cross-source ID collisions and cache-limit failures leave the previous snapshot intact. Bounds are 100 sources, 2,000 articles per source, 64,000 characters per body, and a global 8 MiB UTF-8 budget for article titles/bodies/URLs. The text budget excludes the old snapshot being replaced and counts existing SQLite text as UTF-8 bytes, so multibyte text cannot bypass it. Database/index overhead and source metadata are additional. The DAO is not yet wired into the News UI.

New tests cover 51-article paging, read-state retention, search/unread filtering, pruning/deletion, absent parents, transaction rollback, ID collisions, source/article limits and global multibyte-text limits. The v9 upgrade fixture preserves existing IPTV series and episode membership; older fixtures now upgrade through v10. Final app/test builds, 17 JVM tests and 78 dedicated-VM tests pass (115.631 seconds); lint 0 errors/33 warnings. APK SHA-256: 2be3f027cc56e59f3e4cd5e9b9e913ab6d14094602733f4d16558c6bb1f6f855. Earlier parser-stage counts below are historical.

`news/NewsParser.java` introduces RSS 2.0 and Atom article parsing. It is not yet wired to the News dashboard route. Storage was added in the continuation above. Source management, article reading UI, refresh orchestration and platform speech remain next steps; Room is now v10.

The parser accepts up to 4 MiB, 2,000 articles, XML depth 32 and 64,000 characters per body field. It rejects DTD declarations, unresolved entities, malformed/incomplete roots and excessive input. Cancellation is checked during token processing. IDs are scoped to the subscribed source URL, using GUID/Atom ID first, then the article link, then a content-based fallback. Duplicate IDs and empty articles are counted. Relative article links resolve against the final document URL and nested xml:base values, and only HTTP(S) links without embedded credentials are retained.

RSS descriptions and content:encoded fields are reduced from HTML to plain text; content takes precedence over summary. Atom text, HTML and XHTML are handled separately, so literal markup in type=text remains literal. Script/style blocks are excluded from the plain-text body. No WebView, image fetch, script execution or remote Atom content request occurs. Titleless descriptive entries remain readable. Publication dates accept ISO instants and RFC 1123 dates; unsupported dates remain unknown. Two-digit RSS dates, more provider date variants, RSS 1.0/RDF and richer media metadata remain unsupported.

Semantics were checked against the [RSS 2.0 specification](https://www.rssboard.org/rss-specification) and [Atom RFC 4287](https://www.rfc-editor.org/rfc/rfc4287). These references establish format behavior, not real-publisher compatibility.

Four new device tests cover RSS content/summary handling, stable GUID identity, duplicates, Atom text/HTML/XHTML and nested bases, external-content fallback, unsafe links, unknown dates, description-only articles, malformed XML, DTD/entities, input/field/item/depth limits and cancellation. Final app/test builds, 17 JVM tests and 74 dedicated-VM tests pass (113.654 seconds); lint 0 errors/33 warnings. APK SHA-256: c3c66018790e1970e007f2db7bd2ff68d9b5841a849651fe50c2b3f7c7aa7f1d. The personal emulator was untouched.

Next: integrate platform TTS with stop/lifecycle/missing-engine behavior and audio-focus handling. Test these paths on the dedicated VM. Real feeds, accessibility/visual review, other modules and all car-host/release gates remain open.
