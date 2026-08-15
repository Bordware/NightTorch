# Device matrix

Results of the Phase 1 hardware/OS capability spike (PLAN.md §5, Phase 1).

**Status: complete for Pixel 10 Pro (Android 17 / API 37). All three questions
answered; screen-off delivery works, so no design branch was triggered.**

## The three questions

1. **Variable torch strength** — does the device report
   `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` > 1?
2. **Screen-off key delivery** — are volume key events delivered to an accessibility
   service while the display is off? *Widely OEM-dependent.*
3. **Lock-screen key delivery** — are they delivered on the lock screen with the
   display on?

## How to run the spike

```bash
# 1. Install
./gradlew installDebug

# 2. Watch the log (leave this running in its own terminal)
adb logcat -c && adb logcat -s SPIKE
# Windows PowerShell 5.1 has no `&&` operator — use `;` there:
#   adb logcat -c; adb logcat -s SPIKE

# 3. Q1: open the app, tap "Probe camera flash". Results print to the log and on screen.

# 4. Enable the service: app's "Open accessibility settings" button ->
#    "NightTorch key event spike" -> On.
#    If the toggle is greyed out, see "Restricted settings" below.

# 5. Q3: lock the phone (display stays on), press volume keys. Check the log.

# 6. Q2: lock the phone and let the display turn off, press volume keys.
#    Check the log AFTER waking, since logcat keeps buffering.
```

Every `onKeyEvent` line reports `display=` and `keyguardLocked=`, so which scenario a
line belongs to is unambiguous — that is the point of logging both.

The spike consumes nothing (`onKeyEvent` always returns `false`), so volume keys keep
working normally throughout.

### Restricted settings (Android 13+)

Sideloaded builds — including `./gradlew installDebug` and `adb install` — are blocked
from enabling accessibility services until you allow it explicitly:

> **Settings → Apps → NightTorch → ⋮ (top right) → Allow restricted settings**

This is PLAN.md §1.6, and it is expected during development rather than a bug. Phase 6
has to detect and explain this case to real users.

Alternatively, enable it over adb, which bypasses the restricted-settings block
entirely. **Append to the existing list — do not overwrite it**, or you will silently
disable every other accessibility service on the device:

```bash
# Save the current value FIRST so it can be restored.
adb shell settings get secure enabled_accessibility_services

# Then append, keeping everything already there:
adb shell settings put secure enabled_accessibility_services \
  "<previous value>:com.bordware.nighttorch/com.bordware.nighttorch.spike.SpikeAccessibilityService"
```

## Q1 — Torch strength capability

| Device | Android / API | Camera ID | `FLASH_INFO_AVAILABLE` | `LENS_FACING` | Max level | Default level | Capability |
|---|---|---|---|---|---|---|---|
| Pixel 10 Pro | 17 / API 37 | `0` | `true` | BACK | **21** | 21 | `Variable(21)` |
| Pixel 10 Pro | 17 / API 37 | `1` | `false` | FRONT | `null` | `null` | no flash unit |

`cameraIdList` was `[0, 1]`. Measured 2026-08-14.

Two things worth carrying into Phase 2:

- **`FLASH_INFO_STRENGTH_DEFAULT_LEVEL` equals the maximum (21), not a mid-scale
  value.** Do not assume the default sits somewhere in the middle of the range, and do
  not use it as a stand-in for "comfortable indoor brightness" — on this device it is
  simply full power.
- The front camera reports `FLASH_INFO_AVAILABLE=false`, so the "iterate IDs and pick
  the first with a flash unit, preferring `LENS_FACING_BACK`" rule from §4.2 is
  exercised by a real device here rather than being theoretical.

Capability maps as follows (PLAN.md §4.2):

- no flash unit → `Unsupported`
- API < 33 → `BinaryOnly`
- API ≥ 33 and max level is `null` or `1` → `BinaryOnly` — **this is common, do not
  assume API 33 implies variable brightness**
- API ≥ 33 and max level > 1 → `Variable(max)`

## Q2 / Q3 — Key event delivery

| Device | Android / API | Screen ON, unlocked | Screen ON, locked | Screen OFF |
|---|---|---|---|---|
| Pixel 10 Pro | 17 / API 37 | **Yes** | **Yes** | **Yes** |

Measured 2026-08-14 with physical key presses. Reported state per scenario:

| Scenario | `display=` | `keyguardLocked=` | `interactive=` |
|---|---|---|---|
| Screen on, unlocked | `ON` | `false` | `true` |
| Screen on, locked | `ON` | `true` | `true` |
| Screen off | `DOZE` | `true` | `false` |

### The screen is never `STATE_OFF` on this device

With the display asleep, `Display.getState()` returned **`STATE_DOZE`, not `STATE_OFF`** —
this is the always-on display. Any logic gated on `Display.STATE_OFF` would silently
never fire here.

This does not affect the chosen design (§1.2 strategy B does not inspect display state),
but it would have quietly broken §1.2 strategy C, and it is a trap for any later
"only when the screen is off" feature. **`PowerManager.isInteractive` is the reliable
signal; the display state is not.**

### Measured timings — feed these into Phase 4

| Measurement | Value |
|---|---|
| Combo inter-key gap (the two `ACTION_DOWN`s) | **10 ms and 18 ms** across two trials |
| Single press duration, down → up | 113–162 ms |
| Combo total gesture duration | ~190–210 ms |
| Both keys released within | 3–5 ms of each other |

