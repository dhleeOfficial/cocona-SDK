package framework.Thread;

import android.content.Context;
import android.os.Environment;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FFmpegThread implements Runnable {
    private Context context;
    private Callback callback;

    private String pipe;

    public FFmpegThread(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public interface Callback {
        void onReadyPipe(String pipe);
        void onOpenPipe();
        void onTerminatePipe();
    }

    public void requestPipe() {
        pipe = Config.registerNewFFmpegPipe(context);

        callback.onReadyPipe(pipe);
    }

    public void requestTerminate() {
        FFmpeg.cancel();
        callback.onTerminatePipe();
    }

    @Override
    public void run() {
        callback.onOpenPipe();
        String command = "-f h264" + " -i " + pipe + " -c copy " + "-f mp4 " + getOutputMediaFile().getPath();
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
