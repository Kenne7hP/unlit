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
        } else if (GuardService.calibrationMatches(getSharedPreferences("guard", MODE_PRIVATE).getString("confirmed", "")) && !isLocked()) {
            startForegroundService(new Intent(this, GuardService.class).setAction("START"));
        } else {
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(open);
        }
    }
}
