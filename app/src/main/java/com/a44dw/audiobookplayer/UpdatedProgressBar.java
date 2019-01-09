package com.a44dw.audiobookplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ProgressBar;

public class UpdatedProgressBar extends ProgressBar {

    private String number;
    private Paint paint;
    private Rect r;

    public UpdatedProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.paint = new Paint();
        this.paint.setTextSize(spToPx(14, getContext()));
        this.r = new Rect();
    }

    public void setNumber(int num) {
        this.number = String.valueOf(num);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawCenter(canvas, paint, number);
    }

    private void drawCenter(Canvas canvas, Paint paint, String text) {
        canvas.getClipBounds(r);
        int cHeight = r.height();
        int cWidth = r.width();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.getTextBounds(text, 0, text.length(), r);
        float x = cWidth/2f - r.width()/2f - r.left;
        float y = cHeight - 20f;
        canvas.drawText(text, x, y, paint);
    }

    public static int spToPx(float sp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }
}
