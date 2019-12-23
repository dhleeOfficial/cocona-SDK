package framework.Manager;

import android.icu.text.AlphabeticIndex;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Size;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import framework.Message.MessageObject;
import framework.Message.ThreadMessage;

public class VideoManager extends HandlerThread implements ImageReader.OnImageAvailableListener {
    private Handler myHandler;
    private Size previewSize;
    private Handler muxHandler;

    // VIDEO
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 4000000;
    private static final int FRAME_RATE = 30;
    private static final float IFRAME_INTERVAL = 0.5f;
    private static final int TIMEOUT_USEC = 10000;

    private MediaFormat videoFormat;
    private MediaCodec videoCodec;
    private MediaCodecInfo videoCodecInfo;

    private String videoPipe;
    private BufferedOutputStream bufferedOutputStream = null;

    // STATUS
    private boolean isReady = false;
    private boolean isEOS = false;

    // WRITER Thread
    private MuxWriter muxWriter = null;
    private Queue<byte[]> encodeList = new LinkedList<byte[]>();

    static {
        System.loadLibrary("image-convert");
    }

    public native byte[] YUVtoBytes(ByteBuffer y, ByteBuffer u, ByteBuffer v, int yPixelStride, int yRowStride,
                                    int uPixelStride, int uRowStride, int vPixelStride, int vRowStride, int imgWidth, int imgHeight);


    public VideoManager() {
        super("VideoManager");
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch(msg.arg1) {
                    case ThreadMessage.RecordMessage.MSG_RECORD_START : {
                        initCodec((MessageObject.VideoRecord) msg.obj);

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_STOP : {
                        isEOS = true;

                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireNextImage();

            if (isReady == true){
                if (image != null) {
                    final Image.Plane[] planes = image.getPlanes();
                    final long timestamp = image.getTimestamp();

                    final Image.Plane yPlane = planes[0];
                    final Image.Plane uPlane = planes[1];
                    final Image.Plane vPlane = planes[2];
                    final int width = image.getWidth();
                    final int height = image.getHeight();

                    final byte[] buffer = YUVtoBytes(yPlane.getBuffer(),
                            uPlane.getBuffer(),
                            vPlane.getBuffer(),
                            yPlane.getPixelStride(),
                            yPlane.getRowStride(),
                            uPlane.getPixelStride(),
                            uPlane.getRowStride(),
                            vPlane.getPixelStride(),
                            vPlane.getRowStride(),
                            width,
                            height);

                    encode(buffer, timestamp, isEOS);
                }
            }
        } catch (NullPointerException ne) {
            ne.printStackTrace();
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void initMediaFormat() {
        videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, previewSize.getWidth(), previewSize.getHeight());

        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);;
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51);
        videoFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
    }

    private void initCodec(MessageObject.VideoRecord recordObj) {
        videoPipe = recordObj.getPipe();
        previewSize = recordObj.getSize();
        muxHandler = recordObj.getMuxHandler();

        initMediaFormat();
        videoCodecInfo = initMeidaCodeInfo();
        initMediaCodec();

        if (encodeList.isEmpty() == false) {
            encodeList.clear();
        }

        isReady = true;
    }

    private MediaCodecInfo initMeidaCodeInfo() {
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

    private void initMediaCodec() {
        videoCodec = null;

        if (videoCodecInfo != null) {
            try {
                videoCodec = MediaCodec.createByCodecName(videoCodecInfo.getName());

                videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                videoCodec.start();
            } catch (IOException ie) {
                ie.printStackTrace();
            }
        }
    }

    private void stopCodec() {
        videoCodec.stop();
        videoCodec.release();
        videoCodec = null;

        try {
            muxWriter.setIsRun(false);
            muxWriter.join();
            muxWriter = null;
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        try {
            if (bufferedOutputStream != null) {
                bufferedOutputStream.close();
                bufferedOutputStream = null;
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    private void encode(byte[] buffer, long timeStamp, boolean isEOS) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int inputBufferIndex = videoCodec.dequeueInputBuffer(-1);

        if (inputBufferIndex >= 0) {
            if (isEOS == true) {
                videoCodec.queueInputBuffer(inputBufferIndex, 0, 0, timeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                muxing(true, bufferInfo);
            } else {
                final ByteBuffer in = videoCodec.getInputBuffer(inputBufferIndex);

                in.clear();
                in.put(buffer);

                videoCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, timeStamp, 0);
                muxing(false, bufferInfo);
            }
        }
    }

    private void muxing(boolean isEOS, MediaCodec.BufferInfo bufferInfo) {
        while (true) {
            int outputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (isEOS == false) {
                    break;
                }
            } else  if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (bufferedOutputStream == null) {
                    try {
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(videoPipe));
                    } catch (FileNotFoundException fe) {
                        fe.printStackTrace();
                    }
                }
            } else if (outputBufferIndex >= 0) {
                if (bufferInfo.size != 0) {
                    if (bufferedOutputStream != null) {
                        final ByteBuffer out = videoCodec.getOutputBuffer(outputBufferIndex);

                        if (out != null) {
                            final byte[] outBytes = new byte[bufferInfo.size];

                            out.get(outBytes);

                            if (outBytes.length > 0) {
                                encodeList.add(outBytes);

                                if (muxWriter == null) {
                                    muxWriter = new MuxWriter();
                                    muxWriter.start();
                                }
                            }
                        }
                    }
                }

                videoCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isReady = false;
                    this.isEOS = false;
                    stopCodec();
                    break;
                }
            }
        }
    }

    private class MuxWriter extends Thread {
        private boolean isRun = true;

        @Override
        public void run() {
            while(isRun || !encodeList.isEmpty()) {
                try {
                    final byte[] data = encodeList.poll();

                    if (data != null) {
                        bufferedOutputStream.write(data);
                    } else {
                        if (isRun == false) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0,null));
        }

        public void setIsRun(boolean isRun) {
            this.isRun = isRun;
        }
    }
}
