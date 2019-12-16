package framework.Manager;

import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Size;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import framework.Message.ThreadMessage;
import framework.Thread.FFmpegThread;
import framework.Util.RecordData;

public class VideoManager extends HandlerThread implements ImageReader.OnImageAvailableListener, FFmpegThread.Callback {
    private Handler myHandler;
    private Size previewSize;

    // VIDEO
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 4000000;
    private static final int FRAME_RATE = 30;
    private static final float IFRAME_INTERVAL = 0.5f;

    MediaFormat videoFormat;
    MediaCodec videoCodec;
    MediaCodecInfo videoCodecInfo;

    Queue<RecordData> videoQueue = new LinkedList<RecordData>();

    private Thread videoThread = null;

    private String videoPipe;
    private boolean isOpenPipe = false;

    BufferedOutputStream bufferedOutputStream = null;

    // STATUS
    private boolean isStart = false;
    private boolean isEOS = false;

    static {
        System.loadLibrary("image-convert");
    }

    public native byte[] YUVtoBytes(ByteBuffer y, ByteBuffer u, ByteBuffer v, int yPixelStride, int yRowStride,
                                    int uPixelStride, int uRowStride, int vPixelStride, int vRowStride, int imgWidth, int imgHeight);

    private MediaCodec.Callback videoCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            ByteBuffer in = codec.getInputBuffer(index);

            final RecordData data = videoQueue.poll();

            if ((data != null) && (in != null)) {
                in.clear();
                in.put(data.getBuffer());

                if (data.getIsEOS() == false) {
                    codec.queueInputBuffer(index, 0, data.getBuffer().length, data.getPresentationTimeUS(), 0);
                } else {
                    codec.queueInputBuffer(index, 0, data.getBuffer().length, data.getPresentationTimeUS(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            } else {
                codec.queueInputBuffer(index, 0, 0, 0, 0);
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if (index >= 0) {
                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    //codec.releaseOutputBuffer(index, false);
                    //return;
                }

                ByteBuffer out = codec.getOutputBuffer(index);
                byte[] outBytes = new byte[info.size];

                if (out != null) {
                    out.get(outBytes);
                }

                if (isOpenPipe == true) {
                    if (bufferedOutputStream == null) {
                        try {
                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(videoPipe));
                        } catch (FileNotFoundException fe) {
                            fe.printStackTrace();
                        }
                    }

                    if (bufferedOutputStream != null) {
                        try {
                            if (outBytes.length > 0) {
                                bufferedOutputStream.write(outBytes);
                            }
                        } catch (IOException ie) {
                            ie.printStackTrace();
                        }
                    }
                }

                codec.releaseOutputBuffer(index, false);

                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    stopCodec();
                    isEOS = false;
                    FFmpegThread.getInstance().requestTerminate();
                }
            }
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

        }
    };

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
                        initMediaFormat((Size) msg.obj);
                        videoCodecInfo = initMeidaCodeInfo();
                        initMediaCodec();

                        FFmpegThread.getInstance().requestPipe();

                        isStart = true;

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

            if (isStart == true) {
                if (image != null) {
                    final Image.Plane[] planes = image.getPlanes();

                    Image.Plane yPlane = planes[0];
                    Image.Plane uPlane = planes[1];
                    Image.Plane vPlane = planes[2];

                    byte[] buffer = YUVtoBytes(yPlane.getBuffer(),
                            uPlane.getBuffer(),
                            vPlane.getBuffer(),
                            yPlane.getPixelStride(),
                            yPlane.getRowStride(),
                            uPlane.getPixelStride(),
                            uPlane.getRowStride(),
                            vPlane.getPixelStride(),
                            vPlane.getRowStride(),
                            image.getWidth(),
                            image.getHeight());

                    videoQueue.add(new RecordData(buffer, image.getTimestamp(), isEOS));

                    if (isEOS == true) {
                        isStart = false;
                    }
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

    @Override
    public void onReadyPipe(String videoPipe, String audioPipe) {
        this.videoPipe = videoPipe;

        if (videoThread == null) {
            videoThread = new Thread(FFmpegThread.getInstance());
            videoThread.start();
        }
    }

    @Override
    public void onOpenPipe() {
        isOpenPipe = true;
    }

    @Override
    public void onTerminatePipe() {
        videoThread.interrupt();
        videoThread = null;

        isOpenPipe = false;

        try {
            bufferedOutputStream.close();
            bufferedOutputStream = null;
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void initMediaFormat(Size size) {
        previewSize = size;

        videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, previewSize.getWidth(), previewSize.getHeight());
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);;
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51);
        videoFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
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
                videoCodec.setCallback(videoCallback);
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
    }
}
