package local.ken.foldguard;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public final class DisplaySettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Shizuku.UserServiceArgs args;
    private IDisplaySettings service;
    private EditText width, height, density;
    private CheckBox lock;
    private Button preview, defaults, keep, cancel;
    private TextView status, current;
    private Button[] presets;
    private int ratioWidth, ratioHeight;
    private long deadline;
    private boolean bound, busy;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IDisplaySettings.Stub.asInterface(binder);
            submit(IDisplaySettings::state);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            handler.removeCallbacks(tick);
            status.setText("授权服务已断开，请重新打开显示设置");
            controls();
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTitle("显示设置");
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.addView(panel);
        setContentView(scroll);
        label(panel, "显示设置").setTextSize(24);
        current = label(panel, "正在读取当前设置…");
        status = label(panel, "试用后请在 15 秒内点保留，超时将恢复试用前的设置。");
        keep = button(panel, "保留", () -> finishTrial(true));
        cancel = button(panel, "立即恢复", () -> finishTrial(false));
        label(panel, "XREAL One Pro · 横屏镜像");
        presets = new Button[]{
                preset(panel, "标准观看", 1920, 1080, 280),
                preset(panel, "大字阅读", 1920, 1080, 340),
                preset(panel, "高分辨率", 2560, 1440, 375)
        };
        defaults = button(panel, "恢复系统默认", () -> submit(s -> {
            Bundle applied = s.preview(0, 0, 0, true);
            return s.finishTrial(applied.getLong("deadline"), true);
        }));
        width = field(panel, "宽度（像素，自然方向）");
        height = field(panel, "高度（像素，自然方向）");
        lock = new CheckBox(this);
        lock.setText("锁定宽高比（修改宽度时自动计算高度）");
        lock.setChecked(true);
        panel.addView(lock);
        lock.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                int w = number(width), h = number(height);
                if (w <= 0 || h <= 0) { lock.setChecked(false); return; }
                ratioWidth = w; ratioHeight = h;
            }
            controls();
        });
        width.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (lock.isChecked() && ratioWidth > 0) {
                    int w = number(width);
                    height.setText(w <= 0 ? "" : String.valueOf(Math.round((double) w * ratioHeight / ratioWidth)));
                }
            }
        });
        density = field(panel, "逻辑显示密度（DPI）");
        preview = button(panel, "试用 15 秒", () -> {
            int w = number(width), h = number(height), dpi = number(density);
            submit(s -> s.preview(w, h, dpi, false));
        });
        controls();
        try {
            if (!"TB321FU".equals(Build.MODEL) || !Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("请先在 Unlit 主页授权 Shizuku 或 Sui");
            args = new Shizuku.UserServiceArgs(new ComponentName(this, DisplaySettingsService.class))
                    .daemon(true).processNameSuffix("display_settings").version(BuildConfig.VERSION_CODE);
            Shizuku.bindUserService(args, connection);
            bound = true;
        } catch (Exception e) { status.setText(e.getMessage()); }
    }

    private Button preset(LinearLayout panel, String name, int landscapeWidth, int landscapeHeight, int dpi) {
        String title = name + " · " + landscapeWidth + " × " + landscapeHeight + " · " + dpi + " DPI";
        // TB321FU window-manager overrides use portrait natural orientation.
        return button(panel, title, () -> submit(s -> s.preview(landscapeHeight, landscapeWidth, dpi, false)));
    }

    private interface Request { Bundle run(IDisplaySettings s) throws RemoteException; }
    private void submit(Request request) {
        IDisplaySettings target = service;
        if (target == null || busy) return;
        busy = true;
        controls();
        worker.execute(() -> {
            try {
                Bundle b = request.run(target);
                runOnUiThread(() -> {
                    if (isDestroyed() || service != target) return;
                    busy = false;
                    String[] size = b.getString("size", "").split("x");
                    ratioWidth = Integer.parseInt(size[0]); ratioHeight = Integer.parseInt(size[1]);
                    width.setText(size[0]); height.setText(size[1]); density.setText(b.getString("density"));
                    current.setText("当前：" + b.getString("size") + " 像素，" + b.getString("density") + " DPI");
                    deadline = b.getLong("deadline");
                    String message = b.getString("status", "");
                    status.setText(message.isEmpty() ? "试用后 15 秒内可保留，超时自动恢复。" : message);
                    controls();
                    handler.removeCallbacks(tick);
                    if (deadline > 0) handler.post(tick);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    status.setText("操作失败：" + e.getMessage());
                    controls();
                });
            }
        });
    }
    private void finishTrial(boolean save) {
        long id = deadline;
        submit(s -> s.finishTrial(id, save));
    }
    private final Runnable tick = new Runnable() {
        public void run() {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) {
                if (busy) handler.postDelayed(this, 200);
                else finishTrial(false);
                return;
            }
            status.setText("剩余 " + ((remaining + 999) / 1000) + " 秒，超时自动恢复");
            handler.postDelayed(this, 250);
        }
    };
    private void controls() {
        boolean editable = service != null && !busy && deadline == 0;
        width.setEnabled(editable); height.setEnabled(editable && !lock.isChecked());
        density.setEnabled(editable); lock.setEnabled(editable);
        for (Button preset : presets) preset.setEnabled(editable);
        preview.setEnabled(editable); defaults.setEnabled(editable);
        for (Button b : new Button[]{keep, cancel}) {
            b.setVisibility(deadline == 0 ? View.GONE : View.VISIBLE);
            b.setEnabled(!busy && service != null);
        }
    }
    private TextView label(LinearLayout p, String text) {
        TextView v = new TextView(this); v.setText(text); p.addView(v); return v;
    }
    private EditText field(LinearLayout p, String text) {
        TextView label = label(p, text);
        EditText v = new EditText(this);
        v.setId(View.generateViewId()); label.setLabelFor(v.getId());
        v.setSingleLine(); v.setInputType(InputType.TYPE_CLASS_NUMBER); p.addView(v); return v;
    }
    private Button button(LinearLayout p, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setOnClickListener(v -> action.run()); p.addView(b); return b;
    }
    private int number(EditText field) {
        try { return Integer.parseInt(field.getText().toString()); }
        catch (NumberFormatException e) { return 0; }
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (bound && Shizuku.pingBinder()) Shizuku.unbindUserService(args, connection, false);
        service = null;
        worker.shutdown();
        super.onDestroy();
    }
}
