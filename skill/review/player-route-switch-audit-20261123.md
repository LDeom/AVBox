# Phase 5 — Manual playback route-switch audit

Branch: `feature/tablet-live-immersive`

## Confirmed current behavior
- The parse selection row in `PlayerBottomBar` reads `ApiConfig.get().parseBeanList`, persists the selected parse via `setDefaultParse`, and invokes `changeParse(parseBean)`.
- `PlayerActionsDelegate.onParseSelected` routes the selection to the controller listener and hides controls.
- `PlaybackController.requestParse(ParseBean)` resets retry state and delegates to `doParse`; this is parse-service switching, not playback-engine switching.
- `PlaybackController` already tracks current VOD/episode selection, progress key, user-picked-line state, and line-switch progress migration.
- `PlayerActionsDelegate.onPlayerClicked/onPlayerLongClicked` provide existing player-engine cycling and explicit selection.
- `PlayerSwitchUseCase.switchPlayer()` is a placeholder that currently returns `true` without changing engine or playback.

## Gap / risk
The code currently exposes distinct concepts (parse-service, playback engine, episode/source selection) but a unified manual route switch operation is not established. Implementing an arbitrary “switch route” UI without defining which of these concepts it targets risks triggering duplicate state transitions or resetting the playback session.

## Implementation direction
1. Reuse existing parse-service selection for parse routes; avoid a duplicate sheet.
2. For player-engine switching, wire the actual engine catalog/config path and preserve current position before switching, rather than using the placeholder.
3. For VOD/source line switching, use existing episode/series map selection and scheduler pipeline; capture current position and key before mutation, explicitly set user-choice state, and avoid retry ladder overriding the choice.
4. Provide explicit failure feedback and log a redacted route/engine identifier only.
5. Add unit/source-level coverage for state transitions and progress inheritance. Device validation remains intentionally deferred.

## Validation
Source audit only. No APK build, Gradle task, release, or device test was performed.
