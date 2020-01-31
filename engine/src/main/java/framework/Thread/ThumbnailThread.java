package framework.Thread;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;

import java.io.File;
import framework.Util.Util;

public class ThumbnailThread implements Runnable {
    int orientation;
    private Size previewSize;
    int[] rgbBytes;

    private Bitmap imageRGBBitmap;
    private Bitmap rotatedBitmap;

    private Callback callback;

    public interface Callback {
        void onThumbnailDone();
    }

    final File file = new File(Util.getOutputLIVEDir().getPath(),"thumbnail.jpeg");

    public void setData(int orientation, Size previewSize, int[] rgbBytes) {
        this.orientation = orientation;
        this.previewSize = previewSize;
        this.rgbBytes = rgbBytes;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        if (rgbBytes != null) {
            imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
            imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

            rgbBytes = null;
        }

        if (imageRGBBitmap != null) {
            if (orientation == 3) {
                Util.saveBitmap(file, imageRGBBitmap);

                imageRGBBitmap.recycle();
                imageRGBBitmap = null;
            } else {
                Matrix matrix = new Matrix();
                matrix.postRotate(90 * (1 + orientation));

                rotatedBitmap = Bitmap.createBitmap(imageRGBBitmap, 0, 0, previewSize.getWidth(), previewSize.getHeight(), matrix, true);

                if (rotatedBitmap != null) {
                    Util.saveBitmap(file, rotatedBitmap);

                    imageRGBBitmap.recycle();
                    imageRGBBitmap = null;

                    rotatedBitmap.recycle();
                    rotatedBitmap = null;
                }
            }
        }

        if (callback != null) {
            callback.onThumbnailDone();
        }
    }
}

