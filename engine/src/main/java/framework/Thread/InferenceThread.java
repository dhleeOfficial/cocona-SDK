package framework.Thread;

import android.media.Image;

public class InferenceThread extends Thread {
    private boolean isComplete = false;
    private Image image;

    public interface Callback {
        void onComplete();
    }

    public InferenceThread(Image image) {
        super("Inference");

        this.image = image;
    }

    @Override
    public void run() {
    }
}
