package com.veloredge.velock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Drop target that only exists during a drag. Release the padlock on it to remove the
 * lock completely.
 */
@SuppressLint({"ViewConstructor"})
final class TrashView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final int colorFill;
    private final int colorBorder;
    private final int colorGlyph;
    private final int colorFillHot;
    private final int colorBorderHot;
    private final int colorGlyphHot;

    private boolean hot;

    TrashView(Context context) {
        super(context);
        colorFill = context.getColor(R.color.trash_fill);
        colorBorder = context.getColor(R.color.trash_border);
        colorGlyph = context.getColor(R.color.trash_glyph);
        colorFillHot = context.getColor(R.color.accent);
        colorBorderHot = context.getColor(R.color.accent);
        colorGlyphHot = context.getColor(R.color.ink);

        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeWidth(Ui.dpf(context, 1.9f));
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);

        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dpf(context, 1.4f));

        applyColors();
    }

    void setHot(boolean value) {
        if (hot == value) {
            return;
        }
        hot = value;
        applyColors();
        animate().scaleX(hot ? 1.12f : 1f).scaleY(hot ? 1.12f : 1f).setDuration(90L).start();
        invalidate();
    }

    private void applyColors() {
        fill.setColor(hot ? colorFillHot : colorFill);
        border.setColor(hot ? colorBorderHot : colorBorder);
        glyph.setColor(hot ? colorGlyphHot : colorGlyph);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float cx = getWidth() * 0.5f;
        final float cy = getHeight() * 0.5f;
        final float radius = Math.min(cx, cy) - Ui.dpf(getContext(), 1f);

        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius, border);
        drawGlyph(canvas, cx, cy);
    }

    private void drawGlyph(Canvas canvas, float cx, float cy) {
        final float u = Ui.dpf(getContext(), 1f);

        // Lid
        canvas.drawLine(cx - 9f * u, cy - 6f * u, cx + 9f * u, cy - 6f * u, glyph);
        // Handle
        path.reset();
        path.moveTo(cx - 3.5f * u, cy - 6f * u);
        path.lineTo(cx - 3.5f * u, cy - 9f * u);
        path.lineTo(cx + 3.5f * u, cy - 9f * u);
        path.lineTo(cx + 3.5f * u, cy - 6f * u);
        canvas.drawPath(path, glyph);
        // Bin
        path.reset();
        path.moveTo(cx - 7f * u, cy - 3.4f * u);
        path.lineTo(cx - 6.2f * u, cy + 8.6f * u);
        path.quadTo(cx - 6.1f * u, cy + 9.4f * u, cx - 5.2f * u, cy + 9.4f * u);
        path.lineTo(cx + 5.2f * u, cy + 9.4f * u);
        path.quadTo(cx + 6.1f * u, cy + 9.4f * u, cx + 6.2f * u, cy + 8.6f * u);
        path.lineTo(cx + 7f * u, cy - 3.4f * u);
        canvas.drawPath(path, glyph);
        // Rungs
        canvas.drawLine(cx - 2.4f * u, cy + 0.6f * u, cx - 2.4f * u, cy + 5.6f * u, glyph);
        canvas.drawLine(cx + 2.4f * u, cy + 0.6f * u, cx + 2.4f * u, cy + 5.6f * u, glyph);
    }
}
