package framework.ObjectDetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Size;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;

import framework.Util.Util;

public class BoxDrawer {
    public static class ClassifierDrawer {
        public static class DrawInfo {
            private String text;
            private float textSize;
            private Paint textPaint;
            private PointF textPosition;

            public DrawInfo(final String text, final float textSize, final Paint textPaint, final PointF textPosition) {
                this.text = text;
                this.textSize = textSize;
                this.textPaint = textPaint;
                this.textPosition = textPosition;
            }

            public final String getText() {
                return text;
            }

            public final float getTextSize() {
                return textSize;
            }

            public final Paint getTextPaint() {
                return textPaint;
            }

            public final PointF getTextPosition() {
                return textPosition;
            }
        }

        public ClassifierDrawer() {}

        public void draw(final Canvas canvas, final DrawInfo drawInfo) {
            canvas.drawText(drawInfo.getText(),
                            drawInfo.getTextPosition().x,
                         drawInfo.getTextPosition().y + drawInfo.getTextSize(),
                            drawInfo.getTextPaint());
        }
    }

    private final Context context;
    private final Size frameSize;
    private final int orientation;

    private final float DEFAULT_DIP = 18.0f;
    private final float MIN_SIZE = 16.0f;
    private final int DEFAULT_BOX_COLOR = Color.DKGRAY;
    private final int DEFAULT_BOX_TEXT_COLOR = Color.WHITE;

    private Paint boxPaint;
    private Paint boxTextPaint;
    private float textSizePxValue;
    private Matrix matrixFromFrame;
    private ArrayList<Classifier.Recognition> box = new ArrayList<Classifier.Recognition>();


    public BoxDrawer(final Context context, final Size frameSize, final int orientation) {
        this.context = context;
        this.frameSize = frameSize;
        this.orientation = orientation;

        this.init();
    }

    public synchronized void processWillDrawBox(final List<Classifier.Recognition> boxList) {
        if (boxList.isEmpty() == false) {
            box.clear();

            for (final Classifier.Recognition boxElement : boxList) {
                if (boxElement.getLocation() != null) {
                    if ((boxElement.getLocation().width() > MIN_SIZE) && (boxElement.getLocation().height() > MIN_SIZE)) {
                        box.add(boxElement);
                    }
                }
            }
        }
    }

    public synchronized void draw(final Canvas canvas) {
        if (box.isEmpty() == false) {
            boolean rotation = orientation % 180 == 90;
            float multiplier = Math.min(canvas.getHeight() / (float) (rotation ? frameSize.getWidth() : frameSize.getHeight()),
                    canvas.getWidth() / (float) (rotation ? frameSize.getHeight() : frameSize.getWidth()));

            Size transSize = new Size((int) (multiplier * (rotation ? frameSize.getHeight() : frameSize.getWidth())), (int) (multiplier * (rotation ? frameSize.getWidth() : frameSize.getHeight())));

            matrixFromFrame = Util.getTransformationMatrix(frameSize, transSize, orientation, false);


            for (final Classifier.Recognition boxElement : box) {
                final RectF boxRect = new RectF(boxElement.getLocation());

                matrixFromFrame.mapRect(boxRect);
                canvas.drawRect(boxRect, boxPaint);

                final String boxName = String.format("%s %.2f", boxElement.getTitle(), (100 * boxElement.getConfidence())) + "%";
                final ClassifierDrawer classifierDrawer = new ClassifierDrawer();
                final PointF boxTextPoint = new PointF(boxRect.left + 50.0f, boxRect.top);
                final ClassifierDrawer.DrawInfo drawInfo = new ClassifierDrawer.DrawInfo(boxName, textSizePxValue, boxTextPaint, boxTextPoint);

                classifierDrawer.draw(canvas, drawInfo);
            }
        }
    }

    public void clearBoxDrawer() {
        box.clear();
    }

    private void init() {
        boxPaint = new Paint();

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(20.0f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);
        boxPaint.setStrokeJoin(Paint.Join.ROUND);
        boxPaint.setStrokeMiter(10.0f);
        boxPaint.setColor(DEFAULT_BOX_COLOR);

        boxTextPaint = new Paint();

        boxTextPaint.setColor(DEFAULT_BOX_TEXT_COLOR);
        boxTextPaint.setTextSize(50.0f);

        textSizePxValue = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_DIP, context.getResources().getDisplayMetrics());
    }
}