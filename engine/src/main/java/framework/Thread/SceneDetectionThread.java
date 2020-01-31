package framework.Thread;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import android.util.Size;

import java.util.ArrayList;
import framework.SceneDetection.SceneData;
import framework.SceneDetection.SceneDetection;

public class SceneDetectionThread implements Runnable {
    private SceneDetection sceneDetection;
    private Size previewSize;
    int[] rgbBytes;

    private Bitmap imageRGBBitmap;

    private Callback callback;

    private int frameIdx;
    private int timeStamp;
    private int chunkIdx;

    public interface Callback {
        void onSceneDetectionDone(SceneData sceneData);
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
        if (rgbBytes != null) {
            imageRGBBitmap = Bitmap.createBitmap(previewSize.getWidth(), previewSize.getHeight(), Bitmap.Config.ARGB_8888);
            imageRGBBitmap.setPixels(rgbBytes, 0, previewSize.getWidth(), 0, 0, previewSize.getWidth(), previewSize.getHeight());

            rgbBytes = null;
        }

        if (imageRGBBitmap != null) {
            ArrayList<Pair<String, Float>> results = sceneDetection.recognizeImage(imageRGBBitmap);
            SceneData sceneData = new SceneData(frameIdx, timeStamp, chunkIdx);

            for (final Pair<String, Float> result : results) {
                sceneData.addLabelData(new SceneData.LabelData(result.first, result.second));
            }

            imageRGBBitmap.recycle();
            imageRGBBitmap = null;

            if (callback != null) {
                callback.onSceneDetectionDone(sceneData);
            }
        }
    }

    public void setData(int frameIdx, int timeStamp, int chunkIdx, int[] rgbBytes) {
        this.frameIdx = frameIdx;
        this.timeStamp = timeStamp;
        this.chunkIdx = chunkIdx;
        this.rgbBytes = rgbBytes;
    }

    public void loadModel(Context context) {
        SceneDetection.loadTFLite(context);
        SceneDetection.readLabels(context);
    }
}
