# Security policy

## Supported versions

The latest release on the `main` branch receives fixes.

## Reporting a vulnerability

Please report privately through GitHub's
[private vulnerability reporting](https://github.com/veloredge/velock/security/advisories/new)
rather than opening a public issue. Include the device, Android version, and
reproduction steps. You will get a reply as soon as the report is triaged.

## What this app can and cannot do

VeLock holds `SYSTEM_ALERT_WINDOW`, so it can draw above other applications and
receive touches before they do. That is the entire feature, and it is also the
entire risk surface. Things worth knowing before you audit it:

- The overlay is user initiated, one tap away from the Quick Settings tile, and
  can always be stopped from the tile, the settings screen or the notification.
- The app has no `INTERNET` permission. It cannot exfiltrate anything, and it
  collects nothing: no analytics, no identifiers, no logs to disk.
- The only data it stores are two integers: the padlock's screen position.
- The overlay never reads screen content. `ShieldView` draws nothing, and the app
  does not use `MediaProjection`, screenshots or accessibility APIs.
- While the screen is frozen the notification shade and the hardware keys stay
  with the system on purpose. Removing that escape hatch is not a feature.

Reports about any of the following are in scope: overlay windows that survive the
service being stopped, touch events leaking to the app underneath while frozen,
the foreground notification being suppressible by another app, or crashes that
leave windows on screen after the process dies.
