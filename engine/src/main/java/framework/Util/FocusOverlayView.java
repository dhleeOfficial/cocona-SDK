package framework.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class FocusOverlayView extends View {
    private Context context;
    private boolean isFocus = false;
    private PointF touchPoint;
    private int color;

    public FocusOverlayView(Context context) {
        super(context);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public FocusOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public FocusOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setFocus(boolean isFocus, PointF pointF, int color) {
        this.isFocus = isFocus;
        this.touchPoint = pointF;
        this.color = color;
    }

    @Override
    public void draw(Canvas canvas) {
        if (isFocus == true) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
            canvas.drawCircle(touchPoint.x, touchPoint.y, 80, paint);

            isFocus = false;
        } else {
            super.draw(canvas);
        }

    }
}
