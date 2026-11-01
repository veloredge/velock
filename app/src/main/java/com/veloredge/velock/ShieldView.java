package com.veloredge.velock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Invisible full-screen surface. While it is attached every touch is delivered here
 * instead of the app underneath, which is what "the display is frozen" means on a
 * non-rooted device: the screen still shows, but it stops taking input. The hardware
 * keys and the system gesture areas stay with the system, as Android requires.
 */
@SuppressLint({"ViewConstructor", "ClickableViewAccessibility"})
final class ShieldView extends View {

    interface Host {
        void onShieldTap();
    }

    private final Host host;

    ShieldView(Context context, Host host) {
        super(context);
        this.host = host;
        // Focusable so the window takes key focus and can swallow BACK.
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            host.onShieldTap();
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Swallow BACK so the frozen screen cannot be navigated out of.
        return true;
    }
}
