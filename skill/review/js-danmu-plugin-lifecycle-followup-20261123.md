# JS danmaku player integration — lifecycle follow-up

- Avoided relying on a non-portable Kotlin collection adapter for Android `JSONArray`; candidate objects are now copied with indexed `optJSONObject` access.
- Added explicit cancellation/invalidation of the pending plugin lookup when the playback container is destroyed.
- No APK, Gradle task, unit test, or device test was run.
