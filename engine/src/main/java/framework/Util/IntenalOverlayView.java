package framework.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class IntenalOverlayView extends View {
    private Context context;
    private boolean isFocus = false;
    private PointF touchPoint;

    public interface DrawCallback {
        void onDraw(final Canvas canvas);
    }

    private ArrayList<DrawCallback> drawCallbacks = new ArrayList<DrawCallback>();

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

    public void registerDrawCallback(final DrawCallback drawCallback) {
        drawCallbacks.add(drawCallback);
    }

    public void unRegisterAllDrawCallback() {
        drawCallbacks.clear();
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
        }

        if (drawCallbacks.isEmpty() == false) {
            for (final DrawCallback drawCallback : drawCallbacks) {
                drawCallback.onDraw(canvas);
            }
        }

        super.onDraw(canvas);
    }
}
