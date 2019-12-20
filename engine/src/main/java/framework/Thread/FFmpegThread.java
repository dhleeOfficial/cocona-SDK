package framework.Thread;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class FFmpegThread implements Runnable {
    private Context context;
    private ArrayList<Callback> callbackList = new ArrayList<Callback>();

    private String videoPipe;
    private String audioPipe;

    public interface Callback {
        void onReadyPipe(String videoPipe, String audioPipe);
        void onOpenPipe();
        void onTerminatePipe();
    }

    private FFmpegThread() {
    }

    private static class InstanceHolder {
        private static final FFmpegThread inst = new FFmpegThread();
    }

    public static FFmpegThread getInstance() {
        return InstanceHolder.inst;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void addCallback(Callback callback) {
        callbackList.add(callback);
    }

    public void requestPipe() {
        System.out.println("");
        videoPipe = Config.registerNewFFmpegPipe(context);
        audioPipe = Config.registerNewFFmpegPipe(context);

        for (final Callback cb : callbackList) {
            cb.onReadyPipe(videoPipe, audioPipe);
        }
    }

    public void requestTerminate() {
        FFmpeg.cancel();
        Config.closeFFmpegPipe(videoPipe);
        Config.closeFFmpegPipe(audioPipe);

        videoPipe = null;
        audioPipe = null;
        Log.e("FFMPEG", "CANCEL");

        for (final Callback cb : callbackList) {
            cb.onTerminatePipe();
        }
    }

    @Override
    public void run() {
        for (final Callback cb : callbackList) {
            cb.onOpenPipe();
        }

        String command = "-thread_queue_size 512 -f h264 -r 30 -i " + videoPipe + " -thread_queue_size 512 -f aac -i " + audioPipe + " -map 0:v -map 1:a" +
                " -muxdelay 0 -vsync 2 -max_muxing_queue_size 9999 -c copy -copyts -f mp4 " + getOutputMediaFile().getPath();

        Log.e("FFMPEG", "START");
        FFmpeg.execute(command);
        Log.e("FFMPEG", "ING");
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "FFMPEG");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                 + timeStamp + ".mp4");
        return mediaFile;
    }
}
