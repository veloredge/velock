package com.veloredge.velock;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;

import java.lang.ref.WeakReference;

/**
 * In-process state shared by the overlay service, the Quick Settings tile and the
 * activity. Everything lives in one process, so a plain singleton beats broadcasting
 * and costs nothing while idle.
 */
final class LockState {

    interface Observer {
        void onLockStateChanged();
    }

    static volatile boolean running;
    static volatile boolean locked;

    private static WeakReference<Observer> observer;

    private LockState() {
    }

    static void observe(Observer o) {
        observer = o == null ? null : new WeakReference<>(o);
    }

    /** Tells the activity (if it is on screen) and the system tile that state moved. */
    static void publish(Context context) {
        Observer o = observer == null ? null : observer.get();
        if (o != null) {
            o.onLockStateChanged();
        }
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context, LockTileService.class));
        } catch (Throwable ignored) {
            // Tile not available on this build; nothing else to do.
        }
    }
}
