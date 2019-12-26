package framework.Manager;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.util.ArrayList;

import framework.Message.ThreadMessage;
import framework.Util.Util;

public class MuxManager extends HandlerThread {
    private Context context;
    private Handler myHandler;

    ArrayList<String> pipeList = new ArrayList<String>();

    // PIPE STATUS
    private int PIPE1_STATUS = 0;
    private int PIPE2_STATUS = 0;

    private FFMPEGThread ffmpeg;

    // FFMPEG COMMAND
    private final String IN_PIPE1 = "-thread_queue_size 512 -f h264 -r 30 -vsync 2 -i ";
    private final String IN_PIPE2 = " -thread_queue_size 2048 -f aac -i ";
    private final String OTHERS = " -map 0:v -map 1:a -muxdelay 0 -max_muxing_queue_size 9999 ";
    private final String OUTPUT = " -c copy -f mp4 ";

    public MuxManager(Context context) {
        super("MuxManager");

        this.context = context;
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.MuxMessage.MSG_MUX_START : {
                        muxExecute();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END : {
                        PIPE1_STATUS = 0;
                        muxCancel();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_AUDIO_END : {
                        PIPE2_STATUS = 0;
                        muxCancel();

                        return true;
                    }
                }
                return false;
            }
        });
    }

    public String requestPipe() {
        String pipe = Config.registerNewFFmpegPipe(context);

        pipeList.add(pipe);

        return pipe;
    }

    public void resetPipeList() {
        pipeList.clear();
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void muxExecute() {
        PIPE1_STATUS = 1;
        PIPE2_STATUS = 1;

        ffmpeg = new FFMPEGThread();
        ffmpeg.start();
    }

    private void muxCancel() {
        if ((PIPE1_STATUS | PIPE2_STATUS) == 0) {
            for (final String pipe : pipeList) {
                Config.closeFFmpegPipe(pipe);
            }

            ffmpeg.quitThread();
            ffmpeg.interrupt();
            ffmpeg = null;

            pipeList.clear();
        }
    }

    private class FFMPEGThread extends Thread {
        @Override
        public void run() {
            FFmpeg.execute(IN_PIPE1 + pipeList.get(0) +
                    IN_PIPE2 + pipeList.get(1) + OTHERS + OUTPUT + Util.getOutputMuxFile());
        }

        public void quitThread() {
            FFmpeg.cancel();
        }
    }
}
