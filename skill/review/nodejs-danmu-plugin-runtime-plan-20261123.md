# Node.js-style JavaScript danmaku plugin runtime audit (2026-11-23)

## User requirement
Provide a runtime for JavaScript danmaku plugins written for a Node.js-style environment, plus a plugin entry and management UI in AVBox Settings. Do not build APKs or perform device validation until the feature sequence is completed.

## Existing infrastructure discovery
- The app already bundles QuickJS (:quickjs) and uses it in com.github.catvod.crawler.js.JsSpider.
- JsSpider already creates a QuickJS context, binds host functions, loads modules, provides console, async execution and timeout handling, and integrates crawler module loading. This is a useful starting point; avoid introducing a second JS engine or duplicating this executor/context lifecycle.
- Existing TVBox/Drpy spider runtime is coupled to VOD spider lifecycle and API calls (Spider, JsLoader, JsSpider). It is not yet a standalone danmaku-plugin contract and must not be blindly reused as if it were.
- Existing DanmuLoadController expects a resolved danmaku payload and DanmakuXmlParser parses XML-oriented input. A JS plugin bridge needs a clearly defined adapter that converts the plugin result to the current parser format or supported data model.

## Proposed architecture
1. Plugin repository/registry: persist plugin metadata, file location, enabled state, id and version. Import JS via Android document picker, copy into app-private plugin storage, validate size and basic syntax/metadata. Do not execute imported source merely to inspect it.
2. Runtime adapter: isolate each plugin execution in a QuickJS context/session on a dedicated executor. Reuse the QuickJS and host-bridge infrastructure where appropriate, but separate lifecycle and APIs from VOD spiders. Provide deterministic destroy/cancel and execution timeouts.
3. Compatibility bridge: progressively implement only documented APIs required by target plugins (CommonJS module/exports, require for bundled modules, console, Promise, selected Buffer/util/http helpers). Node native modules, filesystem, process, and child-process APIs are not implicitly available. Keep permissions scoped and network access through host-controlled HTTP interfaces.
4. Danmaku contract: formalize plugin interface and result schema before wiring playback. Normalize plugin outputs to AVBox's internal danmu representation; support search/resolve workflow only when plugin interface requires it. Avoid assumptions about a specific third-party API.
5. Settings UI: add a dedicated JS danmaku plugin settings page with import, enable/disable, remove, detail/status, diagnostics and runtime reset. Avoid mixing management into unrelated cloud-drive/config settings.
6. Playback integration: expose plugin selection/invocation to existing danmaku flow, handle cancellation when episode/video changes, prevent stale results and report failures without crashing playback.
7. Diagnostics and security: redact secrets and full URLs; cap source size and returned data, enforce timeouts, avoid unrestricted host filesystem/credential access, and document unsupported Node APIs.

## Implementation order
- Milestone A: define plugin manifest/interface, storage model, file import and lifecycle tests/diagnostics.
- Milestone B: runtime wrapper and CommonJS module shim, basic compatibility tests.
- Milestone C: Settings entry and management UI.
- Milestone D: playback integration and normalized results.
- Milestone E: expand compatibility using real plugin samples, static review and source-level tests.

## Validation restrictions
No APK build, release, or device validation. Source-level checks and review only when the work reaches a suitable test milestone. Avoid running tests for small changes as repo rules require.