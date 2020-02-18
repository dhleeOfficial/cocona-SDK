package framework.Manager;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.Level;

import java.io.File;
import java.util.ArrayList;

import framework.Engine.EngineObserver;
import framework.Enum.Mode;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.Util.Constant;
import framework.Util.LiveFileObserver;
import framework.Util.Util;

public class MuxManager extends HandlerThread {
    private Context context;
    private Handler myHandler;
    private EngineObserver engineObserver;
    private CancelHandler cancelHandler;

    private boolean isConvert = false;
    private String srcFileName;
    private String dstPath;

    ArrayList<String> pipeList = new ArrayList<String>();

    // MODE STATUS
    private Mode mode = Mode.TRAVEL;

    private int VIDEO_1080_STATUS = 0;
    private int VIDEO_720_STATUS = 0;
    private int VIDEO_480_STATUS = 0;
    private int AUDIO_STATUS = 0;

    private FFMPEGThread ffmpeg;

    private LiveFileObserver liveFileObserver;
    private LiveFileObserver liveFileObserver1;
    private LiveFileObserver liveFileObserver2;
    private LiveFileObserver liveFileObserver3;

    private String srcDir;

    // VOD FFMPEG COMMAND SET
    private final String INPUT_VIDEO_OPT = "-thread_queue_size 1024";
    private final String INPUT_AUDIO_OPT = " -thread_queue_size 2048";
    private final String INPUT_VIDEO = " -f h264 -r 30 -i ";
    private final String INPUT_AUDIO = " -f aac -i ";
    private final String OUTPUT = " -muxdelay 0 -vsync 1 -max_muxing_queue_size 9999 -c copy -f mp4 ";

    // LIVE TEST
    private final String INV = "-thread_queue_size 1024 -f h264 -r 24 -i ";
    private final String INA = " -thread_queue_size 2048 -f aac -i ";
    private final String VIDEO1080 = " -map 0:v -map 3:a -b:v:0 6000k -muxdelay 0 -flags +cgop -shortest -g 24 -c copy -bsf:a aac_adtstoasc -f hls -hls_list_size 0 -hls_time 5 -hls_allow_cache 1" +
            " -hls_segment_type fmp4 -movflags faststart -master_pl_publish_rate 999999999 -hls_fmp4_init_filename 1920x1080_init.mp4 -hls_segment_filename " + Util.getOutputLiveDirByResolution(Constant.Resolution.FHD) + "/1920x1080_%d.mp4 " + Util.getOutputLiveDirByResolution(Constant.Resolution.FHD) + "/1920x1080.m3u8";
    private final String VIDEO720 = " -map 1:v -map 3:a -b:v:1 2500k -muxdelay 0 -flags +cgop -shortest -g 24 -c copy -bsf:a aac_adtstoasc -f hls -hls_list_size 0 -hls_time 5 -hls_allow_cache 1" +
            " -hls_segment_type fmp4 -movflags faststart -master_pl_publish_rate 999999999 -hls_fmp4_init_filename 1280x720_init.mp4 -hls_segment_filename " + Util.getOutputLiveDirByResolution(Constant.Resolution.HD) + "/1280x720_%d.mp4 " + Util.getOutputLiveDirByResolution(Constant.Resolution.HD) + "/1280x720.m3u8";
    private final String VIDEO480 = " -map 2:v -map 3:a -b:v:2 1000k -muxdelay 0 -flags +cgop -shortest -g 24 -c copy -bsf:a aac_adtstoasc -f hls -hls_list_size 0 -hls_time 5 -hls_allow_cache 1" +
            " -hls_segment_type fmp4 -movflags faststart -master_pl_publish_rate 999999999 -hls_fmp4_init_filename 854x480_init.mp4 -hls_segment_filename " + Util.getOutputLiveDirByResolution(Constant.Resolution.SD) + "/854x480_%d.mp4 " + Util.getOutputLiveDirByResolution(Constant.Resolution.SD) + "/854x480.m3u8";

    // CONVERT HLS FORMAT FROM VOD FILE
    private final String SET_LOGLEVEL = "-loglevel warning -y";
    private final String INPUT_OPT = " -vsync 1 -i ";
    private final String MAPPING_INFO = " -map 0:v -map 0:a -map 1:v -map 1:a -map 2:v -map 2:a -var_stream_map \"v:0,a:0 v:1,a:1 v:2,a:2\"";
    private final String HLS_OPT = " -master_pl_name \"master.m3u8\" -c copy -muxdelay 0 -max_muxing_queue_size 9999 -copyts -vsync 0 -g 15 -flags +cgop -f hls -hls_flags" +
            " +independent_segments -hls_time 5 -hls_segment_type fmp4 -hls_list_size 0 -movflags frag_keyframe+faststart -vsync 2 -r 30 -hls_segment_filename ";

