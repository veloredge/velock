package com.veloredge.velock;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

/**
 * The floating padlock. It draws itself instead of inflating a layout, so the overlay
 * costs one small surface and no view hierarchy. Nothing runs between touches: the
 * pulse is a single one-shot animator that ends and leaves the view idle.
 */
@SuppressLint({"ViewConstructor", "ClickableViewAccessibility"})
final class BubbleView extends View {

    interface Host {
        void onBubbleTap();

        void onBubbleDragStart();

        void onBubbleDrag(float dx, float dy, float x, float y);

        void onBubbleDrop(float x, float y);
    }

    private static final int GLYPH_DP = 26;

    private final Host host;
    private final int slop;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Drawable glyphClosed;
    private final Drawable glyphOpen;

    private boolean locked;
    private float pulse = -1f;
    private ValueAnimator pulseAnim;

    private float downRawX;
    private float downRawY;
    private boolean dragging;
    private boolean touchActive;

    BubbleView(Context context, Host host) {
        super(context);
        this.host = host;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();

        glyphClosed = context.getDrawable(R.drawable.ic_lock_closed).mutate();
        glyphOpen = context.getDrawable(R.drawable.ic_lock_open).mutate();
        glyphClosed.setTint(context.getColor(R.color.accent));
        glyphOpen.setTint(context.getColor(R.color.glyph_idle));

        fill.setColor(context.getColor(R.color.bubble_fill));

        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dpf(context, 1.2f));
        border.setColor(context.getColor(R.color.bubble_border));

        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(Ui.dpf(context, 1.6f));
        pulsePaint.setColor(context.getColor(R.color.accent));
    }

    void setLocked(boolean value) {
        if (locked == value) {
            return;
        }
        locked = value;
        invalidate();
    }

    /** One-shot ring that confirms the padlock is awake. */
    void pulse() {
        if (pulseAnim != null) {
            pulseAnim.cancel();
        }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(520L);
        pulseAnim.setInterpolator(new DecelerateInterpolator());
        pulseAnim.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pulse = -1f;
                invalidate();
            }
        });
        pulseAnim.start();
    }

    void haptic() {
        int kind = Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY;
        performHapticFeedback(kind);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float cx = getWidth() * 0.5f;
        final float cy = getHeight() * 0.5f;
        final float radius = Math.min(cx, cy) - Ui.dpf(getContext(), 4f);

        border.setAlpha(locked ? 0x8C : 0x3D);
        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius, border);

        if (pulse >= 0f) {
            pulsePaint.setAlpha((int) (110f * (1f - pulse)));
            canvas.drawCircle(cx, cy, radius + Ui.dpf(getContext(), 7f) * pulse, pulsePaint);
        }

        Drawable glyph = locked ? glyphClosed : glyphOpen;
        float half = Ui.dpf(getContext(), GLYPH_DP) * 0.5f;
        glyph.setBounds(Math.round(cx - half), Math.round(cy - half),
                Math.round(cx + half), Math.round(cy + half));
        glyph.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchActive = true;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                dragging = false;
                spring(0.94f);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (!touchActive) {
                    return true;
                }
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && dx * dx + dy * dy > slop * slop) {
                    dragging = true;
                    host.onBubbleDragStart();
                }
                if (dragging) {
                    host.onBubbleDrag(dx, dy, event.getRawX(), event.getRawY());
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (!touchActive) {
                    return true;
                }
                touchActive = false;
                spring(1f);
                if (dragging) {
                    dragging = false;
                    host.onBubbleDrop(event.getRawX(), event.getRawY());
                } else {
                    haptic();
                    host.onBubbleTap();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (!touchActive) {
                    return true;
                }
                touchActive = false;
                spring(1f);
                if (dragging) {
                    dragging = false;
                    host.onBubbleDrop(event.getRawX(), event.getRawY());
                }
                return true;

            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // The window is briefly re-attached to sit above the shield. A stream that was open
        // at that moment must never leak into the new attachment as a phantom drag.
        touchActive = false;
        dragging = false;
        setScaleX(1f);
        setScaleY(1f);
    }

    private void spring(float scale) {
        animate().scaleX(scale).scaleY(scale)
                .setDuration(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}
