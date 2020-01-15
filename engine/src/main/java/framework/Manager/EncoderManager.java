package framework.Manager;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.Util.Util;
import framework.Util.VideoMuxData;

public class EncoderManager extends HandlerThread {
    private Handler handler;
    private Handler handler1;
    private Callback callback;

    private int videoWidth;
    private int videoHeight;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 4000000;
    private static final int FRAME_RATE = 30;
    private static final float IFRAME_INTERVAL = 0.5f;

    private MediaCodecInfo mediaCodecInfo;
    private MediaFormat mediaFormat;
    private MediaCodec mediaCodec;
    private Surface surface;

    private final Object sync = new Object();

    private String name;
    private String pipe;
    private Handler muxHandler;
    private FileOutputStream fileOutputStream = null;
    private BufferedOutputStream bufferedOutputStream = null;

    private MuxWriter muxWriter;
    private Queue<VideoMuxData> muxList = new LinkedList<VideoMuxData>();

    private boolean isReady = false;

    private int drainCount;

    public interface Callback {
        void initDone();
        void eosDone();
        void drainDone();
    }

    public EncoderManager(String name, int videoWidth, int videoHeight, Callback callback){
        super(name);

        this.name = name;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.callback = callback;

        initCodec();
    }

    public final Handler getHandler() {
        return handler;
    }

    public Surface getSurface() {
        return surface;
    }

    public void reFrame() {
        handler.sendMessage(handler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_FRAME_AVAILABLE, 0, null));
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.RecordMessage.MSG_RECORD_START : {
                        setInfo((MessageObject.VideoObject) msg.obj);

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_FRAME_AVAILABLE : {

                        drain(false);

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_STOP : {
                        mediaCodec.signalEndOfInputStream();
                        drain(true);

                        return true;
                    }
                }
                return false;
            }
        });

        handler1 = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.RecordMessage.MSG_RECORD_FRAME_AVAILABLE : {
                        drain(false);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void initCodec() {
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight);

        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);;
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51);
        mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mediaCodecInfo = getMediaCodecInfo();

        try {
            mediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = mediaCodec.createInputSurface();
            mediaCodec.start();
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        isReady = true;
        callback.initDone();
    }

    private void setInfo(MessageObject.VideoObject obj) {
        pipe = obj.getPipe();
        muxHandler = obj.getMuxHandler();
        muxList.clear();
    }

    private MediaCodecInfo getMediaCodecInfo() {
        int codecNum = MediaCodecList.getCodecCount();

        for (int i = 0; i < codecNum; ++i) {
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);

            if (mediaCodecInfo.isEncoder() == false) {
                continue;
            }

            String[] types = mediaCodecInfo.getSupportedTypes();

            for (int j = 0; j < types.length; ++j) {
                if (types[j].equalsIgnoreCase(MIME_TYPE)) {
                    return mediaCodecInfo;
                }
            }
        }

        return null;
    }

    private synchronized void drain(boolean endOfStream) {
        Log.e(name, "==========" + drainCount++ + "=============");
        if (isReady == false) {
            return;
        }

        if (endOfStream == true) {
            isReady = false;
            callback.eosDone();
        }

        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (endOfStream == false) {
                    break;
                }
            } else  if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                if (fileOutputStream == null && bufferedOutputStream == null) {
//                    try {
//
//                        Log.e(name, "stream create, pipe : " + pipe);
//                        fileOutputStream = new FileOutputStream(pipe, true);
//                        Log.e(name, "FileOutputStream done");
//                        bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
//                        Log.e(name, "stream done");
//
//                    } catch (FileNotFoundException fe) {
//                        fe.printStackTrace();
//                    } catch (Exception ie) {
//                        ie.printStackTrace();
//                    }
//                }
            } else if (outputBufferIndex >= 0) {
                if (bufferInfo.size != 0) {
                    //Log.e(name, "======bufferInfoSize====" + bufferInfo.size + "=============");

                    //if (bufferedOutputStream != null) {
                        final ByteBuffer out = mediaCodec.getOutputBuffer(outputBufferIndex);

                        if (out != null) {
                            byte[] outBytes = new byte[bufferInfo.size];

                            out.get(outBytes);

                            if (outBytes.length > 0) {
                                if (bufferedOutputStream == null) {
                                    if (videoHeight == 1080) {
                                        //try {
                                            Log.e(name, "CREATE fileOutputStream");
                                            Log.e(name, "PIPE name : " + pipe);
//                                            File pipeFile = new File(pipe);
//                                            FileDescriptor fd = null;
//
//                                            if (pipeFile.exists() == true) {
                                        try {
                                            //bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pipe));
                                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(Util.getOutput1080File()));

                                        } catch (FileNotFoundException fe) {
                                            fe.printStackTrace();
                                        }
//                                            }
//
//                                        } catch (FileNotFoundException fe) {
//                                            fe.printStackTrace();
//                                        }
                                    } else if (videoHeight == 720){
                                        try {
                                            //bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pipe));
                                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(Util.getOutput720File()));

                                        } catch (FileNotFoundException fe) {
                                            fe.printStackTrace();
                                        }
//                                        try {
//                                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(Util.getOutputTTTTFile()));
//                                        } catch (FileNotFoundException fe) {
//                                            fe.printStackTrace();
//                                        }
                                    } else if (videoHeight == 480){
                                        try {
                                            //bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pipe));
                                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(Util.getOutput480File()));

                                        } catch (FileNotFoundException fe) {
                                            fe.printStackTrace();
                                        }
//                                        try {
//                                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(Util.getOutputTTTTFile()));
//                                        } catch (FileNotFoundException fe) {
//                                            fe.printStackTrace();
//                                        }
                                    }
                                }
                                try {
                                    Log.e(name, "WRITE fileOutputStream");
                                    bufferedOutputStream.write(outBytes);
                                } catch (IOException ie) {
                                    ie.printStackTrace();
                                }
//                                try {
//                                    if (bufferedOutputStream != null) {
//                                        Log.e(name, "Write start");
//                                        bufferedOutputStream.write(outBytes);
//                                        bufferedOutputStream.flush();
//                                        Log.e(name, "Write END");
//                                    }
//                                } catch (IOException ie) {
//                                    ie.printStackTrace();
//                                }

//                                VideoMuxData data = new VideoMuxData(outBytes, endOfStream);
//
//                                Log.e(name, "muxing completed");
//                                muxList.add(data);
//
//                                if (muxWriter == null) {
//                                    muxWriter = new MuxWriter();
//                                    muxWriter.start();
//                                }
                            }
                        }
                    //}
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    stopCodec();
                    break;
                }
            }
        }
        callback.drainDone();
    }

    private void stopCodec() {
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;

//        try {
//            muxWriter.join();
//            muxWriter = null;
//        } catch (InterruptedException ie) {
//            ie.printStackTrace();
//        }

        try {
            if (bufferedOutputStream != null) {
                bufferedOutputStream.close();
                bufferedOutputStream = null;
            }

            if (fileOutputStream != null) {
                fileOutputStream.close();
                fileOutputStream = null;
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    private class MuxWriter extends Thread {
        @Override
        public void run() {
            while(true || !muxList.isEmpty()) {
                try {
                    final VideoMuxData data = muxList.poll();

                    if (data != null) {
                        //if (isPause == false) {
                        if (bufferedOutputStream != null) {
                            Log.e(name, "Write start");
                            bufferedOutputStream.write(data.getBuffer());
                            bufferedOutputStream.flush();
                            Log.e(name, "Write end");
                        }
                        //}
                        if (data.getIsEOS() == true) {
                            //muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0,null));

                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
