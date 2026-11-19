package local.ken.foldguard;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class GuardTile extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) { tile.setState(GuardService.active || GuardService.starting ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); tile.updateTile(); }
    }
    @Override public void onClick() {
        super.onClick();
        if (GuardService.active || GuardService.starting) {
            startForegroundService(new Intent(this, GuardService.class).setAction("RESTORE"));
        } else if (!isLocked() && android.provider.Settings.canDrawOverlays(this) && rikka.shizuku.Shizuku.pingBinder()
                && rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startForegroundService(new Intent(this, GuardService.class).setAction("START"));
        } else {
            Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                PendingIntent open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(open);
            } else {
                openLegacy(intent);
            }
        }
    }

    // Android 13 has only the Intent overload; the target-SDK restriction starts on Android 14.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openLegacy(Intent intent) {
        startActivityAndCollapse(intent);
    }
}
