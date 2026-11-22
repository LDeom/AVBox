# JS danmaku plugin bridge security and interface notes (2026-11-23)

## Current implementation status
The Settings entry and plugin file registry are present. Import is storage/management only; imported source is not executed. Playback does not yet invoke plugins.

## Critical runtime finding
The existing crawler `Global` bridge is not safe to expose wholesale to third-party danmaku plugins. It includes host capabilities such as WebView cookie read/write, proxy URL generation, HTTP access and session cookie operations. Reusing the entire bridge would grant a danmaku plugin access beyond its purpose and could expose login state.

The new runtime must use a dedicated, minimal bridge. Do not bind `Global`, `local()`, cloud credential stores, or crawler-wide cookie APIs directly into third-party plugin contexts.

## Proposed plugin contract (initial, versioned)
Use a small manifest header or sidecar metadata:
- `id`: stable plugin identifier
- `name`: display name
- `version`: plugin version
- `apiVersion`: AVBox danmaku plugin contract version
- optional `description`

Expose one documented entry point:
- `module.exports = { search(query), load(result) }` for CommonJS-style modules, with both methods allowed to return values or Promises.
- `search` returns an array of candidate matches with title, episode label, and opaque plugin-owned id.
- `load` returns normalized danmaku items: `{ time, mode, color, text }`. Time is seconds; mode maps to scroll/top/bottom; color is integer RGB. AVBox validates every item and drops malformed/out-of-range records.
- Plugins may optionally implement `init(context)` and `destroy()`.

This contract is an AVBox proposal, not a claim of compatibility with arbitrary existing Node.js plugins. Before broad compatibility, collect representative plugin samples and map their actual exported interfaces.

## Runtime capability policy
- QuickJS remains the embedded engine; it is not Node.js and does not provide Node native modules.
- Provide a CommonJS loader shim only for explicitly supported bundled modules.
- Expose a narrow HTTP request capability with host allowlist/redirect checks, response size limits, timeout and cancellation. No cookie jar by default.
- Do not expose `process`, unrestricted `fs`, `child_process`, Android Context, app-private paths, WebView cookies, cloud credentials or arbitrary reflection.
- Each plugin receives a dedicated execution session and bounded executor; enforce execution deadlines, cancellation on media change, response/item limits and deterministic teardown.
- Keep logs to plugin id, operation, duration and sanitized error category; never log full URLs, headers, cookies or response bodies.

## Follow-up implementation
1. Add contract models and validation.
2. Build a QuickJS adapter with minimal CommonJS exports/module shim and dedicated safe bridge.
3. Add contract tests using a tiny in-repo JS fixture (not third-party proprietary source).
4. Add plugin diagnostics and explicit unsupported-API reporting.
5. Wire plugin invocation into DanmuLoadController after the result normalization path is ready.

## Validation constraints
No APK build, release, or device testing until the overall requested optimization work is complete.
