package local.ken.foldguard;
import android.os.Bundle;
interface IDisplaySettings {
    void destroy() = 16777114;
    Bundle state() = 1;
    Bundle preview(int width, int height, int density, boolean defaults) = 2;
    Bundle finishTrial(long trial, boolean keep) = 3;
}
