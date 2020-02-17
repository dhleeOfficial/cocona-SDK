package framework.SceneDetection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import framework.Util.Util;

public class JsonResult {
    private JSONObject obj;
    private JSONArray objArray;

    private File file = null;
    private FileWriter fileWriter = null;

    public JsonResult(String srcDir) {
        init(srcDir);
    }

    public void inputData(SceneData sceneData) {
        ArrayList<SceneData.LabelData> labelDataList = sceneData.getLabelList();
        int frameIdx = sceneData.getFrameIdx();
        int timeStamp = sceneData.getTimeStamp();
        int chunkIdx = sceneData.getChunkIdx();

        try {
            JSONObject jobj = new JSONObject();

            JSONArray jsonArray = new JSONArray();
            int listSize = labelDataList.size();

            for (int i = 0; i < listSize; ++i) {
                JSONObject jsonObject = new JSONObject();

                jsonObject.put("name", labelDataList.get(i).getName());
                jsonObject.put("score", new Float(labelDataList.get(i).getScore()));
                jsonArray.put(jsonObject);
            }

            jobj.put("labels", jsonArray);
            jobj.put("frame_index", new Integer(frameIdx));
            jobj.put("time_stamp", new Integer(timeStamp));
            jobj.put("chunk_index", new Integer(chunkIdx));

            objArray.put(jobj);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    public String createJSONFile() {
        try {
            obj.put("chunks", objArray);

            fileWriter.write(obj.toString());
        } catch (IOException ie) {
            ie.printStackTrace();
        } catch (JSONException je) {
            je.printStackTrace();
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException ie) {
                ie.printStackTrace();
            }
        }
        return file.getPath();
    }

    private void init(String srcDir) {
        obj = new JSONObject();
        objArray = new JSONArray();
        try {
            file = Util.getOutputLabelFile(srcDir);
            fileWriter = new FileWriter(file);
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }
}
