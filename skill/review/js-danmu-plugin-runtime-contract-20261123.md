# JS plugin runtime contract and diagnostics — 2026-11-23

## Runtime contract tightened
The player integration requires both exported functions:
- `search(query)`: returns a JSON array of candidate objects.
- `load(candidate)`: returns a JSON array of danmaku records.

The settings-page run check now rejects a module that cannot satisfy the player contract instead of reporting a partial module as usable.

## Compatibility boundary shown in UI
The plugin page now describes the supported QuickJS/CommonJS subset and explicitly calls out unsupported full-Node features (native modules, dependency-tree `require`, Promise returns).

## Asynchronous execution and diagnostics
- Player invokes plugin work off the UI thread.
- A plugin failure is isolated and logged; later enabled plugins can still be tried.
- Request generations invalidate stale completion; player teardown cancels pending lookup.
- The current 5-second wait deadline is not a hard execution interrupt. A JS infinite loop may keep the runtime worker occupied. Engine interrupt/process isolation and enforceable memory budgets remain open engineering work.

## Validation
Source review only. No build, tests, APK, or device verification.
