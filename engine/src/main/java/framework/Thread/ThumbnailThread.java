package framework.Thread;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;

import java.io.File;
import framework.Util.Util;

public class ThumbnailThread implements Runnable {
    byte[][] yuvBytes;
    int yRowStride;
    int uvRowStride;
    int uvPixelStride;
    int[] rgbBytes;
    int orientation;
    private Bitmap imageRGBBitmap;
    private Size previewSize;
    private Callback callback;

    public interface Callback {
        void onDone();
    }

    final File file = new File(Util.getOutputLIVEDir().getPath(),"thumbnail.jpeg");

    public void setInfo(int orientation, Size previewSize, byte[][] in, int yRowStride, int uvRowStride, int uvPixelStride) {
        this.previewSize = previewSize;
        this.yuvBytes = in.clone();
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
        this.orientation = orientation;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        rgbBytes = new int[previewSize.getWidth() * previewSize.getHeight()];

        Util.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2], previewSize.getWidth(), previewSize.getHeight(),
                yRowStride, uvRowStride, uvPixelStride, rgbBytes);

        imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
        imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

        rgbBytes = null;


        if (orientation == 3) {
            Util.saveBitmap(file, imageRGBBitmap);
            imageRGBBitmap.recycle();
            imageRGBBitmap = null;
        } else {
            Matrix matrix = new Matrix();
            matrix.postRotate(90 * (1 + orientation));
            Bitmap rotatedBitmap = Bitmap.createBitmap(imageRGBBitmap, 0, 0, previewSize.getWidth(), previewSize.getHeight(), matrix, true);
            Util.saveBitmap(file, rotatedBitmap);
            imageRGBBitmap.recycle();
            imageRGBBitmap = null;
            rotatedBitmap.recycle();
            rotatedBitmap = null;
        }
        yuvBytes = null;
        callback.onDone();
    }
}

