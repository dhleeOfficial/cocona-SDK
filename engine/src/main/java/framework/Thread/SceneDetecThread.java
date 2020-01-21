package framework.Thread;

public class SceneDetecThread implements Runnable {
    private Callback callback;

    public interface Callback {
        void onSceneDetecDone();
    }

    public SceneDetecThread() {
    }

    @Override
    public void run() {

    }
}
