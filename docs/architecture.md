# Architecture

How NightTorch is put together, and why the awkward parts are the way they are.

This is the document the code comments point at. If you are changing behaviour rather than
fixing a typo, the sections below are the ones worth reading first — several describe
decisions that look wrong until you know what was measured.

## Shape

One Gradle module. Multi-module is unjustified at this size.

```
com.bordware.nighttorch/
├── NightTorchApp.kt          Application; owns AppContainer
├── di/AppContainer.kt        hand-rolled singletons
├── torch/
│   ├── TorchController.kt    the only thing that touches CameraManager
│   ├── TorchCapability.kt    Unsupported | BinaryOnly | Variable(max, default)
│   ├── TorchState.kt         observable snapshot
│   └── TorchHardwareResolver.kt   pure decisions, no Android types
├── schedule/
│   ├── NightScheduleEvaluator.kt  is it night? pure
│   └── BrightnessResolver.kt      time + settings + capability -> level; pure
├── data/
│   ├── AppSettings.kt        immutable, with DEFAULT
│   └── SettingsRepository.kt DataStore wrapper
├── service/
│   ├── FlashlightAccessibilityService.kt
│   ├── VolumeComboDetector.kt     pure state machine
│   ├── AccessibilityStatusMonitor.kt
│   └── HapticFeedback.kt
└── ui/                       Compose; state hoisted to ViewModels
```

The organising principle: **anything genuinely tricky is pure Kotlin with no Android
imports**, so it can be unit tested in milliseconds instead of on a device. The combo
detector, the schedule evaluator, the brightness resolver and the hardware resolver all
follow that rule. Everything else is thin glue.

## Permissions

Three declared, and two conspicuously absent.

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | The only way to see volume keys while locked |
| `MODIFY_AUDIO_SETTINGS` | Undo the volume step the shortcut causes |
| `VIBRATE` | Confirmation buzz |

**No `CAMERA`.** `setTorchMode` and `turnOnTorchWithStrengthLevel` deliberately sit outside
the camera permission model, precisely so torch utilities do not need camera access.
Declaring it would trigger a runtime prompt users are right to distrust and put "Camera" in
the store listing, for no functional gain.

**No `INTERNET`.** This is the app's central claim, and it is deliberately
machine-checkable rather than a promise: without the permission Android will not permit a
network connection at all. CI inspects the **merged** manifest — not ours, because a
dependency can contribute a permission nobody wrote — and fails the build if either
`INTERNET` or `CAMERA` appears.

## The volume-key problem

This is the hardest part of the app, and the reason it exists at all.

`AccessibilityService.onKeyEvent` must answer **synchronously**: return `true` to swallow the
event, `false` to pass it on. When `VOLUME_UP` goes down you cannot know whether
`VOLUME_DOWN` will follow 15 ms later. You have to decide now.

- Swallow it, and single volume presses stop working. Unacceptable.
- Pass it through, and a completed combo has already nudged the volume.

There is no third option. NightTorch passes the first key through and **restores the volume
afterwards**, degrading gracefully to "the volume moved one step" if the write is refused.

Two rules were learned by measuring, not from documentation:

**A key whose press was passed through must have its release passed through too.** Swallowing
it leaves the system believing the key is still held, and it keeps applying that key's
effect. Measured on a Pixel 10 Pro, consuming the first key's `ACTION_UP` ran the media
volume to maximum and left it there. Only keys the system never saw pressed may have their
release consumed.

**The restore must be scheduled after the gesture ends**, not when the combo fires. The
system applies the passed-through key asynchronously; restoring at trigger time writes back a
value that has not changed yet, and the system's own change lands on top.

## Combo detection

`VolumeComboDetector` is a pure state machine over event primitives — no clock, no I/O, no
Android imports. It declares its own copies of the four `KeyEvent` constants it needs, and a
unit test asserts they equal the platform values so they cannot drift.

The contract:

- Anything other than the two volume keys is ignored outright, **including while a gesture is
  armed** — swallowing the power key would trap the user.
- A press fires the combo when the other volume key is **still held** and its press landed
  within 150 ms.
- Once fired, further volume events are swallowed until both keys are released, so one
  gesture produces exactly one toggle.
- `repeatCount > 0` never fires the combo, so holding one key cannot trigger it.

Requiring the partner key to be *still held* is stricter than "pressed recently", and
deliberately so: measured single presses last 113–162 ms while a real combo has a 10–18 ms
gap, so a user correcting an overshoot — nudge up, release, nudge down — fits inside 150 ms
comfortably and would otherwise light the torch by accident.

A 15-second watchdog covers a key-up that never arrives. Without it, a lost release would
leave the detector swallowing volume events forever, which is the dead-volume-keys outcome
the whole design exists to avoid.

## Night schedule

`NightScheduleEvaluator` exists as a separate class for one reason: the midnight wrap. A
naive `now >= start && now <= end` is wrong for every sensible night window, because night
windows cross midnight by definition.

The window is **half-open** — it includes the start and excludes the end — so adjacent
windows tile without overlapping and 06:00 is unambiguously morning. Three cases:

- `start == end` — zero length. Defined as **never night**, not always. Both readings are
  defensible, but "never" is the safe one: a user who sets both ends the same gets an
  ordinary torch rather than one permanently stuck at 1%, which would look like a hardware
  fault and be undiagnosable from the UI.
- `start < end` — an ordinary same-day window: `now >= start && now < end`.
- `start > end` — wraps past midnight: `now >= start || now < end`.

