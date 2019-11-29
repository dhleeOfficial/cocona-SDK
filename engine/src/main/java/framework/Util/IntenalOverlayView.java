package framework.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class IntenalOverlayView extends View {
    private Context context;
    private boolean isFocus = false;
    private PointF touchPoint;

    public IntenalOverlayView(Context context) {
        super(context);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public IntenalOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public IntenalOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setFocus(boolean isFocus, PointF pointF) {
        this.isFocus = isFocus;
        this.touchPoint = pointF;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isFocus == true) {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(touchPoint.x, touchPoint.y, 80, paint);

            isFocus = false;
        } else {
            super.onDraw(canvas);
        }
    }
}
