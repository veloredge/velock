# Changelog

All notable changes to VeLock are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] — 2026-11-02

First public release.

### Added

- Quick Settings tile **Display Lock** that floats a padlock over any app.
- Tap the padlock to freeze the screen: a transparent full-screen layer consumes
  every touch before the app underneath can react.
- The padlock fades to fully transparent three seconds after freezing, so the
  frozen screen stays clean.
- Tapping anywhere on the frozen screen wakes the padlock, which then stays
  visible until it is used. While invisible it can only be woken: a tap or a
  swipe across it never unlocks and never drags it.
- Drag the padlock anywhere; the position survives restarts. Dropping it on the
  bin at the bottom of the screen removes the lock completely.
- Onboarding screen that reports overlay and notification permission state and
  can freeze the screen without the tile.
- Quiet foreground service notification with a "Turn off" action.

### Fixed

- Freezing is rolled back when the padlock cannot be attached above the shield,
  instead of leaving a frozen screen with no way out.
- An invisible padlock can no longer be dragged, so a pocket swipe cannot remove
  the lock and thaw the screen unseen.
- The service is stopped with `stopService` instead of a foreground start that
  can throw on Android 12+ and open an activity from the background.
- Screen metrics are cached per configuration change instead of being queried
  several times per touch move during a drag.
- The padlock is remeasured on configuration changes, so a display-size change
  cannot leave a stale window with mismatched hit geometry.
- Shutdown is idempotent and removes the drag target synchronously.
- The pulse animator is released when the window is detached.
- The "allow overlay" hint survives a warm start (`onNewIntent`), and the
  notification row falls back to the app's notification settings when the
  permission dialog can no longer appear.

### Notes

- No wake locks, no polling, no repeating timers, no analytics, no internet
  permission. The full-screen layer exists only while the screen is frozen.
- `minSdk 28`, `targetSdk 34`, framework APIs only, no third-party dependencies.
- Bundled typefaces: Inter and Space Grotesk (SIL OFL 1.1).
