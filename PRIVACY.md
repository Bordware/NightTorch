# Privacy Policy

**NightTorch collects no data. None at all.**

Last updated: 15 August 2026

## What is collected

Nothing. No personal data, no usage analytics, no crash reports, no device identifiers, no
advertising identifiers. There is no account, no sign-in, and no server.

## What is stored, and where

Your settings — night hours, brightness levels, and whether you have finished the
introduction — are written to a file in the app's private storage on your own device. No
other app can read it. Uninstalling NightTorch deletes it.

Nothing is stored anywhere else, because there is nowhere else.

## Why you can verify this rather than trust it

NightTorch does not request the `INTERNET` permission. Without it, Android will not let the
app open a network connection at all — this is enforced by the operating system, not by the
app's good behaviour.

You can check this yourself:

- On your phone: **Settings → Apps → NightTorch → Permissions**.
- In the source: [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) lists every
  permission the app declares.
- In CI: the build fails if `android.permission.INTERNET` ever appears in the merged
  manifest, so it cannot be added by accident or by a dependency.

There are no analytics, crash-reporting or advertising libraries in the dependency list
either. That list is [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## The accessibility permission

This is the permission that reasonably makes people uneasy, so here is exactly what it does.

To notice both volume keys being pressed while your phone is locked or another app is open,
an Android app must run an accessibility service. There is no other API for it.

When you enable it, Android shows a warning saying NightTorch can "view and control your
screen". **That warning is the same for every accessibility service** and cannot be changed
by the app. What matters is what the app actually asks for:

- NightTorch declares **`canRequestFilterKeyEvents`** — key events only.
- It requests **no** capability to read window content.
- Its `onAccessibilityEvent` handler is **empty**, and its event subscription is the
  narrowest workable, not `typeAllMask`.
- The keys it inspects are volume up and volume down. Everything else is ignored and passed
  straight through.

The whole service is around 200 lines:
[`FlashlightAccessibilityService.kt`](app/src/main/java/com/bordware/nighttorch/service/FlashlightAccessibilityService.kt).

Nothing the service sees is stored, logged to disk, or transmitted.

## The other two permissions

- **`MODIFY_AUDIO_SETTINGS`** — the first key of the shortcut unavoidably reaches the
  system and nudges the media volume by one step. This permission is used to put it back,
  and for nothing else.
- **`VIBRATE`** — the short buzz confirming the shortcut fired. NightTorch also respects
  your system-wide haptic feedback setting, so if you have haptics off device-wide, it stays
  silent.

## Children

The app collects no data from anyone, including children.

## Changes

Material changes to this policy will appear in [CHANGELOG.md](CHANGELOG.md) and in the
commit history of this file.

## Contact

Open an issue at <https://github.com/bordware/NightTorch/issues>.
