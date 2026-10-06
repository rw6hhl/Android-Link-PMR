package com.pmr.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* VoxView V1.9.
 *
 * Изменения V1.9:
 *   - убрана жёлтая заливка (она закрывала красную);
 *   - оставлена только красная полоса от My_Vox до текущего RMS;
 *   - фон полосы — белый;
 *   - одна метка My_Vox (красная), шкала 0..1000.
 */
public class VoxView extends View {

    public interface Listener {
        void onVoxChanged(int myVox);
    }

    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 1000;
    private static final int EDGE_MARGIN_DP = 24;

    private int currentRms = 0;
    private int myVox = 400;
    private boolean locked = false;
    private Listener listener;

    private final Paint paintBarBg     = new Paint();
    private final Paint paintBarRed    = new Paint();
    private final Paint paintBorder    = new Paint();
    private final Paint paintVoxMark   = new Paint();
    private final Paint paintText      = new Paint();

    private float density = 1.0f;

    public VoxView(Context c) { super(c); init(); }
    public VoxView(Context c, AttributeSet a) { super(c, a); init(); }
    public VoxView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        paintBarBg.setColor(Color.WHITE);
        paintBarBg.setStyle(Paint.Style.FILL);

        paintBarRed.setColor(Color.RED);
        paintBarRed.setStyle(Paint.Style.FILL);

        paintBorder.setColor(Color.DKGRAY);
        paintBorder.setStrokeWidth(2f);
        paintBorder.setStyle(Paint.Style.STROKE);

        paintVoxMark.setColor(Color.RED);
        paintVoxMark.setStrokeWidth(8f);
        paintVoxMark.setStyle(Paint.Style.STROKE);

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
        invalidate();
    }
    public int getMyVox() { return myVox; }
    public void setLocked(boolean b) {
        this.locked = b;
        invalidate();
    }
    public boolean isLocked() { return locked; }

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

        /* Фон полосы — белый. */
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBarBg);

        /* Позиция метки My_Vox. */
        int voxX = barLeft + edge + (int) ((barRight - barLeft - 2 * edge)
                * (myVox - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));

        /* Позиция текущего RMS. */
        int rmsX = barLeft + edge + (int) ((barRight - barLeft - 2 * edge)
                * (currentRms - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        if (rmsX < barLeft + edge) rmsX = barLeft + edge;
        if (rmsX > barRight - edge) rmsX = barRight - edge;

        /* Красная заливка — только от My_Vox до RMS, если RMS > My_Vox. */
        if (rmsX > voxX) {
            canvas.drawRect(voxX, barTop, rmsX, barBottom, paintBarRed);
        }

        /* Окантовка полосы. */
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBorder);

        /* Красная метка My_Vox. */
        canvas.drawLine(voxX, barTop - (int) (50 * density),
                voxX, barBottom + (int) (50 * density), paintVoxMark);

        /* Надпись My_Vox над полосой. */
        canvas.drawText("My_Vox=" + myVox, barLeft,
                barTop - (int) (70 * density), paintText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) return true;

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
            setMyVox(value);
            if (listener != null) listener.onVoxChanged(myVox);
            return true;
        }
        return super.onTouchEvent(e);
    }
}