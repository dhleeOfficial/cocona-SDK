package framework.Engine;

import java.util.ArrayList;

import framework.Enum.DeviceOrientation;

/**
 * Engine Observer
 * Callback change orientation & flash is possible
 * Callback create file (Recoding file, Labeling file, Score file)
 * Callback Live File Upload done
 */
public interface EngineObserver {
    void onChangeOrientation(DeviceOrientation deviceOrientation);
    void onCheckFlashSupport(boolean isSupport);

    void onCompleteVODFile(ArrayList<String> pathArray);
    void onCompleteScoreFile(String scorePath);
    void onCompleteLabelFile(String labelPath);

    void onCompleteLiveUpload();

}
