package local.ken.foldguard;
interface IGuard {
    void destroy() = 16777114;
    String initialize(String libraryPath) = 1;
    String begin(boolean probe) = 2;
    String heartbeat() = 3;
    String stop() = 4;
}