Times are persisted as minutes since midnight rather than formatted strings, so stored data
does not depend on locale or formatter patterns. The stored window is whole minutes, but
`LocalTime.now()` carries seconds, so the comparison that actually runs is sub-minute — and
is tested at that precision.

## Torch capability

Three cases, and conflating them is the classic bug in this category:

- **`Unsupported`** — no camera reports a flash unit.
- **`BinaryOnly`** — on/off only. Reached below API 33, *and* on API 33+ where the device
  reports a maximum strength of `null` or `1`.
- **`Variable(max, default)`** — API 33+ with a real range.

**Variable brightness is not implied by the Android version.** Plenty of devices on Android
13+ report a maximum flash level of 1. Treating those as variable is the mistake to avoid,
and it is why capability detection lives in `TorchHardwareResolver` as a pure function — the
development device reports 21 levels, so that case is untestable on it and testable there.

Torch state is driven entirely by `CameraManager.TorchCallback`, never by assuming a call
succeeded, so the UI stays correct when the torch is changed by the Quick Settings tile or
another app. An instrumented test enforces this by changing the torch through a separate
`CameraManager` and asserting the state follows.

Levels run `1..max`. **Level 0 is not "off", it is invalid**, and is clamped up everywhere.

Preferences are stored as **percentages**, because `maxLevel` varies enormously between
devices and a stored raw level would mean a different brightness on different hardware. The
UI, however, snaps to whole levels: a 21-level device turns a 0–100% range into 101 positions
with only 21 distinct outcomes, so most drag steps would change the stored value without
changing the light.

## Settings on the key-event path

`onKeyEvent` runs on the main thread and must answer synchronously, so it **cannot touch
DataStore**. `runBlocking { dataStore.data.first() }` there is disk I/O on the main thread and
will ANR on a slow device.

Instead the service collects settings into a `@Volatile` field in `onServiceConnected`, and
`onKeyEvent` reads that field — effectively free.

Setup belongs in `onServiceConnected`, not `onCreate`: the system kills and restarts the
service whenever the user toggles it, so it must hold no unrecoverable state.

## Accessibility service configuration

Key filtering needs **both** of these. Either alone silently delivers no key events at all,
with no error anywhere to explain why:

```xml
android:accessibilityFlags="flagRequestFilterKeyEvents"
android:canRequestFilterKeyEvents="true"
```

`accessibilityEventTypes` is deliberately narrow rather than `typeAllMask`. The service reads
no screen content and needs only key events; requesting every event type would be a privacy
liability and a constant battery cost for no benefit. `onAccessibilityEvent` is empty.

There is no foreground notification, because an accessibility service does not need one.

## Detecting whether the service is enabled

Read `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` directly rather than calling
`AccessibilityManager.getEnabledAccessibilityServiceList()`, which has historically returned
stale results on some OEM builds. Observe it with a `ContentObserver` so the UI updates the
moment the user flips the switch, and re-read on `ON_RESUME` to cover process death while
they were away in Settings.

Parse each entry with `ComponentName.unflattenFromString` rather than matching substrings.
Both flattened forms occur in the wild — `pkg/com.pkg.Svc` and the short `pkg/.Svc` — and a
naive `contains` check on the long form reports "disabled" for a service the user has
actually enabled.

Deep-linking to this app's own entry in Settings is undocumented and OEM-dependent, so it is
not attempted. The plain list always works.

## Restricted settings

Apps installed from outside an app store are blocked from enabling accessibility services
until the user allows it via **App info → ⋮ → Allow restricted settings**. Onboarding detects
this by counting departures to Settings — two failed attempts, not one, since a single
failure is an ordinary change of mind — and shows the workaround inline.

## Dependency injection

No Hilt. `AccessibilityService` is not supported by `@AndroidEntryPoint`, so injecting into
the single most important class in the app would need `EntryPointAccessors` boilerplate
anyway. `AppContainer` on the `Application` is smaller than the setup Hilt would require.

Sharing `TorchController` is a correctness requirement, not a convenience: the service and
the UI must observe the same `TorchState`, and each controller registers its own system
callback.

## Minimum SDK

**minSdk 26.** `java.time.LocalTime` is available natively from API 26; targeting 24 would
mean core library desugaring for a single API.

Note that `registerTorchCallback(Executor, TorchCallback)` is **API 28** — above our minimum
— so the `(TorchCallback, Handler)` overload is used instead. API levels here were checked
against `api-versions.xml` rather than recalled.

## Testing

Pure logic is unit tested exhaustively on the JVM: the midnight-wrap boundary down to
nanosecond precision, the combo state machine including timing edges, capability mapping for
devices we do not own, and percentage/level round-tripping.

Compose tests cover the four cards' state transitions, including states a working device
cannot produce — no flash unit, camera busy, binary-only hardware.

**One thing is deliberately not automated.** The key-event path cannot be tested by machine:
`adb shell input keyevent` does not reach the accessibility filter, so injected events are
invisible to the service. An emulator test would pass while testing nothing. Verifying the
shortcut therefore means pressing real buttons on a real phone.

Instrumented tests need the device **unlocked and awake**; a dozing device never foregrounds
the host activity, which surfaces as a misleading "No compose hierarchies found".

## Measured device behaviour

[`device-matrix.md`](device-matrix.md) records what was actually observed on hardware —
screen-off key delivery, combo timings, the absence of key repeats, and flash strength
levels. Several decisions above only make sense in light of it, and contributions from
non-Pixel devices are especially welcome.
