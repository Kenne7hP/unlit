# Unlit·息幕

Unlit is an experimental Android screen-off touch guard for compatible Samsung foldables, the Samsung SM-G9860, and Lenovo Y700 TB321FU tablets. It uses a Shizuku shell service or an existing Sui root service to control the active physical display and Linux input ownership to block real touchscreen input.

Current source version is **v0.4.9-preview**, version code 15. Published packages are listed under [Releases](https://github.com/Kenne7hP/unlit/releases).

> **Credit to [Extinguish](https://github.com/Moderpach/Extinguish) by Moderpach.** Unlit's display-control class-loader and native-library loading implementation is adapted from Extinguish. Its screen-off workflow also informed this project. The original copyright and GPL license are retained. Unlit is independently maintained and is not an official Extinguish release.

## Controls

- Enable the service from the app before using shortcuts. Enabling alone keeps the display on.
- **Three short Volume Down presses:** start screen-off protection, after a 0.5-second hand-off.
- **Two short Volume Down presses:** restore the screen while keeping the service ready.
- Consecutive completed short presses must be no more than 500 ms apart. Long presses and autorepeat do not count. System volume behavior stays active.
- Tap the draggable shield or Quick Settings tile to start; the app and notification also provide screen and service controls.
- The shield hides during the hand-off and protection, then returns on restoration. Stopping the service restores the screen and removes both the shield and key listener.
- Ordinary screen rotation and layout configuration callbacks keep the countdown and protection active. Physical panel switches and logical display-power changes end screen-off protection.

## Compatibility and first use

The supported input profiles require arm64, Android 13 or later, Shizuku 13 or later or an existing compatible Sui service, and access to the input devices and display interfaces. Compatibility depends on firmware.

- Samsung dual-screen profile requires both `sec_touchscreen` and `sec_touchscreen2` with multitouch/contact capability.
- Samsung SM-G9860 single-screen profile requires direct multitouch input from `sec_touchscreen` and excludes `sec_touchpad`.
- Lenovo TB321FU single-screen profile requires `NVTCapacitiveTouchScreen` with multitouch axes, contact and direct-touch properties. Its separate pen device is excluded. Other Y700 models need their own compatibility verification.

Discovery uses device names and capabilities. Display control accepts shell UID (user identifier) 2000 or root UID 0 within the privileged service and checks the calling application separately. The included Shizuku provider automatically selects existing Sui where available. The app uses existing permissions and services, with no root module installation or security-policy changes.

1. Start Shizuku or use your existing Sui service and authorize Unlit. Allow notifications and overlay access. Each time the app is opened with permissions missing, a reminder lists what needs attention and links to the permission checks.
2. Stop any other active display-control app, including Extinguish.
3. Enable the service. The ten-second screen-on test under **权限与触控检查** is optional, including after a firmware upgrade. Use it to check touch blocking and recovery on each panel you use.
4. Use the floating button or volume shortcuts. Each screen-off attempt requires successful touchscreen acquisition before the display turns off.

The original application identity `local.ken.foldguard` is intentionally retained for upgrades from FoldGuard. The visible project/app name is now **Unlit·息幕**.

## Recovery

- If restoration fails, press Power to lock/sleep, then press again to wake. Verify this hardware recovery on your own device before relying on protection.
- The screen-on probe releases input after ten seconds. Two short Volume Down presses cancel it early.
- The app sends frequent heartbeats; five seconds of lost heartbeat ends protection. An independent native watchdog releases input ownership. Process termination also releases kernel input ownership.
- Protection holds a renewable fifteen-second wake-lock lease. It does not restart automatically after reboot, revoked permission or process loss.
- Physical display switches and logical power-state changes end screen-off protection. System lock stops the service. Ordinary rotation keeps the current protection session active. The display-on probe keeps its ten-second limit.
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

Version 0.4.5 passed 22 Java unit tests, native analysis, Android lint with zero errors, and package signature/permission/alignment checks. Its configuration callback only repositions the floating control. The existing controller continues to monitor actual physical-display changes. Physical Samsung rotation testing for this version is pending.

The Samsung lifecycle and physical volume-key controls passed earlier device checks. On TB321FU, v0.4.4 passed user-reported touch blocking, restoration, three-press start/two-press restore and floating-control checks using existing Sui. Three device display cycles verified physical OFF/ON and shield hide/return. Thirteen synthetic native profile assertions also passed on the tablet. New physical Samsung testing after the profile changes is pending. Playback endurance, reboot survival, stylus blocking and broader device compatibility remain unverified.

## Source layout

`app/` contains native Android Java views, the foreground controller, shell-service interface and tests. `native/` contains the input observer/ownership and watchdog implementation. `scripts/` contains the portable build command. Generated files, local device records, signing keys and private backups are excluded.

## License and acknowledgements

**GNU GPL-3.0-or-later**, preserving Extinguish's upstream copyright. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [SOURCES.md](SOURCES.md).

- [Moderpach/Extinguish](https://github.com/Moderpach/Extinguish): adapted display-control loading; inspiration for the screen-off controls. Copyright (C) 2024 Moderpach.
- [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API): shell user-service bridge, MIT; notice in [LICENSE-Shizuku](LICENSE-Shizuku).
- Linux evdev, AOSP and scrcpy: reference implementations/documentation, as described in SOURCES.md.
