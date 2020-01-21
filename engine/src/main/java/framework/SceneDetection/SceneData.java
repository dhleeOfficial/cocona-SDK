package framework.SceneDetection;

import java.lang.reflect.Array;
import java.security.acl.LastOwnerException;
import java.util.ArrayList;

public class SceneData {
    public static class LabelData {
        private String name;
        private float score;

        public LabelData(String name, float score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public float getScore() {
            return score;
        }
    }

    private ArrayList<LabelData> labelList = new ArrayList<LabelData>();
    private int frameIdx;
    private int timeStamp;
    private int chunkIdx;

    public SceneData(int frameIdx, int timeStamp, int chunkIdx) {
        this.frameIdx = frameIdx;
        this.timeStamp = timeStamp;
        this.chunkIdx = chunkIdx;

        labelList.clear();
    }

    public void addLabelData(LabelData labelData) {
        labelList.add(labelData);
    }

    public ArrayList<LabelData> getLabelList() {
        return labelList;
    }

    public int getFrameIdx() {
        return frameIdx;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public int getChunkIdx() {
        return chunkIdx;
    }
}
