# JS danmaku player integration — 2026-11-23

Branch: `feature/tablet-live-immersive`

## Changes
- Added a player-side fallback loader for enabled JS danmaku plugins.
- When the existing configured danmaku source is empty, the player supplies video title and current episode to each enabled plugin's `search({title, episode, query})` and calls `load(candidate)`.
- Normalized plugin JSON danmaku records through the existing XML adapter and then passed the result to the existing danmaku renderer/controller.
- Plugin failures are isolated per plugin; the regular player remains usable.
- Multiple search results are selected only when a single best title/episode match can be established. Tied or unidentifiable candidates are skipped rather than guessing.
- Pending plugin work is cancelled/invalidated when another danmaku check or reset occurs.

## Contract
A plugin exports both `search(input)` and `load(candidate)`. Search receives title, episode and a combined query. Load returns an array of records with time/mode/color/text as documented in the plugin bridge review.

## Limitations
- This is a QuickJS-compatible CommonJS subset, not a full Node.js runtime.
- Promise-based functions, `require`, Node built-ins, and plugin-controlled networking remain unsupported.
- The runtime deadline is not a hard execution sandbox; hostile/infinite JS still needs engine-level interruption or process isolation.
- Candidate selection intentionally refuses ambiguous multi-result matches.

## Validation
Source-level change only. No Gradle build, APK, unit test, or device test was run, as requested.
