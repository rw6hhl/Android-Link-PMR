package com.pmr.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* VoxView V1.2.
 *
 * Изменения V1.2:
 *   - метки My_Vox и vox_pause не прижимаются к краю (минимум 24dp);
 *   - работают с шкалой 0..1000.
 */
public class VoxView extends View {

    public interface Listener {
        void onVoxChanged(int myVox, int voxPause);
    }

    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 1000;
    private static final int EDGE_MARGIN_DP = 24;

    private int currentRms = 0;
    private int myVox = 400;
    private int voxPause = 15;
    private Listener listener;

    private final Paint paintBarBg     = new Paint();
    private final Paint paintBarFill   = new Paint();
    private final Paint paintBorder    = new Paint();
    private final Paint paintVoxMark   = new Paint();
    private final Paint paintPauseMark = new Paint();
    private final Paint paintText      = new Paint();

    private float density = 1.0f;

    public VoxView(Context c) { super(c); init(); }
    public VoxView(Context c, AttributeSet a) { super(c, a); init(); }
    public VoxView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        paintBarBg.setColor(Color.BLACK);
        paintBarBg.setStyle(Paint.Style.FILL);

        paintBarFill.setColor(Color.WHITE);
        paintBarFill.setStyle(Paint.Style.FILL);

        paintBorder.setColor(Color.GRAY);
        paintBorder.setStrokeWidth(2f);
        paintBorder.setStyle(Paint.Style.STROKE);

        paintVoxMark.setColor(Color.RED);
        paintVoxMark.setStrokeWidth(8f);
        paintVoxMark.setStyle(Paint.Style.STROKE);

        paintPauseMark.setColor(Color.GREEN);
        paintPauseMark.setStrokeWidth(6f);
        paintPauseMark.setStyle(Paint.Style.STROKE);

        paintText.setColor(Color.BLACK);
        paintText.setTextSize(36f);
        paintText.setAntiAlias(true);
    }

    public void setListener(Listener l) { this.listener = l; }
    public void setCurrentRms(int rms) {
        this.currentRms = rms;
        invalidate();
    }
    public void setMyVox(int v) {
        if (v < MIN_LEVEL) v = MIN_LEVEL;
        if (v > MAX_LEVEL) v = MAX_LEVEL;
        this.myVox = v;
        if (voxPause > myVox) voxPause = myVox;
        invalidate();
    }
    public void setVoxPause(int v) {
        if (v < MIN_LEVEL) v = MIN_LEVEL;
        if (v > myVox) v = myVox;
        this.voxPause = v;
        invalidate();
    }
    public int getMyVox() { return myVox; }
    public int getVoxPause() { return voxPause; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        int padding = (int) (16 * density);
        int edge = (int) (EDGE_MARGIN_DP * density);
        int barTop = h / 2 - (int) (25 * density);
        int barBottom = h / 2 + (int) (25 * density);
        int barLeft = padding;
        int barRight = w - padding;

        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBarBg);

        int fillRight = barLeft + (int) ((barRight - barLeft)
                * (currentRms - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        if (fillRight < barLeft) fillRight = barLeft;
        if (fillRight > barRight) fillRight = barRight;
        canvas.drawRect(barLeft, barTop, fillRight, barBottom, paintBarFill);

        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBorder);

        int voxX = barLeft + edge + (int) ((barRight - barLeft - 2 * edge)
                * (myVox - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(voxX, barTop - (int)(50 * density),
                voxX, barBottom + (int)(50 * density), paintVoxMark);

        int pauseX = barLeft + edge + (int) ((barRight - barLeft - 2 * edge)
                * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(pauseX, barTop - (int)(30 * density),
                pauseX, barBottom + (int)(30 * density), paintPauseMark);

        canvas.drawText("My_Vox=" + myVox, barLeft,
                barTop - (int)(70 * density), paintText);
        canvas.drawText("pause=" + voxPause, barLeft,
                barBottom + (int)(100 * density), paintText);
        canvas.drawText("RMS=" + currentRms,
                barRight - (int)(250 * density),
                barTop - (int)(70 * density), paintText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int padding = (int) (16 * density);
        int edge = (int) (EDGE_MARGIN_DP * density);
        int barLeft = padding;
        int barRight = w - padding;
        float x = e.getX();
        if (x < barLeft + edge) x = barLeft + edge;
        if (x > barRight - edge) x = barRight - edge;

        int value = MIN_LEVEL + (int) ((MAX_LEVEL - MIN_LEVEL)
                * (x - barLeft - edge)
                / (float) (barRight - barLeft - 2 * edge));

        if (e.getAction() == MotionEvent.ACTION_DOWN
                || e.getAction() == MotionEvent.ACTION_MOVE) {
            int pauseX = barLeft + edge + (int) ((barRight - barLeft - 2 * edge)
                    * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
            if (Math.abs(x - pauseX) < (int)(40 * density)) {
                setVoxPause(value);
            } else {
                setMyVox(value);
            }
            if (listener != null) listener.onVoxChanged(myVox, voxPause);
            return true;
        }
        return super.onTouchEvent(e);
    }
}