package local.ken.foldguard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView state, checks, badge;
    private Button confirm, power, protect, setupToggle;
    private LinearLayout setup;
    private int count;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean binder = Shizuku.pingBinder();
            boolean permission = binder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            SharedPreferences prefs = getSharedPreferences("guard", MODE_PRIVATE);
            boolean tested = GuardService.calibrationMatches(prefs.getString("probe", ""));
            boolean verified = GuardService.calibrationMatches(prefs.getString("confirmed", ""));
            checks.setText("Shizuku：" + (permission ? "已授权" : binder ? "待授权" : "未运行")
                    + "\n持续运行权限：" + (Settings.canDrawOverlays(MainActivity.this) ? "已允许" : "待允许")
                    + "\n手指触控验证：" + (verified ? "已由你确认" : tested ? "等待你确认" : "待测试"));
            badge.setText(GuardService.active ? "●  保护中" : GuardService.starting ? "●  准备灭屏" : GuardService.enabled ? "●  服务就绪" : GuardService.connecting ? "●  正在连接" : "○  待启用");
            state.setText(GuardService.status);
            boolean running = GuardService.enabled || GuardService.connecting;
            power.setText(GuardService.connecting ? "取消启用" : GuardService.enabled ? "停止服务" : "启用服务");
            power.setEnabled(running || (verified && permission && Settings.canDrawOverlays(MainActivity.this)));
            styleButton(power, running ? 0xFFE6ECE8 : 0xFFB8F2D4, 0xFF142A30);
            power.setAlpha(power.isEnabled() ? 1f : 0.45f);
            protect.setAlpha(GuardService.enabled && verified ? 1f : 0.45f);
            protect.setText(GuardService.active ? "恢复亮屏" : GuardService.starting ? "取消灭屏" : "立即灭屏");
            protect.setEnabled(GuardService.enabled && verified);
            confirm.setEnabled(tested && !GuardService.active && !GuardService.starting);
            confirm.setAlpha(confirm.isEnabled() ? 1f : 0.45f);
            handler.postDelayed(this, 750);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF3F5F2);
        scroll.setFillViewport(true);
        LinearLayout frame = new LinearLayout(this);
        frame.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        frame.addView(body, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(frame); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            frame.setPadding(dp(20) + safe.left, dp(20) + safe.top, dp(20) + safe.right, dp(24) + safe.bottom);
            return insets;
        });
        frame.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
            int width = Math.min(dp(600), frame.getWidth() - frame.getPaddingLeft() - frame.getPaddingRight());
            if (width > 0 && body.getLayoutParams().width != width) {
                body.getLayoutParams().width = width; body.requestLayout();
            }
        });
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_launcher);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0); brand.addView(names);
        TextView title = text(names, getString(R.string.app_name)); title.setTextColor(0xFF142A30); title.setTextSize(25); title.setTypeface(null, Typeface.BOLD); title.setPadding(0,0,0,0);
        TextView sub = text(names, "灭屏守护 · 让运行继续"); sub.setTextSize(13); sub.setPadding(0,dp(3),0,0);
        body.addView(brand);

        LinearLayout hero = card(body, 0xFF102D35, 24);
        badge = text(hero, "○  待启用"); badge.setTextColor(0xFFB8F2D4); badge.setTextSize(13); badge.setTypeface(null, Typeface.BOLD);
        TextView headline = text(hero, "屏幕休息\n精彩继续。");
        headline.setTextColor(Color.WHITE); headline.setTextSize(34); headline.setTypeface(null, Typeface.BOLD);
        headline.setLineSpacing(dp(3), 1f);
        TextView intro = text(hero, "保持当前应用运行，隔绝手指误触。"); intro.setTextColor(0xFFC1D3D3); intro.setTextSize(14);
        state = text(hero, "正在检查…"); state.setTextColor(0xFFC1D3D3); state.setTextSize(13);
        power = button(hero, "启用服务", () -> action(GuardService.enabled || GuardService.connecting ? "STOP" : "ENABLE"));
        protect = button(body, "立即灭屏", () -> {
            boolean restoring = GuardService.active || GuardService.starting;
            action(restoring ? "RESTORE" : "START");
            if (!restoring) moveTaskToBack(true);
        });
        TextView hint = text(body, "启用后，三击音量下键或轻点悬浮按钮灭屏。"); hint.setTextSize(13);

        LinearLayout guide = card(body, Color.WHITE, 20);
        TextView guideTitle = text(guide, "音量下键：三击灭屏 · 双击恢复"); guideTitle.setTextColor(0xFF142A30); guideTitle.setTypeface(null, Typeface.BOLD); guideTitle.setTextSize(17);
        text(guide, "连续短按 · 每次间隔不超过 0.5 秒").setTextSize(14);
        TextView keys = text(guide, "三击 → 灭屏  /  双击 → 亮屏"); keys.setTextColor(0xFF21694F); keys.setTextSize(18); keys.setTypeface(null, Typeface.BOLD);
        text(guide, "恢复后可再次三击灭屏。停止服务会关闭按键监听。\n若未恢复：按电源键锁屏，再按一次唤醒。").setTextSize(13);
        setupToggle = button(body, "权限与触控检查  ＋", () -> {
            boolean show = setup.getVisibility() != View.VISIBLE;
            setup.setVisibility(show ? View.VISIBLE : View.GONE);
            setupToggle.setText(show ? "权限与触控检查  −" : "权限与触控检查  ＋");
        });
        setup = card(body, Color.WHITE, 20);
        boolean verified = GuardService.calibrationMatches(getSharedPreferences("guard", MODE_PRIVATE).getString("confirmed", ""));
        setup.setVisibility(verified ? View.GONE : View.VISIBLE);
        setupToggle.setText(verified ? "权限与触控检查  ＋" : "权限与触控检查  −");
        checks = text(setup, "正在检查权限…");
        button(setup, "1. 授权 Shizuku", () -> {
            if (!Shizuku.pingBinder()) { Toast.makeText(this, "请先在 Shizuku 中启动服务", Toast.LENGTH_LONG).show(); return; }
            Shizuku.requestPermission(100);
        });
        button(setup, "2. 允许悬浮按钮", () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        text(setup, "首次使用请完成双屏触控检查；使用 Unlit·息幕 时请停止 Extinguish 的活动控制。");
        TextView pad = text(setup, "触控测试区：点击或滑动\n事件计数：0");
        pad.setMinHeight(dp(132)); pad.setPadding(dp(16), dp(20), dp(16), dp(20));
        pad.setBackground(shape(0xFFEAF3ED, 16));
        pad.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE)
                pad.setText("触控测试区：点击或滑动\n事件计数：" + (++count));
            return true;
        });
        button(setup, "3. 亮屏独占测试（10 秒）", () -> {
            getSharedPreferences("guard", MODE_PRIVATE).edit().remove("probe").remove("confirmed").apply();
            action("PROBE");
        });
        confirm = button(setup, "4. 确认手指和边缘操作均被屏蔽", () -> new AlertDialog.Builder(this)
                .setTitle("确认实测结果")
                .setMessage("只有 10 秒测试期间手指点击、滑动及系统边缘手势均无效，结束后正常恢复，且双击音量下键能提前退出测试，才选择通过。内屏、外屏均须分别验证。")
                .setNegativeButton("未通过", (d, w) -> getSharedPreferences("guard", MODE_PRIVATE).edit().remove("confirmed").apply())
                .setPositiveButton("两块屏幕均已实测通过", (d, w) -> getSharedPreferences("guard", MODE_PRIVATE).edit().putString("confirmed", GuardService.deviceKey()).apply()).show());
        button(setup, "添加快捷设置磁贴", () -> {
            getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, GuardTile.class), getString(R.string.app_name), android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_guard),
                    getMainExecutor(), result -> { });
        });
        text(setup, "若屏幕未恢复：按电源键锁屏，再按一次唤醒。\n固件或触控实现更新后需重新检查；仅调整界面不会清除既有确认。双击音量下键可能同时降低音量。");
        TextView credit = text(setup, "");
        credit.setText(android.text.Html.fromHtml("致谢：<a href=\"https://github.com/Moderpach/Extinguish\">Extinguish · Moderpach</a><br>显示控制加载实现源自 Extinguish，遵循 GPL-3.0-or-later。", android.text.Html.FROM_HTML_MODE_LEGACY));
        credit.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        credit.setTextSize(13);
        TextView footer = text(body, "UNLIT  /  " + BuildConfig.VERSION_NAME + "\n本地运行 · 无网络权限");
        footer.setGravity(Gravity.CENTER); footer.setTextSize(11); footer.setLetterSpacing(0.06f); footer.setPadding(0, dp(28), 0, dp(4));
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(radius)); return background;
    }
    private LinearLayout card(LinearLayout parent, int color, int radius) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(18), dp(22), dp(20)); card.setBackground(shape(color, radius));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(20); parent.addView(card, layout); return card;
    }
    private TextView text(LinearLayout body, String value) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(15); view.setTextColor(0xFF596B70);
        view.setLineSpacing(dp(3), 1f); view.setPadding(0, dp(6), 0, dp(6)); body.addView(view); return view;
    }
    private void styleButton(Button button, int background, int foreground) {
        if (Integer.valueOf(background).equals(button.getTag())) return;
        button.setTag(background);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22142A30), shape(background, 16), null));
        button.setTextColor(foreground);
    }
    private Button button(LinearLayout body, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextSize(15); button.setTypeface(null, Typeface.BOLD);
        button.setMinHeight(dp(56)); button.setPadding(dp(16), dp(12), dp(16), dp(12));
        styleButton(button, 0xFFE6ECE8, 0xFF142A30);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(10);
        button.setOnClickListener(v -> action.run()); body.addView(button, layout); return button;
    }
    private void action(String action) {
        Intent intent = new Intent(this, GuardService.class).setAction(action);
        startForegroundService(intent);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
