package framework.Thread;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;

import framework.AutoEdit.AutoEdit;
import framework.Util.Util;

public class AutoEditThread implements Runnable {
    private Matrix matrix;
    private AutoEdit autoEdit;

    private Size previewSize;

    // SETTING INFO
    byte[][] yuvBytes;
    int yRowStride;
    int uvRowStride;
    int uvPixelStride;
    int[] rgbBytes;

    private Bitmap imageRGBBitmap;
    private Bitmap cropBitmap;

    private Callback callback;

    public interface Callback {
        void onDone();
    }

    public AutoEditThread() {
        init();
    }

    @Override
    public void run() {
        rgbBytes = new int[previewSize.getWidth() * previewSize.getHeight()];

        Util.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2], previewSize.getWidth(), previewSize.getHeight(),
                yRowStride, uvRowStride, uvPixelStride, rgbBytes);

        imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
        imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

        rgbBytes = null;

        cropBitmap = Bitmap.createBitmap(imageRGBBitmap, 0, 0, imageRGBBitmap.getWidth(), imageRGBBitmap.getHeight(), matrix, true);

        if (autoEdit != null) {
            autoEdit.appendImage(cropBitmap);
        }

        callback.onDone();
    }

    public void loadTFLite(Context context) {
        AutoEdit.loadTFLite(context);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setInfo(byte[][] in, int yRowStride, int uvRowStride, int uvPixelStride) {
        this.yuvBytes = in.clone();
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
    }

    public void setPreviewSize(Size size) {
        this.previewSize = size;
        autoEdit.setImageSize(size);
    }

    public void setFirstTS(long startTS) {
        autoEdit.setFirstTS(startTS);
    }

    public void updateCurrentTS(long currentTS) {
        autoEdit.updateCurrentTS(currentTS);
    }

    public boolean isNextImage() {
        if (autoEdit != null) {
            return autoEdit.isVerify();
        }
        return false;
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
