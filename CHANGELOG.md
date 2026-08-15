# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [1.0.0] — 2026-08-15

First release.

### Added

- **Volume-key shortcut.** Pressing both volume keys together toggles the torch, with the
  screen off and the phone locked. Single presses continue to change the volume normally.
- **Time-aware brightness.** A configurable night window, defaulting to 21:00–06:00, during
  which the torch comes on at the lowest usable level instead of full power.
- **Brightness control** that snaps to the levels the device actually reports, with quick
  presets, shown as "Level 11 of 21" rather than a percentage the hardware cannot honour.
- **Volume restoration.** The first key of the combo unavoidably reaches the system, so the
  media volume is put back afterwards.
- **Haptic confirmation** when the shortcut fires, honouring the system-wide touch feedback
  setting.
- **First-run introduction** explaining what the accessibility permission is for, warning in
  advance about Android's own alarming dialog, and detecting the Android 13+ restricted
  settings block that stops sideloaded apps enabling accessibility services.
- **Material You** theming with a true-black dark scheme for OLED displays.

### Security and privacy

- No `INTERNET` permission. CI fails the build if it ever appears in the merged manifest.
- No `CAMERA` permission; torch control does not require one.
- Three permissions in total, each explained in the app itself, in the README and in
  PRIVACY.md.

### Notes for the curious

Several behaviours were determined by measurement on real hardware rather than from
documentation, and are recorded in `docs/device-matrix.md`:

- Volume keys deliver no repeat events to an accessibility service, so a held key produces
  exactly one down and one up.
- A key whose press is passed through to the system **must** have its release passed through
  too. Swallowing it leaves the system believing the key is held, which ran the media volume
  to maximum during development.
- A device on Android 13+ can still report a maximum flash strength of 1, so variable
  brightness cannot be inferred from the Android version.

[Unreleased]: https://github.com/bordware/NightTorch/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/bordware/NightTorch/releases/tag/v1.0.0
