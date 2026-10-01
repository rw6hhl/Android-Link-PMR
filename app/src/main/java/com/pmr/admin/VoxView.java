package com.pmr.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* Кастомный View для отображения VOX-индикатора V1.0.
 *
 * Что рисует:
 *   - горизонтальная полоса (10..500) — текущий уровень сигнала;
 *   - чёрная вертикальная метка My_Vox (красная, жирная) — порог срабатывания;
 *   - чёрная вертикальная метка vox_pause (зелёная, жирная) — удержание;
 *   - числовые значения обеих меток над полосой.
 *
 * Взаимодействие:
 *   - касание по полосе — перемещает My_Vox;
 *   - длинное касание — перемещает vox_pause (нужно для разделения).
 *   - vox_pause никогда не выше My_Vox.
 */
public class VoxView extends View {

    public interface Listener {
        void onVoxChanged(int myVox, int voxPause);
    }

    private static final int MIN_LEVEL = 10;
    private static final int MAX_LEVEL = 500;

    private int currentRms = 0;
    private int myVox = 40;
    private int voxPause = 15;
    private Listener listener;

    private final Paint paintBarBg      = new Paint();
    private final Paint paintBarFill    = new Paint();
    private final Paint paintVoxMark    = new Paint();
    private final Paint paintPauseMark  = new Paint();
    private final Paint paintText       = new Paint();
    private final Paint paintTicks      = new Paint();

    public VoxView(Context c) { super(c); init(); }
    public VoxView(Context c, AttributeSet a) { super(c, a); init(); }
    public VoxView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        paintBarBg.setColor(Color.WHITE);
        paintBarBg.setStyle(Paint.Style.FILL);

        paintBarFill.setColor(Color.BLACK);
        paintBarFill.setStyle(Paint.Style.FILL);

        paintVoxMark.setColor(Color.RED);
        paintVoxMark.setStrokeWidth(6f);
        paintVoxMark.setStyle(Paint.Style.STROKE);

        paintPauseMark.setColor(Color.GREEN);
        paintPauseMark.setStrokeWidth(6f);
        paintPauseMark.setStyle(Paint.Style.STROKE);

        paintText.setColor(Color.BLACK);
        paintText.setTextSize(36f);
        paintText.setAntiAlias(true);

        paintTicks.setColor(Color.DKGRAY);
        paintTicks.setStrokeWidth(2f);
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
        int barTop = h / 2 - 20;
        int barBottom = h / 2 + 20;
        int barLeft = padding;
        int barRight = w - padding;

        /* Фон полосы — белый, окантовка чёрная. */
        paintBarBg.setColor(Color.WHITE);
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paintBarBg);

        /* Чёрная полоса заполнения по уровню сигнала. */
        int fillRight = barLeft + (int) ((barRight - barLeft)
                * (currentRms - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        if (fillRight < barLeft) fillRight = barLeft;
        if (fillRight > barRight) fillRight = barRight;
        canvas.drawRect(barLeft, barTop, fillRight, barBottom, paintBarFill);

        /* Метка My_Vox — жирная красная вертикальная линия. */
        int voxX = barLeft + (int) ((barRight - barLeft)
                * (myVox - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(voxX, barTop - 40, voxX, barBottom + 40, paintVoxMark);

        /* Метка vox_pause — жирная зелёная вертикальная линия. */
        int pauseX = barLeft + (int) ((barRight - barLeft)
                * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
        canvas.drawLine(pauseX, barTop - 20, pauseX, barBottom + 20, paintPauseMark);

        /* Числа над метками. */
        String sVox = "My_Vox=" + myVox;
        String sPause = "pause=" + voxPause;
        canvas.drawText(sVox, barLeft, barTop - 60, paintText);
        canvas.drawText(sPause, barLeft, barBottom + 90, paintText);

        /* Текущий RMS — справа сверху. */
        canvas.drawText("RSS=" + currentRms,
                barRight - 200, barTop - 60, paintText);
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

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            /* Если касание близко к vox_pause — двигаем vox_pause.
             * Иначе — My_Vox. */
            int pauseX = barLeft + (int) ((barRight - barLeft)
                    * (voxPause - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL));
            if (Math.abs(x - pauseX) < 40) {
                setVoxPause(value);
            } else {
                setMyVox(value);
            }
            if (listener != null) listener.onVoxChanged(myVox, voxPause);
            return true;
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
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