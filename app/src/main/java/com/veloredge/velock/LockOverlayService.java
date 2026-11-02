package com.veloredge.velock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Owns the floating padlock and the touch shield.
 *
 * <p>Battery model: the service is completely event driven. No wake lock, no polling, no
 * repeating animation and no sensor listener. While idle there is exactly one small
 * window attached; the full-screen shield only exists while the display is locked.
 */
public final class LockOverlayService extends Service
        implements BubbleView.Host, ShieldView.Host {

    private static final String ACTION_START = "com.veloredge.velock.action.START";
    private static final String ACTION_STOP = "com.veloredge.velock.action.STOP";
    private static final String ACTION_LOCK = "com.veloredge.velock.action.LOCK";

    private static final String CHANNEL_ID = "display_lock";
    private static final int NOTIFICATION_ID = 41;

    private static final float ALPHA_IDLE = 0.46f;
    private static final float ALPHA_HIDDEN = 0f;
    private static final float ALPHA_REVEALED = ALPHA_IDLE;
    private static final long HIDE_DELAY_MS = 3000L;

    private static final int BUBBLE_DP = 64;
    private static final int TRASH_DP = 68;
    private static final int TRASH_MARGIN_DP = 72;
    private static final int TRASH_REACH_DP = 54;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private int bubbleSize;
    private int screenWidth;
    private int screenHeight;

    private BubbleView bubble;
    private WindowManager.LayoutParams bubbleParams;

    private ShieldView shield;

    private TrashView trash;
    private WindowManager.LayoutParams trashParams;

    private boolean locked;
    private boolean revealed;
    private boolean stopping;
    private boolean dragFramePending;
    private int dragStartX;
    private int dragStartY;

    /** Applies the latest drag position once per frame rather than once per touch sample. */
    private final Runnable applyDragFrame = new Runnable() {
        @Override
        public void run() {
            dragFramePending = false;
            if (bubble == null || bubbleParams == null) {
                return;
            }
            try {
                windowManager.updateViewLayout(bubble, bubbleParams);
            } catch (Exception ignored) {
                // Window detached mid-drag.
            }
        }
    };

    private final Runnable dimBubble = new Runnable() {
        @Override
        public void run() {
            if (locked && revealed) {
                revealed = false;
                applyBubbleAlpha();
            }
        }
    };

    // ---------------------------------------------------------------- entry points

    static void start(Context context) {
        launch(context, ACTION_START);
    }

    static void stop(Context context) {
        launch(context, ACTION_STOP);
    }

    static void freezeNow(Context context) {
        launch(context, ACTION_LOCK);
    }

    private static void launch(Context context, String action) {
        if (ACTION_STOP.equals(action)) {
            // Stopping is not a foreground start; asking for one can throw on Android 12+
            // and would raise a notification just to tear it down again.
            context.stopService(new Intent(context, LockOverlayService.class));
            return;
        }
        Intent intent = new Intent(context, LockOverlayService.class).setAction(action);
        try {
            context.startForegroundService(intent);
        } catch (Exception e) {
            // Android 12+ may refuse a foreground start from the background. The app UI is
            // the one place that is always allowed, so send the user there instead.
            try {
                context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // Nothing sensible left to do.
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshMetrics();
        createChannel();
        goForeground();
        LockState.running = true;
        attachBubble();
        LockState.publish(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_LOCK.equals(action)) {
            lock(true);
        } else if (bubble == null) {
            attachBubble();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        dragFramePending = false;
        detachBubble();
        removeShield();
        removeTrashNow();
        handler.removeCallbacksAndMessages(null);
        LockState.running = false;
        LockState.locked = false;
        LockState.publish(this);
        super.onDestroy();
    }

    private void shutdown() {
        if (stopping) {
            return;
        }
        stopping = true;
        dragFramePending = false;
        detachBubble();
        removeShield();
        removeTrashNow();
        handler.removeCallbacksAndMessages(null);
        locked = false;
        LockState.running = false;
        LockState.locked = false;
        LockState.publish(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ---------------------------------------------------------------- lock state

    private void lock(boolean value) {
        if (locked == value || bubble == null) {
            return;
        }
        if (value && !Settings.canDrawOverlays(this)) {
            return;
        }

        handler.removeCallbacks(dimBubble);

        if (value) {
            addShield();
            if (shield == null) {
                // The system refused the overlay; do not pretend the screen is frozen.
                return;
            }
            locked = true;
            LockState.locked = true;

            if (!raiseBubble()) {
                // Without a padlock above the shield the phone would be frozen with no way
                // out. Undo the freeze instead.
                removeShield();
                locked = false;
                LockState.locked = false;
                updateNotification();
                LockState.publish(this);
                return;
            }
            bubble.setLocked(true);
            revealed = true;
            applyBubbleAlpha();
            bubble.pulse();
            // Show the padlock for a moment after freezing, then let it fade back so the
            // screen stays clean.
            handler.postDelayed(dimBubble, HIDE_DELAY_MS);
        } else {
            locked = false;
            LockState.locked = false;

            removeShield();
            revealed = false;
            bubble.setLocked(false);
            applyBubbleAlpha();
        }

        updateNotification();
        LockState.publish(this);
    }

    private void applyBubbleAlpha() {
        if (bubble == null) {
            return;
        }
        float target = !locked ? ALPHA_IDLE : (revealed ? ALPHA_REVEALED : ALPHA_HIDDEN);
        long duration = target == ALPHA_HIDDEN ? 300L : 120L;
        bubble.animate().alpha(target).setDuration(duration).start();
    }

    /**
     * Wakes a hidden padlock for another {@link #HIDE_DELAY_MS}, then it fades again.
     * A tap on the frozen screen is the only way in, so it must not be permanent.
     */
    private void reveal() {
        handler.removeCallbacks(dimBubble);
        revealed = true;
        applyBubbleAlpha();
        if (bubble != null) {
            bubble.pulse();
        }
        handler.postDelayed(dimBubble, HIDE_DELAY_MS);
    }

    @Override
    public void onBubbleTap() {
        if (locked && !revealed) {
            // A tap on the invisible padlock wakes it; it never unlocks what cannot be seen.
            reveal();
            return;
        }
        final boolean target = !locked;
        // Re-adding a window from inside its own touch callback is asking for trouble,
        // so the transition runs on the next loop iteration.
        handler.post(() -> lock(target));
    }

    @Override
    public void onShieldTap() {
        if (!locked || bubble == null) {
            return;
        }
        if (!revealed) {
            reveal();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Rotation or a display-size change moves the usable area and rescales dp, so the
        // padlock is remeasured and pulled back into reach.
        hideTrash();
        refreshMetrics();
        if (bubble == null || bubbleParams == null) {
            return;
        }
        bubbleParams.width = bubbleSize;
        bubbleParams.height = bubbleSize;
        bubbleParams.x = clampX(bubbleParams.x);
        bubbleParams.y = clampY(bubbleParams.y);
        try {
            windowManager.updateViewLayout(bubble, bubbleParams);
        } catch (Exception ignored) {
            // Window already detached.
        }
    }

    // ---------------------------------------------------------------- bubble window

    private void attachBubble() {
        if (bubble != null) {
            return;
        }
        bubble = new BubbleView(this, this);

        int x = Store.x(this);
        int y = Store.y(this);
        if (x < 0 || y < 0) {
            x = screenWidth - bubbleSize - Ui.dp(this, 10);
            y = Math.round(screenHeight * 0.30f);
        }

        bubbleParams = new WindowManager.LayoutParams(
                bubbleSize, bubbleSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = clampX(x);
        bubbleParams.y = clampY(y);
        bubbleParams.setTitle("VeLock padlock");
        if (Build.VERSION.SDK_INT >= 30) {
            bubbleParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        bubble.setAlpha(ALPHA_IDLE);
        try {
            windowManager.addView(bubble, bubbleParams);
        } catch (Exception e) {
            bubble = null;
            bubbleParams = null;
            shutdown();
        }
    }

    private void detachBubble() {
        if (bubble == null) {
            return;
        }
        BubbleView view = bubble;
        bubble = null;
        bubbleParams = null;
        try {
            windowManager.removeViewImmediate(view);
        } catch (Exception ignored) {
            // Already gone.
        }
    }

    /**
     * Re-adds the padlock so it sits above the freshly attached shield.
     *
     * @return false when the window could not be re-attached, in which case the caller must
     *     not freeze the screen.
     */
    private boolean raiseBubble() {
        if (bubble == null || bubbleParams == null) {
            return false;
        }
        int x = bubbleParams.x;
        int y = bubbleParams.y;
        hideTrash();
        try {
            windowManager.removeViewImmediate(bubble);
        } catch (Exception ignored) {
            // Not attached.
        }
        bubbleParams.x = x;
        bubbleParams.y = y;
        try {
            windowManager.addView(bubble, bubbleParams);
        } catch (Exception e) {
            bubble = null;
            bubbleParams = null;
            return false;
        }
        bubble.setAlpha(revealed ? ALPHA_REVEALED : ALPHA_HIDDEN);
        return true;
    }

    private void removeBubble() {
        if (bubble == null) {
            shutdown();
            return;
        }
        bubble.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(110L)
                .withEndAction(this::shutdown)
                .start();
    }

    // ---------------------------------------------------------------- shield window

    private void addShield() {
        if (shield != null) {
            return;
        }
        shield = new ShieldView(this, this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("VeLock shield");
        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        try {
            windowManager.addView(shield, params);
            shield.requestFocus();
        } catch (Exception e) {
            shield = null;
        }
    }

    private void removeShield() {
        if (shield == null) {
            return;
        }
        ShieldView view = shield;
        shield = null;
        try {
            windowManager.removeViewImmediate(view);
        } catch (Exception ignored) {
            // Already gone.
        }
    }

    // ---------------------------------------------------------------- drag and drop

    @Override
    public boolean canDrag() {
        // A padlock the user cannot see must not be draggable: a swipe over the invisible
        // spot would otherwise drop the lock on the bin and thaw the screen unseen.
        return !locked || revealed;
    }

    @Override
    public void onBubbleDragStart() {
        if (bubbleParams == null) {
            return;
        }
        handler.removeCallbacks(dimBubble);
        dragStartX = bubbleParams.x;
        dragStartY = bubbleParams.y;
        showTrash();
    }

    @Override
    public void onBubbleDrag(float dx, float dy, float x, float y) {
        if (bubbleParams == null) {
            return;
        }
        bubbleParams.x = clampX(dragStartX + Math.round(dx));
        bubbleParams.y = clampY(dragStartY + Math.round(dy));
        if (!dragFramePending) {
            dragFramePending = true;
            handler.post(applyDragFrame);
        }
        if (trash != null) {
            trash.setHot(isOverTrash(Math.round(x), Math.round(y)));
        }
    }

    @Override
    public void onBubbleDrop(float x, float y) {
        if (bubbleParams == null) {
            return;
        }
        boolean remove = isOverTrash(Math.round(x), Math.round(y));
        hideTrash();
        if (remove) {
            removeBubble();
            return;
        }
        Store.save(this, bubbleParams.x, bubbleParams.y);
        if (locked && revealed) {
            handler.removeCallbacks(dimBubble);
            handler.postDelayed(dimBubble, HIDE_DELAY_MS);
        }
    }

    private int trashSize() {
        return Ui.dp(this, TRASH_DP);
    }

    private Point trashCenter() {
        int size = trashSize();
        return new Point((screenWidth - size) / 2 + size / 2,
                screenHeight - Ui.dp(this, TRASH_MARGIN_DP) - size + size / 2);
    }

    private boolean isOverTrash(int x, int y) {
        Point c = trashCenter();
        double distance = Math.hypot(x - c.x, y - c.y);
        return distance <= Ui.dp(this, TRASH_REACH_DP);
    }

    private void showTrash() {
        if (trash != null) {
            return;
        }
        trash = new TrashView(this);
        int size = trashSize();
        trashParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        trashParams.gravity = Gravity.TOP | Gravity.START;
        trashParams.x = (screenWidth - size) / 2;
        trashParams.y = screenHeight - Ui.dp(this, TRASH_MARGIN_DP) - size;
        trashParams.setTitle("VeLock remove target");
        if (Build.VERSION.SDK_INT >= 30) {
            trashParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        trash.setAlpha(0f);
        trash.setScaleX(0.7f);
        trash.setScaleY(0.7f);
        try {
            windowManager.addView(trash, trashParams);
        } catch (Exception e) {
            trash = null;
            trashParams = null;
            return;
        }
        trash.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(110L).start();
    }

    private void hideTrash() {
        if (stopping) {
            removeTrashNow();
            return;
        }
        if (trash == null) {
            return;
        }
        final TrashView view = trash;
        trash = null;
        trashParams = null;
        view.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(96L)
                .withEndAction(() -> {
                    try {
                        windowManager.removeViewImmediate(view);
                    } catch (Exception ignored) {
                        // Already gone.
                    }
                })
                .start();
    }

    /** Drops the bin immediately; used on the way out so nothing is left behind. */
    private void removeTrashNow() {
        TrashView view = trash;
        trash = null;
        trashParams = null;
        if (view == null) {
            return;
        }
        try {
            windowManager.removeViewImmediate(view);
        } catch (Exception ignored) {
            // Already gone.
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Measured at start and on every configuration change, never inside a touch move. */
    private void refreshMetrics() {
        bubbleSize = Ui.dp(this, BUBBLE_DP);
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
    }

    private int clampX(int x) {
        return Math.max(0, Math.min(x, screenWidth - bubbleSize));
    }

    private int clampY(int y) {
        return Math.max(0, Math.min(y, screenHeight - bubbleSize));
    }

    // ---------------------------------------------------------------- notification

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(channel);
    }

    private void goForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        try {
            nm.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
            // Notifications may be disabled; the service still works.
        }
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent off = PendingIntent.getService(this, 1,
                new Intent(this, LockOverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_lock)
                .setContentTitle(getString(locked ? R.string.notif_locked_title
                        : R.string.notif_ready_title))
                .setContentText(getString(locked ? R.string.notif_locked_text
                        : R.string.notif_ready_text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_tile_lock),
                        getString(R.string.notif_turn_off), off).build())
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }
}
