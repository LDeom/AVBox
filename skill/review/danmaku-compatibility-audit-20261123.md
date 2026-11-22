# Phase 6 — Danmaku source audit

- Danmaku loading/settings are centralized in `DanmuLoadController`; XML/HTTP decoding and rendering conversion are handled by `DanmakuXmlParser`.
- The loader synchronizes prepare/start, retries placeholder HTTP sources and cancels stale requests with sequence tokens.
- The parser reads HTTP response bytes and supports XML plus gzip/deflate normalization, and Bilibili-style XML item conversion.
- `DanmakuApi` supports built-in searches and remote API templates with cancellation.

## Privacy fix
The loader previously logged full HTTP/file source strings for danmaku, potentially exposing URL paths or query tokens. It now logs only the HTTP host (or a generic file source / inline XML length), not URL paths, query strings or inline content.

## Remaining compatibility review
- Direct renderer JSON formats are not yet established; API conversion is separate from renderer input.
- Check HTTP response status before accepting bodies and expand supported XML/JSON fixtures.
- Review special-motion item parsing and malformed/oversized input handling.
- SSL fallback currently uses a trust-all client after SSL errors; this compatibility behavior is security-sensitive and needs a separate decision.

Source-only review; no build, APK, release or device validation.
