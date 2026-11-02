# Unlit·息幕

Keep the app running. Let the screen rest.

Unlit is an experimental Android screen-off touch guard for compatible Samsung foldables. It uses a Shizuku shell service to control the active physical display and Linux input ownership to block real touchscreen input. It does not request internet, account or storage permissions.

> **Credit to [Extinguish](https://github.com/Moderpach/Extinguish) by Moderpach.** Unlit's display-control class-loader and native-library loading implementation is adapted from Extinguish. Its screen-off workflow also informed this project. The original copyright and GPL license are retained. Unlit is independently maintained and is not an official Extinguish release.

## Controls

- Enable the service from the app before using shortcuts. Enabling alone keeps the display on.
- **Three short Volume Down presses:** start screen-off protection, after a 0.5-second hand-off.
- **Two short Volume Down presses:** restore the screen while keeping the service ready.
- Consecutive completed short presses must be no more than 500 ms apart. Long presses and autorepeat do not count. System volume behavior stays active.
- Tap the draggable shield or Quick Settings tile to start; the app and notification also provide screen and service controls.
- The shield hides during the hand-off and protection, then returns on restoration. Stopping the service restores the screen and removes both the shield and key listener.

## Compatibility and first use

This is a device-specific preview, not a general Android screen-off solution. The current input discovery expects both `sec_touchscreen` and `sec_touchscreen2` with multitouch capability. It requires arm64, Android 14 or later, Shizuku 13 or later running as shell, and a system that allows shell access to those input devices and display interfaces. Compatibility can vary by firmware. Root and security-policy modifications are not used.

1. Start Shizuku and authorize Unlit. Allow notifications and the app's overlay permission.
2. Stop any other active display-control app, including Extinguish, before testing.
3. Open **权限与触控检查** and run the ten-second screen-on touch test on a harmless screen. On both inner and outer panels, physically test taps, swipes, edge gestures, automatic release, and double-Volume-Down cancellation.
4. Confirm only after both panels actually pass. Formal screen-off stays gated on this confirmation. An injected Android gesture or successful build does not prove physical touch ownership.
5. Enable the service and use the floating button or volume shortcuts. If the system denies ownership, Unlit reports the error; it does not fall back to a black overlay.

The original application identity `local.ken.foldguard` is intentionally retained for upgrades from FoldGuard. The visible project/app name is now **Unlit·息幕**.

## Recovery

- If restoration fails, press Power to lock/sleep, then press again to wake. Verify this hardware recovery on your own device before relying on protection.
- The screen-on probe releases input after ten seconds. Two short Volume Down presses cancel it early.
- The app sends frequent heartbeats; five seconds of lost heartbeat ends protection. An independent native watchdog releases input ownership. Process termination also releases kernel input ownership.
- Protection holds a renewable fifteen-second wake-lock lease. It does not restart automatically after reboot, revoked permission or process loss.
- Display/fold changes end the current session. System lock stops the service. Recovery on every possible firmware/fold state is not guaranteed.
- External mouse/controller input and automation injection are outside the physical touchscreen guarantee.

## Build

Use existing tools; the build script installs nothing:

- PowerShell 7
- JDK (Java Development Kit) 21
- Android SDK (Software Development Kit) platform 36 and build tools 36.0.0
- Android NDK (Native Development Kit) 27.0.12077973
- Gradle 8.14.3; Android Gradle Plugin 8.13.2

Set `JAVA_HOME` and `ANDROID_HOME`, make Gradle available, then run from this repository:

```powershell
pwsh -File scripts/build-portable.ps1
```

Alternatively pass `-SdkRoot`, `-JavaHome`, and `-Gradle` with your existing tool paths. `-Offline` uses cached dependencies. The script compiles the arm64 library with 16 KB load alignment, runs native analysis, all JUnit tests, Android lint and the debug build. Output: `app/build/outputs/apk/debug/app-debug.apk`.

The debug build uses your local Android debug signing identity. No signing key is distributed in this repository. The build does not discover or operate a phone. This repository intentionally has no Gradle wrapper binary; use the stated existing Gradle version.

## Validation status

Version 0.4.0 passed twenty local tests and repeated on-device restore/restart and full stop/re-enable checks. Its tester also reported repeated physical three-press start/two-press restore working. Version 0.4.1 corrected potential floating-window retention before initial attachment; version 0.4.2 adopts the Unlit name and explicit attribution. These later visual changes have local verification, with on-device visual acceptance still pending. No broad device support or playback endurance claim is made.

## Source layout

`app/` contains native Android Java views, the foreground controller, shell-service interface and tests. `native/` contains the input observer/ownership and watchdog implementation. `scripts/` contains the portable build command. Generated files, local device records, signing keys and private backups are excluded.

## License and acknowledgements

**GNU GPL-3.0-or-later**, preserving Extinguish's upstream copyright. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [SOURCES.md](SOURCES.md).

- [Moderpach/Extinguish](https://github.com/Moderpach/Extinguish): adapted display-control loading; inspiration for the screen-off controls. Copyright (C) 2024 Moderpach.
- [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API): shell user-service bridge, MIT; notice in [LICENSE-Shizuku](LICENSE-Shizuku).
- Linux evdev, AOSP and scrcpy: reference implementations/documentation, as described in SOURCES.md.
