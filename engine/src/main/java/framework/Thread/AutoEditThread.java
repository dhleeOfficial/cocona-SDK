package framework.Thread;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;

import framework.AutoEdit.AutoEdit;

public class AutoEditThread implements Runnable {
    private Matrix matrix;
    private AutoEdit autoEdit;

    private Size previewSize;
    int[] rgbBytes;

    private Bitmap imageRGBBitmap;
    private Bitmap cropBitmap;

    private Callback callback;

    public interface Callback {
        void onAutoEditDone();
    }

    public AutoEditThread() {
        init();
    }

    @Override
    public void run() {
        if (rgbBytes != null) {
            imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
            imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

            rgbBytes = null;
        }

        if (imageRGBBitmap != null) {
            cropBitmap = Bitmap.createBitmap(imageRGBBitmap, 0, 0, imageRGBBitmap.getWidth(), imageRGBBitmap.getHeight(), matrix, true);
        }

        if ((autoEdit != null) && (cropBitmap != null)) {
            autoEdit.appendImage(cropBitmap);
        }

        if (callback != null) {
            callback.onAutoEditDone();
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setData(int[] rgbBytes) {
        this.rgbBytes = rgbBytes;
    }

    public void setPreviewSize(Size size) {
        this.previewSize = size;
        autoEdit.setImageSize(size);
    }

    public void setFirstTS(long startTS) {
        autoEdit.setFirstTS(startTS);
    }


    public String getScoreFile() {
        if (autoEdit != null) {
            return autoEdit.getScoreFile();
        }
        return null;
    }

    public void stop() {
        if (autoEdit != null) {
            autoEdit.stop();
            autoEdit = null;
        }
    }

    private void init() {
        matrix = new Matrix();
        matrix.postRotate(90);

        autoEdit = new AutoEdit();
    }
}
