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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.Util.VideoMuxData;

public class EncoderManager extends HandlerThread {
    private String name;
    private Handler handler;
    private Callback callback;

    private int videoWidth;
    private int videoHeight;
    private int bitRate;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int FRAME_RATE = 30;
    private static final float IFRAME_INTERVAL = 0.5f;

    private MediaCodecInfo mediaCodecInfo;
    private MediaFormat mediaFormat;
    private MediaCodec mediaCodec;
    private Surface surface;

    private String pipe;
    private Handler muxHandler;
    private FileOutputStream fileOutputStream = null;
    private BufferedOutputStream bufferedOutputStream = null;

    private PipeOpener pipeOpener = null;
    private MuxWriter muxWriter;
    private BlockingQueue<VideoMuxData> muxList = new LinkedBlockingQueue<VideoMuxData>();

    private boolean isReady = false;
    private boolean isPause = false;

    public interface Callback {
        void initDone();
    }

    public EncoderManager(String name, int videoHeight, int videoWidth, int bitRate, Callback callback){
        super(name);

        this.name = name;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.bitRate = bitRate;
        this.callback = callback;
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
                        initCodec();

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
                    case ThreadMessage.RecordMessage.MSG_RECORD_PAUSE : {
                        isPause = true;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_RESUME : {
                        isPause = false;

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
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51);
        mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mediaCodecInfo = getMediaCodecInfo();

        try {
            mediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = mediaCodec.createInputSurface();
            callback.initDone();

            mediaCodec.start();
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        isPause = false;
        isReady = true;
    }

    private void setInfo(MessageObject.VideoObject obj) {
        videoWidth = obj.getWidth();
        videoHeight = obj.getHeight();
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
        if (isReady == false) {
            return;
        }

        if (endOfStream == true) {
            isReady = false;
        }

        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (endOfStream == false) {
                    break;
                }
            } else  if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (pipeOpener == null) {
                    pipeOpener = new PipeOpener();
                    pipeOpener.start();
                }
            } else if (outputBufferIndex >= 0) {
                if (bufferInfo.size != 0) {
                    final ByteBuffer out = mediaCodec.getOutputBuffer(outputBufferIndex);

                    if (out != null) {
                        byte[] outBytes = new byte[bufferInfo.size];

                        out.get(outBytes);

                        if (outBytes.length > 0) {
                            VideoMuxData data = new VideoMuxData(outBytes, endOfStream);

                            muxList.add(data);

                            if (muxWriter == null) {
                                muxWriter = new MuxWriter();
                                muxWriter.start();
                            }
                        }
                    }
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    stopCodec();
                    break;
                }
            }
        }
    }

    private void stopCodec() {
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;

        if (muxWriter != null) {
            try {
                muxWriter.join();
                muxWriter = null;
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }

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
        pipeOpener = null;
    }

    private class PipeOpener extends Thread {
        @Override
        public void run() {
            if (bufferedOutputStream == null) {
                try {
                    if (pipe != null) {
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pipe));
                    }
                } catch (FileNotFoundException fe) {
                    fe.printStackTrace();
                }
            }
        }
    }

    private class MuxWriter extends Thread {
        @Override
        public void run() {
            while(true) {
                try {
                    if (bufferedOutputStream != null) {
                        final VideoMuxData data = muxList.take();

                        if (data != null) {
                            if (isPause == false) {
                                bufferedOutputStream.write(data.getBuffer());
                                bufferedOutputStream.flush();
                            }
                            if (data.getIsEOS() == true) {
                                muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0, videoHeight));

                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }
}
