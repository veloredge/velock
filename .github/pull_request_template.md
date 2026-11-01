## What this changes

<!-- One or two sentences. Link the issue it closes, if any. -->

## How it was verified

<!-- Device, Android version, and the exact steps you ran. "It builds" is not verification. -->

- [ ] Built with `./gradlew :app:assembleDebug`
- [ ] Installed on a physical device and walked through freeze, wake, unlock and remove
- [ ] `adb shell dumpsys window windows | grep VeLock` is empty after removing the lock

## Checklist

- [ ] No new dependencies and no new permissions
- [ ] Nothing keeps the CPU awake or polls in the background
- [ ] The Quick Settings tile and the notification can still stop the lock
- [ ] Comments explain the why; no dead code, debug prints or TODOs left behind
