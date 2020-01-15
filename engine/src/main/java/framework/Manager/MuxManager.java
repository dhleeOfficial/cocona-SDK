package framework.Manager;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.util.ArrayList;

import framework.Enum.Mode;
import framework.Message.ThreadMessage;
import framework.Util.LiveFileObserver;
import framework.Util.Util;

public class MuxManager extends HandlerThread {
    private Context context;
    private Handler myHandler;

    ArrayList<String> pipeList = new ArrayList<String>();

    // MODE STATUS
    private Mode mode = Mode.TRAVEL;

    // PIPE STATUS
    private int PIPE1_STATUS = 0;
    private int PIPE2_STATUS = 0;

    private FFMPEGThread ffmpeg;
    private LiveFileObserver liveFileObserver;

    // FFMPEG COMMAND (NOT LIVE)
//    private final String IN_PIPE1 = "-thread_queue_size 1024 -f h264 -r 30 -i ";
//    private final String IN_PIPE2 = " -thread_queue_size 2048 -f aac -i ";
//    private final String OTHERS = " -map 0:v -map 1:a -muxdelay 0 -vsync 1 -max_muxing_queue_size 9999 ";
//    private final String OUTPUT = " -c copy -f mp4 ";

    private final String INPUT_VIDEO_OPT = "-thread_queue_size 1024";
    private final String INPUT_AUDIO_OPT = " -thread_queue_size 2048";
    private final String INPUT_VIDEO = " -f h264 -r 30 -i ";
    private final String INPUT_AUDIO = " -f aac -i ";
    private final String OUTPUT = " -muxdelay 0 -vsync 1 -max_muxing_queue_size 9999 -c copy -f mp4 ";

    // FFMPEG COMMAND (LIVE)
    private final String IN_LIVE_PIPE1 = "-thread_queue_size 1024 -f h264 -r 24 -i ";
    private final String IN_LIVE_PIPE2 = " -thread_queue_size 2048 -f aac -i ";
    private final String MAPPING = " -map 0:v -map 1:a -b:v:0 5000k";
    private final String MUXING_HLS = " -muxdelay 0 -max_muxing_queue_size 9999 -flags +cgop -vsync 1 -shortest -g 24 " +
            "-c copy -bsf:a aac_adtstoasc -f hls -hls_list_size 0 -hls_time 2 -hls_flags independent_segments+omit_endlist -hls_allow_cache 0 -hls_segment_type fmp4 -movflags frag_keyframe+faststart";
    private final String MASTER_PL = " -master_pl_publish_rate 999999999 -master_pl_name master.m3u8 -hls_fmp4_init_filename init.mp4 -hls_segment_filename " +
            Util.getOutputVideoSegDir().getPath() + "/sec%v_%d.mp4 " + Util.getOutputVideoSegDir().getPath() + "/sec%v.m3u8";


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
                        mode = ((Mode) msg.obj);
                        muxExecute();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_LIVE_START : {
                        mode = ((Mode) msg.obj);
                        muxLiveExecute();

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

    public void pipeAdd(String pipe) {
        pipeList.add(pipe);
    }

    public void resetPipeList() {
        for (final String pipe : pipeList) {
            Config.closeFFmpegPipe(pipe);
        }

        pipeList.clear();
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void muxExecute() {
        PIPE1_STATUS = 1;
        PIPE2_STATUS = 1;

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void muxLiveExecute() {
        PIPE1_STATUS = 1;
        PIPE2_STATUS = 1;

        //ddd();
        if (liveFileObserver == null) {
            liveFileObserver = new LiveFileObserver(context, Util.getOutputVideoSegDir());
            liveFileObserver.startWatching();
        }

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void muxCancel() {
        if ((PIPE1_STATUS | PIPE2_STATUS) == 0) {
            for (final String pipe : pipeList) {
                Config.closeFFmpegPipe(pipe);
            }

            if (ffmpeg != null) {
                ffmpeg.quitThread();
                ffmpeg.interrupt();
                ffmpeg = null;
            }

            if (liveFileObserver != null) {
                liveFileObserver.close();
                liveFileObserver = null;
            }

            pipeList.clear();
        }
    }

    private class FFMPEGThread extends Thread {
        public FFMPEGThread() {
            super("ffmpeg");
        }

        @Override
        public void run() {
            if (mode != Mode.LIVE) {
                //Log.e("FFMPEG", "Pipe1 : " + pipeList.get(0) + " / Pipe2 : " + pipeList.get(1) + " / Pipe3 : " + pipeList.get(2));
                //FFmpeg.execute(IN_PIPE1 + pipeList.get(0) + IN_PIPE2 + pipeList.get(1) + OTHERS + OUTPUT + Util.getOutput1080File());
                String command = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + /*" " + INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(1) +*/ INPUT_AUDIO_OPT + INPUT_AUDIO + pipeList.get(1) +
                        " -map 0:v -map 1:a" + OUTPUT + Util.getOutput1080File() /*+ " -map 1:v -map 2:a" + OUTPUT + Util.getOutput720File()*/;
//                String command = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + INPUT_VIDEO + pipeList.get(1) + INPUT_AUDIO_OPT + INPUT_AUDIO + pipeList.get(2) +
//                        " -map 0:v -map 2:a -map 1:v -map 2:a -var_stream_map \"v:0,a:0 v:1,a:1\"" + OUTPUT + "-muxdelay 0 -max_muxing_queue_size 9999 -flags +cgop -vsync 1 -shortest -g 24 -f hls -hls_list_size 0 -hls_time 2 -hls_flags independent_segments+omit_endlist -hls_allow_cache 0 -hls_segment_type fmp4 -movflags frag_keyframe+faststart -master_pl_publish_rate 999999999 "
//                         + "-master_pl_name \"master.m3u8\"" +
//                " -hls_segment_filename " + Util.getOutputVideoSegDir() + "/sec%v_%d.mp4";
                //String command1 = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + INPUT_VIDEO + pipeList.get(1) + " -map 0:v" + OUTPUT + Util.getOutput1080File() + " -map 1:v" + OUTPUT + Util.getOutput720File();
                Log.e(Thread.currentThread().getName(), command);
                FFmpeg.execute(command);
            } else {
                FFmpeg.execute(IN_LIVE_PIPE1 + pipeList.get(0) + IN_LIVE_PIPE2 + pipeList.get(1) + MAPPING + MUXING_HLS + MASTER_PL);
            }
        }

        public void quitThread() {
            FFmpeg.cancel();
        }
    }
}
