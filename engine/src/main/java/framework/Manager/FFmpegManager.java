package framework.Manager;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import framework.Message.ThreadMessage;


public class FFmpegManager extends HandlerThread {
    private Handler myHandler;
    private Context context;

    private String pipe;
    private String command;
    private String outputPath;

    public FFmpegManager(Context context) {
        super("FFmpegManager");
        this.context = context;
    }

    public interface FFmpegCallback {
        void ffmpegExecuted();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.FFmpegMessage.MSG_FFMPEG_START : {
                        readyFFmpeg();

                        return true;
                    }
                    case ThreadMessage.FFmpegMessage.MSG_FFMPEG_STOP : {
                        stopFFmpeg();

                        return true;
                    }
                }
                return false;
            }
        });
    }

    public final Handler getHandler() {
        return myHandler;
    }
    public final String getPipe() {return pipe;}

    private void readyFFmpeg() {
        try {
            pipe = Config.registerNewFFmpegPipe(context);
            outputPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath() + "/" + /*new SimpleDateFormat("yyyy_dd-HHmmss").format(new Date()) +*/ "abc.mp4";
            command = "-y -f rawvideo -pixel_format yuv420p -video_size 1080x1920 -i " + pipe + " -c:v libx264 -r 30 -an -f mp4 " + outputPath;

            FFmpeg.execute(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopFFmpeg() {
        FFmpeg.cancel();
    }

    /*private void fillBytes(final Image.Plane[] planes, final byte[] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes == null) {
                yuvBytes = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes);
        }
    }*/
}
