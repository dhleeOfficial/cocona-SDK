package framework.Thread;

import android.content.Context;
import android.os.Environment;

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

    // TODO : Singleton Instance
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
        videoPipe = Config.registerNewFFmpegPipe(context);
        audioPipe = Config.registerNewFFmpegPipe(context);

        for (final Callback cb : callbackList) {
            cb.onReadyPipe(videoPipe, audioPipe);
        }
    }

    public void requestTerminate() {
        FFmpeg.cancel();

        for (final Callback cb : callbackList) {
            cb.onTerminatePipe();
        }
    }

    @Override
    public void run() {
        for (final Callback cb : callbackList) {
            cb.onOpenPipe();
        }

        String command = "-f h264 -r 30 -i " + videoPipe + " -f aac -i " + audioPipe + " -map 0:v -map 1:a" +
                " -muxdelay 0 -vsync 2 -max_muxing_queue_size 9999 -c copy -f mp4 " + getOutputMediaFile().getPath();
        //String command = "-f h264 -r 30 -i " + videoPipe + " -muxdelay 0 -vsync 2 -max_muxing_queue_size 9999 -c copy -f mp4 " + getOutputMediaFile().getPath();
        FFmpeg.execute(command);
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
