package local.ken.foldguard;

import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;
import java.io.File;

/** One serial controller per connection; old workers cannot act on a new controller. */
public final class PrivilegedGuard extends IGuard.Stub {
    private final Context context;
    private int owner = -1;
    private boolean loaded;
    private GuardController controller;
    // Retain the owning class loader for android_servers for the entire shell-process lifetime.
    private DisplayPower display;
    public PrivilegedGuard() { context = null; }
    public PrivilegedGuard(Context context) { this.context = context; }
    private void checkOwner() {
        if (Binder.getCallingUid() != owner) throw new SecurityException("Unexpected caller");
    }
    @Override public synchronized String initialize(String path) {
        if (context == null) return "需要 Shizuku 13 或更高版本";
        int caller = Binder.getCallingUid();
        if (caller != context.getApplicationInfo().uid) throw new SecurityException("Unexpected caller");
        owner = caller;
        try {
            if (controller != null) controller.close();
            File expected = new File(context.getApplicationInfo().nativeLibraryDir, "libfoldguard.so");
            if (!expected.getCanonicalPath().equals(new File(path).getCanonicalPath())) throw new SecurityException("Invalid library");
            if (!loaded) { System.load(expected.getPath()); loaded = true; }
            GuardController current = new GuardController(new GuardController.Device() {
                public String open(boolean keysOnly, boolean test) { return keysOnly ? NativeInput.watchKeys() : NativeInput.start(test); }
                public long poll() { return NativeInput.pollEvent(); }
                public void close() { NativeInput.stop(); }
                public void beat() { NativeInput.heartbeat(); }
                public boolean fingersDown() { return NativeInput.fingersDown(); }
                public long now() { return SystemClock.elapsedRealtime(); }
                public void sleep(long millis) { SystemClock.sleep(millis); }
                public void screenOff() throws Exception {
                    if (display == null) display = new DisplayPower(context);
                    display.off();
                }
                public boolean displayChanged() throws Exception { return display != null && display.changed(); }
                public void restoreDisplay() throws Exception {
                    if (display != null) display.restore();
                }
            });
            String result = current.initialize();
            controller = current;
            if (!"READY".equals(result)) return result;
            new Thread(() -> {
                while (current.tick()) SystemClock.sleep(5);
            }, "FoldGuard-input").start();
            return "READY";
        } catch (Throwable e) { return message(e); }
    }
    @Override public synchronized String begin(boolean probe) { checkOwner(); return controller.begin(probe); }
    @Override public synchronized String heartbeat() { checkOwner(); return controller.heartbeat(); }
    @Override public synchronized String stop() { checkOwner(); return controller == null ? "未初始化" : controller.stop(); }
    @Override public synchronized void setArmed(boolean armed) { checkOwner(); controller.arm(armed); }
    @Override public synchronized void destroy() {
        if (controller != null) controller.close();
        System.exit(0);
    }
    private static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage());
    }
}
