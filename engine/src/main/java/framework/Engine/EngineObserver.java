package framework.Engine;

/**
 * Engine Observer
 * Callback create file (Recoding file, Labeling file, Score file)
 * Callback Live File Upload done
 */
public interface EngineObserver {
    void onCompleteVODFile(String vodPath);
    void onCompleteScoreFile(String scorePath);
    void onCompleteLabelFile(String labelPath);

    void onCompleteLiveUpload();
}
