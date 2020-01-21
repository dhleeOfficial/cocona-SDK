package framework.Manager;

import android.content.Context;
import android.icu.util.Output;
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
import com.arthenica.mobileffmpeg.Level;

import java.io.File;
import java.util.ArrayList;

import framework.Engine.OutputObserver;
import framework.Enum.Mode;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.Util.LiveFileObserver;
import framework.Util.Util;

public class MuxManager extends HandlerThread {
    private Context context;
    private Handler myHandler;
    private OutputObserver outputObserver;

    ArrayList<String> pipeList = new ArrayList<String>();

    // RESOLUTION VALUE
    private final int VIDEO_1080P = 1080;
    private final int VIDEO_720P = 720;
    private final int VIDEO_480P = 480;

    // MODE STATUS
    private Mode mode = Mode.TRAVEL;

    private int VIDEO_1080_STATUS = 0;
    private int VIDEO_720_STATUS = 0;
    private int VIDEO_480_STATUS = 0;
    private int AUDIO_STATUS = 0;

    private FFMPEGThread ffmpeg;
    private LiveFileObserver liveFileObserver;

    // VOD FFMPEG COMMAND SET
    private final String INPUT_VIDEO_OPT = "-thread_queue_size 1024";
    private final String INPUT_AUDIO_OPT = " -thread_queue_size 2048";
    private final String INPUT_VIDEO = " -f h264 -r 30 -i ";
    private final String INPUT_AUDIO = " -f aac -i ";
    private final String OUTPUT = " -muxdelay 0 -vsync 1 -max_muxing_queue_size 9999 -c copy -f mp4 ";

    // LIVE FFMPEG COMMAND SET
    private final String INPUT_LIVE_VIDEO_OPT = "-thread_queue_size 1024";
    private final String INPUT_LIVE_VIDEO = " -f h264 -r 24 -i ";
    private final String INPUT_LIVE_AUDIO_OPT = " -thread_queue_size 2048";
    private final String INPUT_LIVE_AUDIO = " -f aac -i ";
    private final String MAP_LIVE_COMMAND = " -map 0:v -map 3:a -map 1:v -map 3:a -map 2:v -map 3:a -b:v:0 5000k -b:v:1 2000k -b:v:2 1500k -var_stream_map \"v:0,a:0 v:1,a:1 v:2,a:2\"";
    private final String MUXING_LIVE_OPT = " -muxdelay 0 -max_muxing_queue_size 9999 -flags +cgop -vsync 1 -shortest -g 24 -c copy -bsf:a aac_adtstoasc -f hls -hls_list_size 0 -hls_time 2 -hls_flags " +
            "independent_segments+omit_endlist -hls_allow_cache 0 -hls_segment_type fmp4 -movflags frag_keyframe+faststart -master_pl_publish_rate 999999999 -master_pl_name master.m3u8 -hls_fmp4_init_filename init.mp4 " +
            "-hls_segment_filename " + Util.getOutputLIVEDir().getPath() + "/sec%v_%d.mp4 " + Util.getOutputLIVEDir().getPath() + "/sec%v.m3u8";

    public MuxManager(Context context, OutputObserver outputObserver) {
        super("MuxManager");

        this.context = context;
        this.outputObserver = outputObserver;

        Config.setLogLevel(Level.AV_LOG_INFO);
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
                        //muxLiveExecute((MessageObject.MuxLiveObject) msg.obj);

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END : {
                        int resolution = (int) msg.obj;

                        if (resolution == VIDEO_1080P) {
                            VIDEO_1080_STATUS = 0;
                        } else if (resolution == VIDEO_720P) {
                            VIDEO_720_STATUS = 0;
                        } else if (resolution == VIDEO_480P) {
                            VIDEO_480_STATUS = 0;
                        }
                        muxCancel();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_AUDIO_END : {
                        AUDIO_STATUS = 0;
                        muxCancel();

                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void processPipeStatusExecute() {
        VIDEO_1080_STATUS = 0;
        VIDEO_720_STATUS = 0;
        VIDEO_480_STATUS = 0;
        AUDIO_STATUS = 0;

        if (mode != Mode.LIVE) {
            VIDEO_1080_STATUS = 1;
            AUDIO_STATUS = 1;
        } else {
            VIDEO_1080_STATUS = 1;
            VIDEO_720_STATUS = 1;
            VIDEO_480_STATUS = 1;
            AUDIO_STATUS = 1;
        }
    }

    private boolean processPipeStatusCancel() {
        if (mode != Mode.LIVE) {
            if ((VIDEO_1080_STATUS | AUDIO_STATUS) == 0) {
                return true;
            } else {
                return false;
            }
        } else {
            if ((VIDEO_1080_STATUS | VIDEO_720_STATUS | VIDEO_480_STATUS | AUDIO_STATUS) == 0) {
                return true;
            } else {
                return false;
            }
        }
    }

    public String requestPipe() {
        String pipe = Config.registerNewFFmpegPipe(context);

        pipeList.add(pipe);

        return pipe;
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
        processPipeStatusExecute();

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void muxLiveExecute() {
        processPipeStatusExecute();

        if (liveFileObserver == null) {
            liveFileObserver = new LiveFileObserver(context, Util.getOutputLIVEDir());
            liveFileObserver.startWatching();
        }

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

//    private void muxLiveExecute(MessageObject.MuxLiveObject obj) {
//        mode = obj.getMode();
//
//        processPipeStatusExecute();
//
//        if (liveFileObserver == null) {
//            liveFileObserver = new LiveFileObserver(context, Util.getOutputLIVEDir(), obj.getS3Client());
//            liveFileObserver.startWatching();
//        }
//
//        if (ffmpeg == null) {
//            ffmpeg = new FFMPEGThread();
//            ffmpeg.start();
//        }
//    }

    private void muxCancel() {
        boolean isCancel = processPipeStatusCancel();

        if (isCancel == true) {
            for (final String pipe : pipeList) {
                Config.closeFFmpegPipe(pipe);
            }

            if (ffmpeg != null) {
                ffmpeg.quitThread();
                String output = ffmpeg.getVODFile();

                if (output != null) {
                    outputObserver.onCompleteVODFile(output);
                }

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
        private File VODFile;

        public FFMPEGThread() {
            super("ffmpeg");
        }

        public String getVODFile() {
            if (VODFile != null) {
                return VODFile.getPath();
            }
            return null;
        }

        @Override
        public void run() {
            String command;
            if (mode != Mode.LIVE) {
                VODFile = Util.getOutputVODFile();

                command = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + INPUT_AUDIO_OPT + INPUT_AUDIO + pipeList.get(1) + " -map 0:v -map 1:a" + OUTPUT + VODFile;

                FFmpeg.execute(command);
            } else {
                command = INPUT_LIVE_VIDEO_OPT + INPUT_LIVE_VIDEO + pipeList.get(0) + " " + INPUT_LIVE_VIDEO_OPT + INPUT_LIVE_VIDEO + pipeList.get(1) + " " + INPUT_LIVE_VIDEO_OPT + INPUT_LIVE_VIDEO + pipeList.get(2) + INPUT_LIVE_AUDIO_OPT +
                        INPUT_LIVE_AUDIO + pipeList.get(3) + MAP_LIVE_COMMAND + MUXING_LIVE_OPT;

                FFmpeg.execute(command);
            }
        }

        public void quitThread() {
            FFmpeg.cancel();
        }
    }
}
