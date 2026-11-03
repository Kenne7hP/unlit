package local.ken.foldguard;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import rikka.shizuku.Shizuku;

public final class GuardService extends Service {
    public static volatile String status = "未连接";
    public static volatile boolean active;
    public static volatile boolean enabled, connecting, starting;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IGuard remote;
    private Shizuku.UserServiceArgs args;
    private String pending;
    private boolean bound, ending;
    private int bindingGeneration, latestStartId;
    private ServiceConnection connection;
    private View keeper;
    private PowerManager.WakeLock wakeLock;
    private FloatingControl floating;
    private Runnable beginTask;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finishSession("系统锁屏，已退出"); }
    };
    private ServiceConnection newConnection(int generation) {
        return new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                handler.post(() -> {
                    if (ending || generation != bindingGeneration) return;
                    remote = IGuard.Stub.asInterface(binder);
                    try {
                        String result = remote.initialize(getApplicationInfo().nativeLibraryDir + "/libfoldguard.so");
                        if (!"READY".equals(result)) { finishSession(result); return; }
                        remote.setArmed(calibrationMatches(preferences().getString("confirmed", "")));
                        connecting = false; enabled = true;
                        status = "服务已启用；三击音量下键灭屏，双击恢复";
                        handler.removeCallbacks(pulse); handler.post(pulse);
                        if (pending != null) beginPending();
                        else { updateFloating(); notifyState(); }
                    } catch (Exception e) { finishSession("权限服务初始化失败：" + e.getClass().getSimpleName()); }
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                handler.post(() -> {
                    if (ending || generation != bindingGeneration) return;
                    remote = null;
                    finishSession("Shizuku 连接中断；若未亮屏请按电源键");
                });
            }
        };
    }
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (ending || remote == null) return;
            try {
                if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    finishSession("Shizuku 失效或权限已撤销，已退出"); return;
                }
                if (enabled && !Settings.canDrawOverlays(GuardService.this)) {
                    finishSession("持续运行权限已撤销，已退出"); return;
                }
                String result = remote.heartbeat();
                if (result.startsWith("CONTROLLER_EXITED:")) { finishSession(result); return; }
                boolean remotelyRunning = "GUARD_RUNNING".equals(result) || "PROBE_RUNNING".equals(result);
                if (active && remotelyRunning && wakeLock != null) wakeLock.acquire(15000);
                if (active && !remotelyRunning) {
                    if ("PROBE_PASSED".equals(result)) {
                        preferences().edit().putString("probe", deviceKey()).apply();
                        result = "10 秒测试完成；请确认实际手指触控确已被屏蔽";
                    }
                    status = result;
                    active = false;
                    releaseKeeper();
                    updateFloating();
                    notifyState();
                }
                if ("KEY_START".equals(result) && enabled && !active && !starting
                        && calibrationMatches(preferences().getString("confirmed", ""))
                        && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                    pending = "START";
                    beginPending();
                }
                updateFloating();
                if (!ending) handler.postDelayed(this, 200);
            } catch (Exception e) { finishSession("服务中断；若未亮屏请按电源键"); }
        }
    };
    // UI releases do not invalidate the unchanged, previously tested input protocol.
    static String deviceKey() { return Build.FINGERPRINT + ":touch-v1"; }
    static boolean calibrationMatches(String value) {
        return deviceKey().equals(value) || (Build.FINGERPRINT + ":1").equals(value)
                || (Build.FINGERPRINT + ":2").equals(value);
    }
    private SharedPreferences preferences() { return getSharedPreferences("guard", MODE_PRIVATE); }
    @Override public void onCreate() {
        super.onCreate();
        active = enabled = connecting = starting = false;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("guard", "灭屏防误触", NotificationManager.IMPORTANCE_LOW));
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String action = intent == null ? "STOP" : intent.getAction();
        if ("STOP".equals(action)) { finishSession("已停止服务"); return START_NOT_STICKY; }
        if ("RESTORE".equals(action)) {
            if (enabled) restoreSession("已恢复亮屏，服务继续运行");
            else finishSession("服务未启用");
            return START_NOT_STICKY;
        }
        // A new command may arrive after stopSelf() but before Android destroys this instance.
        // Stop already released the old binding; allow a clean connection instead of dropping it.
        ending = false;
        if (active || starting) return START_NOT_STICKY;
        try {
            startForeground(1, notification());
        } catch (ForegroundServiceStartNotAllowedException | SecurityException error) {
            finishSession("后台启动被系统拒绝，请从 Unlit·息幕 磁贴或主界面开始");
            return START_NOT_STICKY;
        }
        if (("START".equals(action) || "ENABLE".equals(action)) && !calibrationMatches(preferences().getString("confirmed", ""))) {
            finishSession("请先完成亮屏触控测试并确认结果"); return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            finishSession("请先允许悬浮窗，以保持当前应用持续运行"); return START_NOT_STICKY;
        }
        if (getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            finishSession("请先解锁手机"); return START_NOT_STICKY;
        }
        if ("PROBE".equals(action) || "START".equals(action)) pending = action;
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("Shizuku 未运行或未授权");
            if (!bound) {
                args = new Shizuku.UserServiceArgs(new ComponentName(this, PrivilegedGuard.class))
                        .daemon(true).processNameSuffix("guard").version(BuildConfig.VERSION_CODE);
                status = "正在连接 Shizuku…";
                connecting = true;
                bound = true;
                connection = newConnection(++bindingGeneration);
                Shizuku.bindUserService(args, connection);
                int generation = bindingGeneration;
                handler.postDelayed(() -> { if (!ending && generation == bindingGeneration && remote == null)
                    finishSession("连接超时，请检查 Shizuku"); }, 12000);
            } else if (remote != null && pending != null) beginPending();
            else if (remote != null) { updateFloating(); notifyState(); }
        } catch (Exception e) { finishSession(String.valueOf(e.getMessage())); }
        return START_NOT_STICKY;
    }
    private void beginPending() {
        String action = pending;
        pending = null;
        starting = true;
        updateFloating();
        status = "请移开手指，0.5 秒后开始";
        notifyState();
        beginTask = () -> {
            if (ending || remote == null || active) return;
            try {
                acquireKeeper();
                remote.setArmed(calibrationMatches(preferences().getString("confirmed", "")));
                String result = remote.begin("PROBE".equals(action));
                starting = false;
                if (!"OK".equals(result)) { releaseKeeper(); status = result; updateFloating(); notifyState(); return; }
                active = true;
                status = "PROBE".equals(action) ? "亮屏测试中，10 秒自动恢复；双击音量下键可提前退出"
                        : "灭屏保护中；双击音量下键恢复";
                notifyState();
            } catch (Exception e) { finishSession("启动失败：" + e.getClass().getSimpleName()); }
        };
        handler.postDelayed(beginTask, 500);
    }
    private void restoreSession(String reason) {
        if (beginTask != null) handler.removeCallbacks(beginTask);
        pending = null;
        if (remote != null) try { remote.stop(); } catch (Exception error) { finishSession("恢复连接失败，请按电源键"); return; }
        active = false; starting = false;
        releaseKeeper();
        status = reason;
        updateFloating(); notifyState();
    }
    private void updateFloating() {
        boolean show = enabled && !ending && !active && !starting && calibrationMatches(preferences().getString("confirmed", ""));
        try {
            if (floating == null && show) floating = new FloatingControl(this,
                    () -> startService(new Intent(this, GuardService.class).setAction("START")));
            if (floating != null) { if (show) floating.show(); else floating.hide(); }
        } catch (RuntimeException error) { finishSession("悬浮按钮不可用：" + error.getClass().getSimpleName()); }
    }
    private void acquireKeeper() {
        WindowManager windows = getSystemService(WindowManager.class);
        keeper = new View(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windows.addView(keeper, params);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FoldGuard:session");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(15000);
    }
    private void releaseKeeper() {
        if (keeper != null) {
            try { getSystemService(WindowManager.class).removeViewImmediate(keeper); } catch (RuntimeException ignored) { }
            keeper = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getService(this, 2, new Intent(this, GuardService.class)
                .setAction(active || starting ? "RESTORE" : "START"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "guard").setSmallIcon(R.drawable.ic_guard)
                .setContentTitle(getString(R.string.app_name)).setContentText(status).setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, active || starting ? "恢复亮屏" : "立即灭屏", toggle).build())
                .addAction(new Notification.Action.Builder(null, "停止服务", stop).build()).build();
    }
    private void notifyState() { if (!ending) getSystemService(NotificationManager.class).notify(1, notification()); }
    private void finishSession(String reason) {
        if (ending) return;
        ending = true;
        handler.removeCallbacksAndMessages(null);
        pending = null; beginTask = null;
        ++bindingGeneration;
        if (remote != null) try { remote.stop(); } catch (Exception ignored) { }
        remote = null;
        if (bound && Shizuku.pingBinder()) {
            // remove=true kills the helper but leaves Shizuku's client connection cached until
            // its asynchronous death callback. Detach/clear it first so death cannot erase a rebind.
            try { Shizuku.unbindUserService(args, connection, false); } catch (Exception ignored) { }
            try { Shizuku.unbindUserService(args, null, true); } catch (Exception ignored) { }
        }
        bound = false; connection = null; args = null;
        active = false;
        enabled = false; connecting = false; starting = false;
        if (floating != null) { floating.hide(); floating = null; }
        releaseKeeper();
        status = reason;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(latestStartId);
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        // Rotation only changes layout. The privileged controller detects physical
        // display switches and power-state changes independently of configuration callbacks.
        if (floating != null) floating.reposition();
    }
    @Override public void onDestroy() {
        finishSession(status);
        unregisterReceiver(screenOff);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        out.println("FoldGuard enabled=" + enabled + " connecting=" + connecting + " starting=" + starting
                + " active=" + active + " ending=" + ending + " bound=" + bound);
        out.println("status=" + status);
    }
}
