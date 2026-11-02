package local.ken.foldguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.ImageButton;

/** A nonfocusable, draggable control, hidden before touch ownership starts. */
final class FloatingControl {
    private final Context context;
    private final WindowManager windows;
    private final SharedPreferences prefs;
    private final Runnable onClick;
    private ImageButton button;
    private boolean added;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int initialX, initialY;
    private boolean dragging;
    FloatingControl(Context context, Runnable onClick) {
        // Service contexts have no associated display; the explicit-display overload is required.
        Display display = context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        this.context = context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        this.onClick = onClick;
        windows = this.context.getSystemService(WindowManager.class);
        prefs = context.getSharedPreferences("guard", Context.MODE_PRIVATE);
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    void show() {
        if (button != null) return;
        button = new ImageButton(context);
        button.setImageResource(R.drawable.ic_guard);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFB8F2D4));
        button.setContentDescription(context.getString(R.string.app_name) + "：点击灭屏，拖动移动");
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL); background.setColor(0xF5102D35);
        background.setStroke(dp(1), 0xFFB8F2D4);
        button.setBackground(background); button.setElevation(dp(6));
        params = new WindowManager.LayoutParams(dp(52), dp(52), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("Unlit floating control");
        placeSavedPosition();
        button.setOnClickListener(view -> onClick.run());
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    initialX = params.x; initialY = params.y; dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    int slop = ViewConfiguration.get(context).getScaledTouchSlop();
                    if (dragging || dx * dx + dy * dy > slop * slop) {
                        dragging = true;
                        params.x = initialX + Math.round(dx); params.y = initialY + Math.round(dy);
                        clamp(); windows.updateViewLayout(button, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        Rect bounds = windows.getCurrentWindowMetrics().getBounds();
                        prefs.edit().putFloat("floating_x", (float)params.x / Math.max(1, bounds.width() - params.width))
                                .putFloat("floating_y", (float)params.y / Math.max(1, bounds.height() - params.height)).apply();
                    } else view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL: dragging = false; return true;
                default: return true;
            }
        });
        try {
            windows.addView(button, params);
            added = true;
        } catch (RuntimeException error) {
            button = null; params = null;
            throw error;
        }
    }
    private void clamp() {
        WindowMetrics metrics = windows.getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        android.graphics.Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        params.x = Math.max(safe.left, Math.min(params.x, bounds.width() - safe.right - params.width));
        params.y = Math.max(safe.top, Math.min(params.y, bounds.height() - safe.bottom - params.height));
    }
    private void placeSavedPosition() {
        Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        params.x = Math.round(prefs.getFloat("floating_x", 1f) * Math.max(1, bounds.width() - params.width));
        params.y = Math.round(prefs.getFloat("floating_y", 0.35f) * Math.max(1, bounds.height() - params.height));
        clamp();
    }
    void reposition() {
        if (button == null) return;
        placeSavedPosition(); windows.updateViewLayout(button, params);
    }
    void hide() {
        if (button == null) return;
        button.setVisibility(View.INVISIBLE);
        // addView registers a root before its first attach/layout. Even in that interval,
        // remove the registered window; dropping the reference would orphan the overlay.
        if (added) windows.removeViewImmediate(button);
        added = false; button = null; params = null;
    }
}
