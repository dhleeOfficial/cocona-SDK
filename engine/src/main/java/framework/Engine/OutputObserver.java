package framework.Engine;

public interface OutputObserver {
    void onCompleteVODFile(String vodPath);
    void onCompleteScoreFile(String scorePath);
    void onCompleteLabelFile(String labelPath);
}
