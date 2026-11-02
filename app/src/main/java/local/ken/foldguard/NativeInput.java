package local.ken.foldguard;

final class NativeInput {
    static native String start(boolean probe);
    static native String watchKeys(); // Observes physical keys without opening/grabbing touch devices.
    static native long pollEvent(); // low byte: 1 down, 2 up, 3 repeat, 4 power, 5 lost, 6 probe expiry
    static native void heartbeat();
    static native boolean fingersDown();
    static native void stop();
    private NativeInput() {}
}
