# Sources and attribution

## Adapted implementation: Extinguish

Unlit's `app/src/main/java/local/ken/foldguard/DisplayPower.java` adapts the display-control loading implementation in **[Extinguish](https://github.com/Moderpach/Extinguish)**, by **Moderpach**. Copyright (C) 2024 Moderpach. It remains licensed under GPL version 3 or any later version. The original license/copyright text is preserved verbatim in LICENSE; NOTICE and the adapted source identify the origin and modifications.

Relevant original files:

- [DisplayControlProxy.kt](https://github.com/Moderpach/Extinguish/blob/master/hidden-api/src/main/java/extinguish/hiddenAPI/server/display/DisplayControlProxy.kt): class-loader creation and loading `android_servers` with the owning DisplayControl class.
- [SurfaceControlProxy.kt](https://github.com/Moderpach/Extinguish/blob/master/hidden-api/src/main/java/extinguish/hiddenAPI/view/SurfaceControlProxy.kt): display power interface reference.
- `service/hosts/FloatingButtonHost.kt`: consulted for separate floating-control and service lifecycle behavior. Unlit's native Android UI and draggable button were implemented separately.

Unlit modifications include persistent loader ownership across repeated sessions, active physical-display tracking, guarded restore, independently implemented input ownership/watchdog, triple-start/double-restore gestures, serial control state, bounded single/dual-screen profiles and the native Java UI. Version 0.4.5 separates layout configuration callbacks from physical-display recovery. No installed APK was reverse-engineered. No upstream endorsement is implied.

## Shizuku

[Shizuku-API 13.1.5](https://github.com/RikkaApps/Shizuku-API), copyright (c) 2021 RikkaW, MIT license. API/provider dependencies are resolved by Gradle. LICENSE-Shizuku retains the notice.

Official `Shizuku.java`, `ShizukuServiceConnections.java` and `ShizukuServiceConnection.java`, and the exact dependency bytecode, were checked for binding/removal behavior. Stop detaches client callbacks before removing the helper, preventing stale binder-death events from wiping a new connection. No Shizuku source was copied.

The included ShizukuProvider initializes existing Sui by default through Sui.init(packageName). The existing API dependency includes the Sui client API. Unlit accepts root UID 0 for that privileged backend as well as shell UID 2000 for Shizuku, with a separate Binder caller check. The Sui status label and explicit post-probe confirmation use these existing APIs.

## Reference implementations

- [Linux evdev](https://github.com/torvalds/linux/blob/master/drivers/input/evdev.c): EVIOCGRAB ownership and descriptor-close release semantics. No kernel implementation copied.
- [Linux input event codes](https://docs.kernel.org/input/event-codes.html) describe BTN_TOUCH and INPUT_PROP_DIRECT. The TB321FU profile combines these properties with multitouch axes and the touchscreen name, excluding its separate pen. Synthetic selection tests are in native/profile_test.c.
- [AOSP WindowManagerGlobal](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/WindowManagerGlobal.java) and [ContextImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/ContextImpl.java): registered-window removal and explicit-display WindowContext requirements. No implementation copied.
- [scrcpy DisplayControl](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java): checked loader lifetime and reflective call signatures. No additional scrcpy implementation copied.
- [ART runtime](https://android.googlesource.com/platform/art/+/refs/heads/main/runtime/runtime.cc): standalone shell-process hidden-interface context.

DisplayPower has a constructor-local BlockedPrivateApi lint annotation because it runs in the standalone privileged service. It enforces root UID 0 or shell UID 2000 before loading. No runtime hidden-API exemption, SELinux modification or target-SDK downgrade is applied. Firmware can still reject the calls. Failures keep or restore the display where possible.