The plan's default 150 ms combo window has an **8–15× margin** over how this device is
actually pressed. Press *duration* (113–162 ms) and combo *gap* (10–18 ms) are cleanly
separated by roughly an order of magnitude, so the window cannot swallow single presses.

The gap is small enough that the window could be tightened considerably without
affecting usability — but there is no reason to, and a generous window is kinder to
users with reduced motor control, which is the accessibility justification the whole
feature rests on. **Keep 150 ms.**

### Held keys produce no repeat events

A 5.3 s hold of `VOLUME_DOWN` delivered **exactly one `ACTION_DOWN` and one `ACTION_UP`,
with `repeatCount=0` throughout**. A separate 2.1 s hold behaved identically. The
system's own volume ramp-on-hold is internal to the platform and is not dispatched to
the accessibility key filter.

Consequences for `VolumeComboDetector` (§4.1):

- The "ignore `repeatCount > 0` for trigger purposes" rule is correct defensive coding
  and should stay, but it is **unexercised on this device** — it cannot be validated by
  manual testing here, only by unit tests. Do not delete it as dead code.
- The detector must not depend on repeats arriving to decide a key is still held. Track
  held state from the DOWN/UP pair only.

### Injected key events cannot be used to test this

`adb shell input keyevent 24/25` produced **no `onKeyEvent` callbacks whatsoever** while
the service was live and physical presses were working. Injected events bypass
accessibility key filtering.

This means the key-event path **cannot be covered by an instrumented test** — there is no
way to synthesise the input. Phase 7 should unit-test `VolumeComboDetector` exhaustively
as pure logic and treat end-to-end key delivery as a manual test step, rather than
attempting an Espresso/UiAutomator test that would silently pass by never firing.

## Decisions taken from these results

Per PLAN.md Phase 1, if screen-off delivery fails, choose (a) screen-on-only or (b) add
a Quick Settings tile as the screen-off path.

**Decision: neither is needed. Screen-off delivery works on the target device**, so the
night-time use case — the entire premise of the app — is viable and Phase 4 proceeds as
written. No README caveat and no Quick Settings tile. Confirmed by a control run with
every other accessibility service disabled (see below).

Caveat: this is a single Pixel data point, and screen-off delivery is the most
OEM-variable behaviour in the whole design. Treat it as "viable on Pixel", not "viable
everywhere", until a second manufacturer is tested. A Quick Settings tile remains the
fallback if a non-Pixel device fails Q2.

## Consuming a key-up strands the system mid-press

Found in Phase 4 on the same device, and worth recording next to the timings because it
only shows up on real hardware.

PLAN.md §4.1 says that once the combo is armed, every subsequent volume event including
**both** key-ups is consumed. Doing exactly that produced this:

```
key=24 (VOL_UP)   DOWN → Ignore             (passed through to the system)
key=25 (VOL_DOWN) DOWN → TriggerAndConsume
key=24 (VOL_UP)   UP   → Consume            ← the system never sees this release
key=25 (VOL_DOWN) UP   → Consume
```

Media volume went from **12 to 25, the maximum, and stayed there**. The system had seen
`VOLUME_UP` pressed and never released, so it kept applying the key's effect.

Note this is consistent with the Phase 1 finding that no repeat events reach the
accessibility filter: the platform's volume ramp-on-hold is internal and timer-driven, so
consuming repeats does not stop it. Only the release does.

**Rule:** a key whose `ACTION_DOWN` was passed through must have its `ACTION_UP` passed
through too. Only keys the system never saw pressed may have their release consumed.

A second consequence: restoring the volume at trigger time does nothing. The system applies
the passed-through key asynchronously, and a device log showed the restore writing back a
value that had not changed yet, with the system's own change landing afterwards. The restore
has to be posted after the gesture ends — 150 ms proved sufficient here.

Verified after the fix: baseline 10, two full combos, still 10.

## Control test — Q2 is not an artefact of other services

The first measurement ran with five other accessibility services enabled. Three of them
filter key events, which is more competition than it first appeared:

| Service | `capabilities` | Filters key events? |
|---|---|---|
| Sleep as Android | 0 | No |
| Bitwarden | 1 | No |
| MacroDroid | 201 | **Yes** (bit 8 set) |
| MacroDroid UI Interaction | 171 | **Yes** (bit 8 set) |
| FlashDim `VolumeButtonService` | 8 | **Yes** |
| NightTorch spike | 8 | Yes |

Competing services cannot manufacture a false *positive*, but they could in principle
have been what kept the input path alive during doze — which would have made the Q2
"yes" an artefact rather than a property of the device.

**Re-ran the screen-off case with the spike as the only enabled accessibility service on
the device.** Every event was delivered, identically to the first run:

```
key=VOLUME_UP   action=DOWN eventTime=2020443314 display=DOZE keyguardLocked=true interactive=false
key=VOLUME_DOWN action=DOWN eventTime=2020443324 display=DOZE keyguardLocked=true interactive=false
key=VOLUME_UP   action=UP   eventTime=2020443520 ...
key=VOLUME_DOWN action=UP   eventTime=2020443523 ...
```

**Q2 therefore holds independently.** Screen-off key delivery is a property of the
device, not a side effect of another app keeping something warm.

The first run also establishes the converse, which matters for real users: several
key-filtering services coexisted without starving each other. Each service receives the
event independently, and one consuming an event only stops it reaching the app beneath —
not sibling services. NightTorch will work on devices that already run FlashDim,
MacroDroid, or similar.
