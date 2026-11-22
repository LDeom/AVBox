# JS danmaku fallback ordering correction — 2026-11-23

The initial integration started the JS fallback at the same time as the built-in/remote DanmakuApi search. That could waste JS execution and allow the two results to race.

Correction:
- Do not start an empty danmaku load before the configured DanmakuApi search.
- If DanmakuApi is unavailable, start the JS fallback directly.
- If DanmakuApi is available, wait for its search result; a returned URL is used normally, and an empty result triggers the JS fallback through the existing player bridge.
- Explicit danmaku supplied by the source remains higher priority and bypasses JS fallback.

Source-only correction; no APK build, Gradle test, or device validation.
