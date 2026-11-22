# Danmaku source selector — 2026-11-23

Branch: `feature/tablet-live-immersive`

## Behavior
- Added a persistent source selector to the player's danmaku settings sheet:
  - `视频源自带` (default)
  - `JS 插件源`
- The selection is stored independently from visual danmaku preferences.
- In video-source mode, the player uses only the danmaku URL supplied by the selected video source. It does not silently fall back to JS plugins.
- In JS-plugin mode, the player ignores the video source's danmaku URL and searches enabled JS plugins for the current title and episode.
- Changing the selector re-requests the current episode's danmaku source so the selection takes effect immediately.
- Existing request IDs and coroutine cancellation continue to prevent stale JS results from replacing a newer episode's danmaku.

## Compatibility and fallback
- Default remains the video source's built-in danmaku behavior, preserving existing users' settings.
- If JS-plugin mode is selected but no enabled plugin produces an unambiguous result, danmaku remains empty; it does not silently switch to the video source. This keeps the user's explicit source choice predictable.
- Manual danmaku search remains available as a separate action.

## Validation
- Source-level review only.
- No Gradle build, APK, unit test, or device test was run, per instruction to defer builds until all seven workstreams are complete.
