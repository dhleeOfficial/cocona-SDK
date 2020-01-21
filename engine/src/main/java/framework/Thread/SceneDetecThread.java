package framework.Thread;

import framework.SceneDetection.SceneData;

public class SceneDetecThread implements Runnable {
    private Callback callback;

    private int frameIdx;
    private int timeStamp;
    private int chunkIdx;

    public interface Callback {
        void onSceneDetecDone(SceneData sceneData);
    }

    public SceneDetecThread() {
    }

    @Override
    public void run() {
        // DO somethie~



        //
        SceneData sceneData = new SceneData(frameIdx, timeStamp, chunkIdx);

        sceneData.addLabelData(new SceneData.LabelData("Label_Name1", 0.000001f));
        sceneData.addLabelData(new SceneData.LabelData("Label_Name2", 0.0000001f));

        callback.onSceneDetecDone(sceneData);
    }

    public void setInfo(int frameIdx, int timeStamp, int chunkIdx) {
        this.frameIdx = frameIdx;
        this.timeStamp = timeStamp;
        this.chunkIdx = chunkIdx;
    }
}
