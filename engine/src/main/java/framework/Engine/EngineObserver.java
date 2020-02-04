package framework.Engine;

import java.util.ArrayList;

/**
 * Engine Observer
 * Callback create file (Recoding file, Labeling file, Score file)
 * Callback Live File Upload done
 */
public interface EngineObserver {
    void onCompleteVODFile(ArrayList<String> pathArray);
    void onCompleteScoreFile(String scorePath);
    void onCompleteLabelFile(String labelPath);

    void onCompleteLiveUpload();
}
