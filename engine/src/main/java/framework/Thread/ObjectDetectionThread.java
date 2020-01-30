package framework.Thread;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Size;

import java.util.LinkedList;
import java.util.List;

import framework.Enum.LensFacing;
import framework.ObjectDetection.BoxDrawer;
import framework.ObjectDetection.Classifier;
import framework.Util.Util;

public class ObjectDetectionThread implements Runnable {
    private static final float MINIMUM_CONFIDENCE = 0.5f;
    private static int INPUT_SIZE;
    private boolean isComplete = false;

    private Size previewSize;
    private LensFacing lensFacing;
    private BoxDrawer boxDrawer;
    private Classifier classifier;
    private Matrix matrix;

    // SETTING INFO
    byte[][] yuvBytes;
    int yRowStride;
    int uvRowStride;
    int uvPixelStride;
    int[] rgbBytes;
    //

    private Bitmap imageRGBBitmap;
    private Bitmap cropBitmap;

    private Callback callback;

    public interface Callback {
        void onComplete();
    }

    public ObjectDetectionThread(Classifier classifier, int size) {
        this.classifier = classifier;
        INPUT_SIZE = size;
    }

    public void setInfo(byte[][] in, int yRowStride, int uvRowStride, int uvPixelStride) {
        this.yuvBytes = in.clone();
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setPreviewSize(Size size) {
        this.previewSize = size;
    }

    public void setLensFacing(LensFacing lensFacing) {
        this.lensFacing = lensFacing;
    }

    public void setBoxDrawer(BoxDrawer boxDrawer) {
        this.boxDrawer = boxDrawer;
    }

    public void setMatrix(Matrix matrix) {
        this.matrix = matrix;
    }

    public void setClassifier(Classifier classifier) { this.classifier = classifier; }

    @Override
    public void run() {
        rgbBytes = new int[previewSize.getWidth() * previewSize.getHeight()];

        Util.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2], previewSize.getWidth(), previewSize.getHeight(),
                                     yRowStride, uvRowStride, uvPixelStride, rgbBytes);

        imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
        imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

        rgbBytes = null;

        cropBitmap = Bitmap.createScaledBitmap(imageRGBBitmap, INPUT_SIZE, INPUT_SIZE, true);

        List<Classifier.Recognition> recognitionList = classifier.recognizeImage(cropBitmap);
        List<Classifier.Recognition> result = new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition recElement : recognitionList) {
            RectF location = recElement.getLocation();

            if ((location != null) && (recElement.getConfidence() >= MINIMUM_CONFIDENCE)) {
                location = rotate(location);
                matrix.mapRect(location);
                recElement.setLocation(location);
                result.add(recElement);
            }
        }

        if (result.isEmpty() == true) {
            boxDrawer.clearBoxDrawer();
        } else {
            boxDrawer.processWillDrawBox(result);
        }

        imageRGBBitmap.recycle();
        imageRGBBitmap = null;

        cropBitmap.recycle();
        cropBitmap = null;
        yuvBytes = null;
        callback.onComplete();
    }

    private RectF rotate(RectF rectF) {
        RectF rotateRect;

        if (lensFacing == LensFacing.FRONT) {
            rotateRect = new RectF(INPUT_SIZE - rectF.bottom, INPUT_SIZE - rectF.right, INPUT_SIZE - rectF.top, INPUT_SIZE - rectF.left);
        } else {
            rotateRect = new RectF(INPUT_SIZE - rectF.bottom, rectF.left, INPUT_SIZE - rectF.top, rectF.right);
        }

        return rotateRect;
    }
}
