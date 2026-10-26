# IPTV playback type — 2026-10-13

The IPTV screen provides a Playback type button for channels on the current page. Choose a channel, then Follow playlist, Live, or On demand. Favorites retain their existing long-press behavior. A saved choice survives activity recreation and playlist refresh for the same source/channel URL. Selecting Follow playlist restores the current imported EXTINF classification.

Live entries start at the stream default and do not save viewing progress. On-demand entries resume stored progress, including entries whose playlist duration is zero or negative. The shared service resolves this policy from the database rather than trusting controller metadata. Selecting an already-loaded channel again reloads it when the effective playback type has changed. Saving a type does not itself seek or restart an ongoing stream; select the channel again to apply its start policy. The setting cannot make a non-seekable live stream seekable.

Room v6 adds a playbackMode integer with default zero through an explicit v5-to-v6 migration. The original live field retains the imported classification. Refresh preserves the override independently and carries progress forward when both old and refreshed effective types are on demand. Refresh of an effective live entry does not retain old VOD progress. Previous database migrations compose through v6; schema 6 is exported.

App/test builds, 17 JVM tests and all 48 dedicated Android 15 VM tests pass (51.007 seconds), with lint 0 errors/33 warnings. App APK SHA-256: `5e4aebcb1c1a18620d49fa0fd162fbf217dfc642f78062a52f024c2ad5f27f69`. The personal emulator was not used.

Coverage includes the phone channel/type dialogs, recreation and refresh preserving the selection, invalid-mode rejection, restoring the imported default, v5 migration retaining favorites/progress, earlier upgrades reaching v6, actual HLS playback resuming at 9 seconds after an on-demand override, and MP4 playback ignoring a requested 9-second offset after a live override. These are synthetic media fixtures, not proof of real-provider live-edge or seek support.

Remaining work includes Xtream, EPG/XMLTV, series/category navigation, authentication/headers, stream renewal, fullscreen, visual/accessibility validation and real-provider/car-host testing. The full application remains incomplete.
