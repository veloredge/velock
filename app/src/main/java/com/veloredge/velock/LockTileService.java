package com.veloredge.velock;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * The Quick Settings tile. Tapping it floats the padlock, or takes it away again.
 */
public final class LockTileService extends TileService {

    @Override
    public void onTileAdded() {
        sync(LockState.running);
    }

    @Override
    public void onStartListening() {
        sync(LockState.running);
    }

    @Override
    public void onClick() {
        if (!Settings.canDrawOverlays(this)) {
            openApp();
            return;
        }
        boolean turnOn = !LockState.running;
        if (turnOn) {
            LockOverlayService.start(this);
        } else {
            LockOverlayService.stop(this);
        }
        sync(turnOn);
    }

    private void sync(boolean active) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_label));
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(getString(active && LockState.locked
                    ? R.string.tile_subtitle_locked
                    : active ? R.string.tile_subtitle_active : R.string.tile_subtitle_off));
        }
        tile.updateTile();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void openApp() {
        // Before Android 14 the Intent overload is the only way to collapse the shade
        // and open the activity; the PendingIntent variant does not exist there.
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_NEEDS_OVERLAY, true);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
