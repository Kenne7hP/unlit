package local.ken.foldguard;

import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs as a dedicated Shizuku/Sui user service, independently of the touch guard. */
public final class DisplaySettingsService extends IDisplaySettings.Stub {
    private final Context context;
    private Process trial;
    private long deadline;
    private String result = "";

    public DisplaySettingsService(Context context) { this.context = context; }

    private void authorize() {
        if (Binder.getCallingUid() != context.getApplicationInfo().uid || !"TB321FU".equals(Build.MODEL))
            throw new SecurityException("显示设置仅适用于 Y700");
    }

    private String[] readSetting(String kind, String format) throws Exception {
        Process command = new ProcessBuilder("/system/bin/wm", kind).redirectErrorStream(true).start();
        String physical = null, override = "reset";
        Pattern line = Pattern.compile("(Physical|Override) " + kind + ": (" + format + ")");
        try (BufferedReader reader = reader(command)) {
            for (String s; (s = reader.readLine()) != null;) {
                Matcher m = line.matcher(s.trim());
                if (!m.matches()) throw new IOException("无法识别 " + kind + " 当前值");
                if ("Physical".equals(m.group(1))) physical = m.group(2);
                else override = m.group(2);
            }
        }
        if (command.waitFor() != 0 || physical == null) throw new IOException("无法读取 " + kind);
        return new String[]{physical, override};
    }

    private static BufferedReader reader(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }

    // The child owns the snapshot and timeout. EOF (including helper process death)
    // takes the same rollback path as an explicit cancel.
    static final String SCRIPT =
            "apply() { wm size \"$1\" && wm density \"$2\"; }\n" +
            "if apply \"$1\" \"$2\"; then\n" +
            " echo READY\n" +
            " if IFS= read -r -t 15 answer && [ \"$answer\" = keep ]; then exit 0; fi\n" +
            "fi\n" +
            "wm size \"$3\"; size_status=$?\n" +
            "wm density \"$4\" && [ \"$size_status\" = 0 ] && exit 2\nexit 3";

    private void collect() {
        if (trial == null || trial.isAlive()) return;
        int code = trial.exitValue();
        result = code == 0 ? "已保留显示设置" : code == 2 ? "已恢复试用前的设置" : "恢复失败，请检查当前显示设置";
        trial = null;
        deadline = 0;
    }

    @Override public synchronized Bundle state() {
        authorize();
        collect();
        try {
            String[] size = readSetting("size", "[0-9]+x[0-9]+");
            String[] density = readSetting("density", "[0-9]+");
            Bundle b = new Bundle();
            b.putString("size", "reset".equals(size[1]) ? size[0] : size[1]);
            b.putString("density", "reset".equals(density[1]) ? density[0] : density[1]);
            b.putLong("deadline", deadline);
            b.putString("status", result);
            return b;
        } catch (Exception e) { throw new IllegalStateException(e.getMessage()); }
    }

    @Override public synchronized Bundle preview(int w, int h, int dpi, boolean defaults) {
        authorize();
        collect();
        if (trial != null) throw new IllegalStateException("已有试用正在进行");
        if (!defaults && (w < 200 || h < 200 || w > 8192 || h > 8192 || dpi < 72 || dpi > 1000))
            throw new IllegalArgumentException("宽高范围为 200–8192 像素，DPI 范围为 72–1000");
        try {
            String[] size = readSetting("size", "[0-9]+x[0-9]+");
            String[] density = readSetting("density", "[0-9]+");
            trial = new ProcessBuilder("/system/bin/sh", "-c", SCRIPT, "display-trial",
                    defaults ? "reset" : w + "x" + h, defaults ? "reset" : String.valueOf(dpi),
                    size[1], density[1]).start();
            try (BufferedReader r = reader(trial)) {
                if (!"READY".equals(r.readLine())) {
                    trial.getOutputStream().close();
                    trial.waitFor();
                    collect();
                    throw new IOException("应用失败。" + result);
                }
            }
            deadline = SystemClock.elapsedRealtime() + 15000;
            result = "";
            try {
                Bundle applied = state();
                String expectedSize = defaults ? size[0] : w + "x" + h;
                String expectedDensity = defaults ? density[0] : String.valueOf(dpi);
                if (!expectedSize.equals(applied.getString("size"))
                        || !expectedDensity.equals(applied.getString("density")))
                    throw new IOException("设备未接受所选设置");
                return applied;
            } catch (Exception error) {
                if (trial != null) {
                    trial.getOutputStream().close();
                    trial.waitFor();
                    collect();
                }
                throw new IOException(error.getMessage() + "。" + result);
            }
        } catch (Exception e) { throw new IllegalStateException(e.getMessage()); }
    }

    @Override public synchronized Bundle finishTrial(long id, boolean keep) {
        authorize();
        collect();
        if (trial != null && id == deadline) {
            try (OutputStream out = trial.getOutputStream()) {
                if (keep && SystemClock.elapsedRealtime() < deadline)
                    out.write("keep\n".getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignored) { /* Timeout may already have closed the pipe. */ }
            try { trial.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return state();
    }

    @Override public void destroy() { authorize(); System.exit(0); }
}
