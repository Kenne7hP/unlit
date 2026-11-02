package local.ken.foldguard;

/** All input/display transitions and polling share this lock; workers never own a session. */
final class GuardController {
    interface Device {
        String open(boolean keysOnly, boolean probe);
        long poll();
        void close();
        void beat();
        boolean fingersDown();
        long now();
        void sleep(long millis);
        void screenOff() throws Exception;
        boolean displayChanged() throws Exception;
        void restoreDisplay() throws Exception;
    }
    private final Device device;
    private final DoublePress startKeys = new DoublePress(3), restoreKeys = new DoublePress();
    private boolean alive, running, probe, armed;
    private long lastBeat, started;
    private String status = "未初始化";

    GuardController(Device device) { this.device = device; }
    synchronized String initialize() {
        alive = true;
        lastBeat = device.now();
        String error = device.open(true, false);
        if (!error.isEmpty()) { close(); return status = "按键监听失败：" + error; }
        return status = "READY";
    }
    synchronized void arm(boolean value) {
        armed = value;
        startKeys.reset();
        if ("KEY_START".equals(status)) status = "KEYS_RUNNING";
    }
    synchronized String begin(boolean test) {
        if (!alive) return "控制器已退出，请停止服务后重新启用";
        if (running) return "已有会话正在运行";
        if (!test && !armed) return "尚未确认触控测试";
        startKeys.reset(); restoreKeys.reset();
        device.close();
        try {
            String error = device.open(false, test);
            if (!error.isEmpty()) { recover(error); return error; }
            probe = test;
            started = lastBeat = device.now();
            // Mark active before display control, so a partial display failure is restored too.
            running = true;
            if (!test) device.screenOff();
            status = test ? "PROBE_RUNNING" : "GUARD_RUNNING";
            return "OK";
        } catch (Exception error) {
            recover("启动失败：" + message(error));
            return status;
        }
    }
    synchronized String heartbeat() {
        lastBeat = device.now(); device.beat();
        if (!alive) return "CONTROLLER_EXITED:" + status;
        String result = status;
        if ("KEY_START".equals(status)) status = "KEYS_RUNNING";
        return result;
    }
    synchronized String stop() { recover("已恢复"); return status; }
    synchronized void close() { alive = false; armed = false; recover("服务退出"); }

    synchronized boolean tick() {
        if (!alive) return false;
        try {
            long now = device.now();
            if (now - lastBeat > 5000) { alive = false; recover("心跳中断，已恢复"); return false; }
            if (running && probe && now - started > 10500) recover("测试计时异常，已取消");
            if (running && !probe && device.displayChanged()) recover("显示状态变化，已退出");
            long packed = device.poll();
            int event = (int)(packed & 255);
            long when = packed >>> 8;
            if (event == 5) { alive = false; recover("输入设备异常，已退出"); return false; }
            if (event == 6 && running) { recover(probe ? "PROBE_PASSED" : "输入守护已退出"); return alive; }
            if (event == 4) {
                if (running) recover("电源键退出");
                startKeys.reset(); restoreKeys.reset();
            }
            if (event >= 1 && event <= 3) {
                int value = event == 1 ? 1 : event == 2 ? 0 : 2;
                if (running) {
                    if (restoreKeys.event(value, when)) recover("双击音量下键恢复");
                } else if (armed && startKeys.event(value, when)) {
                    status = "KEY_START";
                }
            }
        } catch (Exception error) {
            alive = false; recover("输入监听退出：" + message(error));
        }
        return alive;
    }
    private void recover(String reason) {
        String error = "";
        try { if (running && !probe) device.restoreDisplay(); }
        catch (Exception e) { error = "；显示恢复失败，请按电源键"; }
        finally {
            if (running) {
                long deadline = device.now() + 2000;
                while (device.fingersDown() && device.now() < deadline) device.sleep(20);
            }
            device.close();
            running = false;
            startKeys.reset(); restoreKeys.reset();
            if (alive) {
                String failure = device.open(true, false);
                if (!failure.isEmpty()) { alive = false; error += "；按键监听失败：" + failure; }
            }
            status = reason + error;
        }
    }
    private static String message(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }
}