    public MuxManager(Context context, EngineObserver engineObserver) {
        super("MuxManager");

        this.context = context;
        this.engineObserver = engineObserver;
        cancelHandler = new CancelHandler();

        Config.setLogLevel(Level.AV_LOG_INFO);
    }

    private class CancelHandler extends Handler {
        public static final int MSG_MUX_CANCEL = 1;

        public CancelHandler() {}

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_MUX_CANCEL : {
                    muxCancel();
                }
            }
        }
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.MuxMessage.MSG_MUX_START : {
                        MessageObject.MuxObject muxObj = (MessageObject.MuxObject) msg.obj;
                        mode = muxObj.getMode();
                        srcDir = muxObj.getSrcDir();
                        muxExecute();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_LIVE_START : {
                        mode = Mode.LIVE;
                        muxLiveExecute((MessageObject.LiveObject) msg.obj);

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END : {
                        int resolution = (int) msg.obj;

                        if (resolution == Constant.Resolution.FHD_WIDTH) {
                            VIDEO_1080_STATUS = 0;
                        } else if (resolution == Constant.Resolution.HD_WIDTH) {
                            VIDEO_720_STATUS = 0;
                        } else if (resolution == Constant.Resolution.SD_WIDTH) {
                            VIDEO_480_STATUS = 0;
                        }
                        checkStatus();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_AUDIO_END : {
                        AUDIO_STATUS = 0;
                        checkStatus();

                        return true;
                    }
                    case ThreadMessage.MuxMessage.MSG_MUX_CONVERT_FORMAT : {
                        convertArchiveFormat((MessageObject.TransformObject) msg.obj);

                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void processPipeStatusExecute() {
        VIDEO_1080_STATUS = 1;
        VIDEO_720_STATUS = 1;
        VIDEO_480_STATUS = 1;
        AUDIO_STATUS = 1;
    }

    private boolean processPipeStatusCancel() {
        if ((VIDEO_1080_STATUS | VIDEO_720_STATUS | VIDEO_480_STATUS | AUDIO_STATUS) == 0) {
            return true;
        } else {
            return false;
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
        isConvert = false;

        if (ffmpeg != null) {
            ffmpeg = null;
        }

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void muxLiveExecute(MessageObject.LiveObject liveObject) {
        processPipeStatusExecute();
        isConvert = false;

        initLiveFileObserver(liveObject);
        Util.getMasterM3u8(Util.getOutputLiveDir(srcDir).getPath());

        if (ffmpeg != null) {
            ffmpeg = null;
        }

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void checkStatus() {
        boolean isCancel = processPipeStatusCancel();

        if (isCancel == true) {
            cancelHandler.sendEmptyMessageDelayed(cancelHandler.MSG_MUX_CANCEL,100);

        }
    }

    private void muxCancel(){
        for (final String pipe : pipeList) {
            Config.closeFFmpegPipe(pipe);
        }

        if (ffmpeg != null) {
            ffmpeg.quitThread();
            ArrayList<String> output = ffmpeg.getVODFile();

            if (output != null) {
                engineObserver.onCompleteVODFile(output);
            }

            ffmpeg.interrupt();
            ffmpeg = null;
        }

        if (liveFileObserver != null) {
            liveFileObserver.close();
            liveFileObserver = null;
        }

        if (liveFileObserver1 != null) {
            liveFileObserver1.close();
            liveFileObserver1 = null;
        }

        if (liveFileObserver2 != null) {
            liveFileObserver2.close();
            liveFileObserver2 = null;
        }

        if (liveFileObserver3 != null) {
            liveFileObserver3.close();
            liveFileObserver3 = null;
        }

        pipeList.clear();
    }

    private void convertArchiveFormat(MessageObject.TransformObject transformObject) {
        srcFileName = transformObject.getSrcFileName();

        File file = new File(Util.getOutputVODFolder(Constant.Resolution.HD).getPath() + File.separator + srcFileName);

        if (file.exists() != true) {
            return;
        }

        dstPath = transformObject.getDstPath();
        isConvert = true;

        if (ffmpeg != null) {
            ffmpeg = null;
        }

        if (ffmpeg == null) {
            ffmpeg = new FFMPEGThread();
            ffmpeg.start();
        }
    }

    private void initLiveFileObserver(MessageObject.LiveObject liveObject) {
        if (liveFileObserver == null) {
            liveFileObserver = new LiveFileObserver(Util.getOutputLiveDir(srcDir), null, liveObject.getLiveStreamingData(), engineObserver);
            liveFileObserver.startWatching();
        }

        if (liveFileObserver1 == null) {
            liveFileObserver1 = new LiveFileObserver(Util.getOutputLiveDirByResolution(Constant.Resolution.FHD), Constant.Resolution.FHD, liveObject.getLiveStreamingData(), engineObserver);
            liveFileObserver1.startWatching();
        }

        if (liveFileObserver2 == null) {
            liveFileObserver2 = new LiveFileObserver(Util.getOutputLiveDirByResolution(Constant.Resolution.HD), Constant.Resolution.HD, liveObject.getLiveStreamingData(), engineObserver);
            liveFileObserver2.startWatching();
        }

        if (liveFileObserver3 == null) {
            liveFileObserver3 = new LiveFileObserver(Util.getOutputLiveDirByResolution(Constant.Resolution.SD), Constant.Resolution.SD, liveObject.getLiveStreamingData(), engineObserver);
            liveFileObserver3.startWatching();
        }
    }

    private class FFMPEGThread extends Thread {
        private File VODFile;
        private File VODFile1;
        private File VODFile2;

        public FFMPEGThread() {
            super("ffmpeg");
        }

        public ArrayList<String> getVODFile() {
            if ((VODFile != null) && (VODFile1 != null) && (VODFile2 != null)){
                ArrayList<String> outputArray = new ArrayList<String>();

                outputArray.add(VODFile.getPath());
                outputArray.add(VODFile1.getPath());
                outputArray.add(VODFile2.getPath());

                return outputArray;
            }
            return null;
        }

        @Override
        public void run() {
            String command;

            if (isConvert == true) {
                String input = Util.getOutputVODFolder(Constant.Resolution.HD) + "/" + srcFileName;
                String input1 = Util.getOutputVODFolder(Constant.Resolution.SD) + "/" + srcFileName;

                command = SET_LOGLEVEL + INPUT_OPT + input + INPUT_OPT + input + INPUT_OPT + input1 +
                        MAPPING_INFO + HLS_OPT + "\"" + dstPath + "/sec%v_%d.mp4\" " + dstPath + "/sec%v.m3u8";

                FFmpeg.execute(command);
            } else {
                if (mode != Mode.LIVE) {
                    ArrayList<File> fileList = Util.getOutputVODFile(srcDir);
                    VODFile = fileList.get(0);
                    VODFile1 = fileList.get(1);
                    VODFile2 = fileList.get(2);

                    String input = " -map 0:v -map 3:a -s 1920x1080 -b:v 6000k -maxrate 8000k -bufsize 6000k" + OUTPUT + VODFile;
                    String input1 = " -map 1:v -map 3:a -s 1280x720 -b:v 3000k -maxrate 3750k -bufsize 3000k" + OUTPUT + VODFile1;
                    String input2 = " -map 2:v -map 3:a -s 854x480 -b:v 1000k -maxrate 1250k -bufsize 1000k" + OUTPUT + VODFile2;

//                    command = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + " " + INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(1) +
//                            " " + INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(2) + INPUT_AUDIO_OPT + INPUT_AUDIO + pipeList.get(3) + " -map 0:v -map 3:a -map 1:v -map 3:a -map 2:v -map 3:a" +
//                            OUTPUT + VODFile + OUTPUT + VODFile1 + OUTPUT + VODFile2;

                    command = INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(0) + " " + INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(1) +
                            " " + INPUT_VIDEO_OPT + INPUT_VIDEO + pipeList.get(2) + INPUT_AUDIO_OPT + INPUT_AUDIO + pipeList.get(3) + input + input1 + input2;

                    FFmpeg.execute(command);
                } else {
                    command = INV + pipeList.get(0) + " " + INV + pipeList.get(1) + " " + INV + pipeList.get(2) + INA + pipeList.get(3) + VIDEO1080 + VIDEO720 + VIDEO480;

                    FFmpeg.execute(command);
                }
            }
        }

        public void quitThread() {
            FFmpeg.cancel();
        }
    }
}
