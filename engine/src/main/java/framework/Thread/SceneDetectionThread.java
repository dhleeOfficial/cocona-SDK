package framework.Thread;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import android.util.Size;

import java.util.ArrayList;
import framework.SceneDetection.SceneData;
import framework.SceneDetection.SceneDetection;
import framework.Util.Util;

public class SceneDetectionThread implements Runnable {

    private SceneDetection sceneDetection;

    private Size previewSize;
    byte[][] yuvBytes;
    int yRowStride;
    int uvRowStride;
    int uvPixelStride;
    int[] rgbBytes;

    private Bitmap imageRGBBitmap;

    private Callback callback;

    private int frameIdx;
    private int timeStamp;
    private int chunkIdx;

    public interface Callback {
        void onSceneDetecDone(SceneData sceneData);
    }

    public SceneDetectionThread() {
        this.sceneDetection = new SceneDetection();
    }

    public void setPreviewSize(Size size) {
        this.previewSize = size;
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

        ArrayList<Pair<String, Float>> results = sceneDetection.recognizeImage(imageRGBBitmap);
        SceneData sceneData = new SceneData(frameIdx, timeStamp, chunkIdx);

        for (final Pair<String,Float> result : results) {
            sceneData.addLabelData(new SceneData.LabelData(result.first, result.second));
        }
        imageRGBBitmap.recycle();
        imageRGBBitmap = null;
        yuvBytes = null;
        callback.onSceneDetecDone(sceneData);
    }

    public void setInfo(int frameIdx, int timeStamp, int chunkIdx, byte[][] in, int yRowStride, int uvRowStride, int uvPixelStride) {
        this.frameIdx = frameIdx;
        this.timeStamp = timeStamp;
        this.chunkIdx = chunkIdx;
        this.yuvBytes = in.clone();
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
    }

    public void loadModel(Context context) {
        SceneDetection.loadTFLite(context);
        SceneDetection.readLabels(context);
    }
}
