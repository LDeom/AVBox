# JS danmaku runtime milestone B1 (2026-11-23)

## Implemented
- Added `JsDanmuPluginRuntime`, a dedicated QuickJS context per plugin.
- Plugin source is evaluated through a CommonJS `module.exports` / `exports` wrapper.
- Runtime exposes no crawler `Global` or `local` object; no Cookie, cloud-drive, filesystem, or HTTP host methods are bound.
- The first contract accepts an object export with optional `manifest` / `meta` and at least one of `search(arg)` or `load(arg)`.
- Added bounded JSON array result parsing (5,000 items) and a 5-second caller wait deadline.
- Added canonical-path and file-size checks when retrieving an enabled plugin from private storage.
- Added a settings-page “运行检查” action to initialize a plugin and display its manifest metadata/error.

## Compatibility boundary
This is a QuickJS-based JavaScript runtime, not a full Node.js runtime. CommonJS entry-file syntax is supported by the wrapper, but `require`, Node built-ins (`fs`, `path`, `crypto`, `http`, `https`), native addons, `process`, and Node event-loop behavior are not implemented. Promise-returning methods are explicitly rejected for now. Plugins that depend on these APIs will not yet work.

## Important timeout limitation
The 5-second deadline bounds how long the Android caller waits. QuickJS native execution may not honor Java thread interruption; therefore this must not be described as a hard CPU execution sandbox. Before enabling untrusted remote plugins, add engine-level interrupt/termination support or run the engine in a killable isolated process, plus memory limits.

## Not yet connected
- No HTTP bridge or Node compatibility shims.
- No conversion of plugin JSON danmaku records into the renderer's parser/model.
- No automatic invocation from playback or episode changes.
- No compatibility claim for arbitrary third-party Node.js danmaku plugins.

## Validation
Source-level review only. No APK build, unit test run, release, or device test was performed, in accordance with the project instruction to defer builds until the planned optimization work is complete.
