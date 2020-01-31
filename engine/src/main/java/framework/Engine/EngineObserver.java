package framework.Engine;

/**
 *
 */
public interface EngineObserver {
    void onCompleteVODFile(String vodPath);
    void onCompleteScoreFile(String scorePath);
    void onCompleteLabelFile(String labelPath);

    void onCompleteLiveUpload();
}
