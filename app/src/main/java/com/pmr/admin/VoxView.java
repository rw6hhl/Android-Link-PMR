package com.pmr.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* VoxView V1.1.
 *
 * Изменения V1.1:
 *   - шкала 0..1000;
 *   - уровень отрисовывается всегда (в тишине видно шум);
 *   - полоса белая на чёрном фоне.
 */
public class VoxView extends View {

    public interface Listener {
        void onVoxChanged(int myVox, int voxPause);
    }

    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 1000;

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

    public VoxView(Context c) { super(c); init(); }
    public VoxView(Context c, AttributeSet a) { super(c, a); init(); }
    public VoxView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
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
        int padding = 24;
        int barTop = h / 2 - 25;
        int barBottom = h / 2 + 25;
        int barLeft = padding;
        int barRight = w - padding;

        /* Фон полосы — чёрный. */
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBarBg);

        /* Белая полоса по уровню сигнала. */
        int fillRight = barLeft + (int) ((barRight - barLeft)
                * (currentRms - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        if (fillRight < barLeft) fillRight = barLeft;
        if (fillRight > barRight) fillRight = barRight;
        canvas.drawRect(barLeft, barTop, fillRight, barBottom, paintBarFill);

        /* Окантовка. */
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBorder);

        /* Метка My_Vox — жирная красная. */
        int voxX = barLeft + (int) ((barRight - barLeft)
                * (myVox - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(voxX, barTop - 50, voxX, barBottom + 50, paintVoxMark);

        /* Метка vox_pause — жирная зелёная. */
        int pauseX = barLeft + (int) ((barRight - barLeft)
                * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(pauseX, barTop - 30, pauseX, barBottom + 30, paintPauseMark);

        /* Числа над метками. */
        canvas.drawText("My_Vox=" + myVox, barLeft, barTop - 70, paintText);
        canvas.drawText("pause=" + voxPause, barLeft, barBottom + 100, paintText);
        canvas.drawText("RMS=" + currentRms,
                barRight - 250, barTop - 70, paintText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int padding = 24;
        int barLeft = padding;
        int barRight = w - padding;
        float x = e.getX();
        if (x < barLeft) x = barLeft;
        if (x > barRight) x = barRight;

        int value = MIN_LEVEL + (int) ((MAX_LEVEL - MIN_LEVEL)
                * (x - barLeft) / (float) (barRight - barLeft));

        if (e.getAction() == MotionEvent.ACTION_DOWN
                || e.getAction() == MotionEvent.ACTION_MOVE) {
            int pauseX = barLeft + (int) ((barRight - barLeft)
                    * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
            if (Math.abs(x - pauseX) < 40) {
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