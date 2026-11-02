// Display-control loading adapted from Extinguish by Moderpach.
// Copyright (C) 2024 Moderpach. Modifications (C) 2026 Unlit contributors.
// SPDX-License-Identifier: GPL-3.0-or-later
// Original: https://github.com/Moderpach/Extinguish
// See NOTICE and SOURCES.md for adaptation scope and retained attribution.
package local.ken.foldguard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.view.Display;
import java.lang.reflect.Method;

/** DisplayControl loading follows Extinguish's GPL-3.0 implementation; see SOURCES.md. */
final class DisplayPower {
    private final DisplayManager manager;
    private final Method token, power;
    private final Class<?> control;
    private IBinder savedToken;
    private String savedAddress;
    // Lint models a zygote app process here; this code only runs in Shizuku's standalone
    // app_process shell service. ART's default non-zygote hidden-api policy is disabled.
    // No hidden-api exemptions, target-SDK downgrades or system policy changes are applied.
    // Reference implementation: scrcpy server/wrappers/DisplayControl.java; see SOURCES.md.
    @SuppressLint("BlockedPrivateApi")
    DisplayPower(Context context) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("Display control requires the Shizuku shell process");
        manager = context.getSystemService(DisplayManager.class);
        Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
        Method create = factory.getDeclaredMethod("createClassLoader", String.class, String.class,
                String.class, ClassLoader.class, int.class, boolean.class, String.class);
        ClassLoader loader = (ClassLoader) create.invoke(null, "/system/framework/services.jar",
                null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
        control = loader.loadClass("com.android.server.display.DisplayControl");
        Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
        load.setAccessible(true);
        load.invoke(Runtime.getRuntime(), control, "android_servers");
        token = control.getMethod("getPhysicalDisplayToken", long.class);
        power = Class.forName("android.view.SurfaceControl").getMethod("setDisplayPowerMode", IBinder.class, int.class);
    }
    private Object address(Display display) throws Exception {
        return Display.class.getMethod("getAddress").invoke(display);
    }
    void off() throws Exception {
        Display display = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null || display.getState() != Display.STATE_ON) throw new IllegalStateException("屏幕未处于亮屏状态");
        Object address = address(display);
        long id = (long) address.getClass().getMethod("getPhysicalDisplayId").invoke(address);
        savedAddress = address.toString();
        savedToken = (IBinder) token.invoke(null, id);
        if (savedToken == null) throw new IllegalStateException("找不到当前物理屏幕");
        power.invoke(null, savedToken, 0);
    }
    boolean changed() throws Exception {
        Display display = manager.getDisplay(Display.DEFAULT_DISPLAY);
        return display == null || display.getState() != Display.STATE_ON
                || !address(display).toString().equals(savedAddress);
    }
    void restore() throws Exception {
        if (savedToken == null) return;
        // Never turn on a now-inactive panel or override a system lock/sleep transition.
        Display display = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null && display.getState() == Display.STATE_ON
                && address(display).toString().equals(savedAddress)) power.invoke(null, savedToken, 2);
        savedToken = null;
    }
}
