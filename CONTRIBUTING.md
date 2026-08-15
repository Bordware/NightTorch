# Contributing

Thanks for looking. This is a small app with a narrow purpose, and the most valuable
contribution is probably not code.

## The most useful thing: device reports

Screen-off key delivery to accessibility services is **the most manufacturer-dependent part
of this design**, and it has only been verified on one device. If you can tell us whether the
shortcut works on yours, that is genuinely more useful than most patches.

Open an issue with:

- Device and Android version
- Whether both volume keys toggle the torch with the **screen off**
- Whether it works on the **lock screen** with the screen on
- What the app's brightness slider shows — a level count, or the binary-only message

[docs/device-matrix.md](docs/device-matrix.md) explains how to gather this properly if you
want to be thorough, but a one-line answer is welcome too.

## Hard constraints

Three rules are not up for negotiation, because the app's entire claim rests on them.

1. **Never add `android.permission.INTERNET`**, or any networking, analytics, crash
   reporting or telemetry dependency. The absence of INTERNET is a verifiable product claim,
   and CI fails the build if it appears in the merged manifest.
2. **Never add `android.permission.CAMERA`.** Torch control does not require it.
   `setTorchMode` and `turnOnTorchWithStrengthLevel` sit outside the camera permission model
   deliberately.
3. **No blocking I/O in `onKeyEvent`.** It runs on the main thread and must answer
   synchronously. Settings are read from a `@Volatile` snapshot, never from disk.

[docs/architecture.md](docs/architecture.md) covers the rest, including several API-level
facts that were measured rather than assumed and should not be "corrected" from memory —
`registerTorchCallback(Executor, TorchCallback)` being API 28 while minSdk is 26, for one.

## Before opening a pull request

```bash
./gradlew build test lint
```

All three must pass. If you touch the pure logic — the combo detector, the schedule
evaluator, the brightness resolver, the torch hardware resolver — add unit tests. Those
classes are deliberately free of Android imports precisely so they can be tested in
milliseconds, and that property is worth protecting.

If you touch the UI, add or update a Compose test. Instrumented tests need a device that is
**unlocked and awake**; a dozing device produces a misleading "No compose hierarchies found".

## Things that will be turned down

- Networking of any kind, for any reason.
- A double-press gesture as an alternative trigger. Considered and declined; the two-key
  combo is the only gesture.
- Inlining the pure logic into the Android classes. It is separated so it can be tested.
- Widening the accessibility service's event subscription. It requests the narrowest scope
  that still delivers key events, and that is a privacy property worth keeping.

## Licence

Contributions are accepted under [GPL-3.0](LICENSE), the licence of the project.
