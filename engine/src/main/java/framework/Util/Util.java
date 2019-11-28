package framework.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Util {
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    public static Size getOptimalSize(List<Size> availSize) {
        return Collections.min(availSize, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                return diff(o1) - diff(o2);
            }

            private int diff(Size size) {
                return Math.abs(MAX_PREVIEW_WIDTH - size.getWidth()) + Math.abs(MAX_PREVIEW_HEIGHT - size.getHeight());
            }
        });
    }

    public static class OverlayView extends View {
        private boolean isFocus = false;
        private PointF touchPoint;

        public OverlayView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
        }

        public OverlayView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            setBackgroundColor(Color.TRANSPARENT);
        }

        public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
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
    };

    public static OverlayView createOverlayView(Context context, View subject) {
        OverlayView view = new OverlayView(context);
        ((RelativeLayout)subject).addView(view);

        return view;
    }

}
