# JS danmaku plugin result adapter milestone (2026-11-23)

## Implemented
- Added `JsDanmuPluginResultAdapter` to convert plugin JSON records into the XML subset already consumed by `Danmu.fromXml` / `DanmakuXmlParser`.
- Supported normalized record fields: `time` (seconds), `mode` (scroll/top/bottom), `color` (24-bit RGB), optional `size` (8–64), and `text`.
- Validates timestamps (0–24 hours), text length (500 chars), supported modes, RGB range, item count (5,000), and generated XML size (2 MiB).
- XML escaping is applied and XML 1.0 forbidden control characters are dropped.

## Important
This adapter is not yet invoked by `DanmuLoadController`. Runtime calls currently return JSON arrays; the playback integration still needs source selection, matching/search UX, episode-change cancellation, and safe fallback behavior. Do not present this as a fully operational player feature.

## Runtime compatibility remains limited
QuickJS CommonJS entry file only; no Node native modules, require graph, HTTP bridge, Promise handling, or true Node event loop. The caller wait deadline is not a hard execution sandbox because native engine work may ignore Java thread interruption.

## Validation
Source review only. No APK build, test execution, release, or device validation.
