# Playback API, route switching and failure-diagnosis audit — 2026-11-23

Branch: `feature/tablet-live-immersive`

## Existing paths confirmed

### Parse-service route
- `PlayerActionsDelegate.onParseSelected` validates the selected index against `ApiConfig.get().parseBeanList`.
- The selected `ParseBean` is persisted with `setDefaultParse`, the UI list version is refreshed, and `changeParse(parseBean)` is dispatched through the player listener.
- This is parse-service switching, not player-engine or VOD source-line switching.

### Player-engine route
- Short press cycles through `PlayerCatalog.all()`.
- Long press presents the available engine catalog and explicit selection.
- `PlayerConfigDelegate.applyPlayer` updates the `pl` config with auto-switch suppressed and calls `replay(false)`.
- The route operation is implemented in the active player controller path; `PlayerSwitchUseCase.switchPlayer()` is a no-op placeholder returning true and should not be treated as the implementation. No direct Kotlin call site was found in the inspected source paths.

### VOD source/episode line
- The active selection is stored as VOD playFlag/playIndex.
- `PlaybackController.beginSourceSwitch` stops parse, cancels timeout, marks source-switch state, and stores progress inheritance before clearing the current URL/headers.
- `PlaybackStarter.play` resolves the current series, advances resolver generation, resets per-attempt state, and migrates/inherits progress around the switch.
- User-picked line state is tracked separately from automatic retry state.

### Failure diagnosis
- Resolve and switch-line timeouts are managed separately.
- Playback errors preserve HTTP status and expose decode/bit-depth diagnostics.
- Retry paths distinguish pre-start and post-start failures and include audio-track, decode, ISO bandwidth, and malformed-manifest recovery.
- Logs exist, but route/parse identity and failure reason are not yet presented through one consistent, redacted diagnostic record.

## Remaining implementation work
1. Unify a redacted route-attempt diagnostic record across parse selection, engine switching, VOD line switching, and retry outcomes.
2. Ensure manual user choices are not silently overridden by automatic line retry; preserve selected line/parse on failure and provide explicit recovery feedback.
3. Review cancellation/generation boundaries for stale resolver/parse results after rapid route changes.
4. Add source-level tests for route-state transitions, timeout cancellation, and progress inheritance.
5. Review the no-op `PlayerSwitchUseCase.switchPlayer()` placeholder and remove or correct it only after confirming any reflection/legacy API contract.

## Validation
This is a source audit, not a build or runtime verification. No APK, Gradle task, or device test was run.
