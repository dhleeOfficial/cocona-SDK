package framework.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class InferenceOverlayView extends View {
    private Context context;

    public interface DrawCallback {
        void onDraw(final Canvas canvas);
    }

    private ArrayList<DrawCallback> drawCallbacks = new ArrayList<DrawCallback>();

    public InferenceOverlayView(Context context) {
        super(context);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public InferenceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public InferenceOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

    @Override
    public void draw(Canvas canvas) {
        if (drawCallbacks.isEmpty() == false) {
            for (final DrawCallback drawCallback : drawCallbacks) {
                drawCallback.onDraw(canvas);
            }
        }
    }
}
