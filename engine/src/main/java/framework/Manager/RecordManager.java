package framework.Manager;

import android.content.Context;
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

import framework.Message.ThreadMessage;
import framework.Thread.FFmpegThread;
import framework.Util.RecordImageData;

public class RecordManager extends HandlerThread implements ImageReader.OnImageAvailableListener, FFmpegThread.Callback {
    private Context context;
    private Handler myHandler;

    // VIDEO FORMAT VALUE
    private static final String MIME_TYPE = "video/avc";
    private static final int BIT_RATE = 2000000;
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 10;
    Queue<RecordImageData> dataQueue = new LinkedList<RecordImageData>();

    MediaFormat mediaFormat;
    MediaCodec mediaCodec;
    MediaCodecInfo mediaCodecInfo;

    private Size previewSize;

    // FFMPEGTHREAD
    private FFmpegThread fFmpegThread;
    private Thread thread;
    private String openPipe;
    private boolean isOpenPipe = false;

    BufferedOutputStream bufferedOutputStream = null;

    // STATUS
    private boolean isStart = false;
    private boolean isEOS = false;

    private MediaCodec.Callback codecCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            ByteBuffer in = codec.getInputBuffer(index);

            RecordImageData data = dataQueue.poll();

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
                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(openPipe));
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
                    fFmpegThread.requestTerminate();
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

    static {
        System.loadLibrary("image-convert");
    }

    public native byte[] YUVtoBytes(ByteBuffer y, ByteBuffer u, ByteBuffer v, int yPixelStride, int yRowStride,
                                     int uPixelStride, int uRowStride, int vPixelStride, int vRowStride, int imgWidth, int imgHeight);

    public RecordManager(Context context) {
        super("RecordManager");

        this.context = context;
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
                        mediaCodecInfo = initMeidaCodeInfo();
                        initMediaCodec();
                        initFFmpegThread();

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

                    dataQueue.add(new RecordImageData(buffer, image.getTimestamp(), isEOS));

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
    public void onReadyPipe(String pipe) {
        openPipe = pipe;

        thread = new Thread(fFmpegThread);
        thread.start();
    }

    @Override
    public void onOpenPipe() {
        isOpenPipe = true;
    }

    @Override
    public void onTerminatePipe() {
        thread.interrupt();

        fFmpegThread = null;
        thread = null;
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

        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, previewSize.getWidth(), previewSize.getHeight());
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);;
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
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
        mediaCodec = null;

        if (mediaCodecInfo != null) {
            try {
                mediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                mediaCodec.setCallback(codecCallback);
                mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                mediaCodec.start();
            } catch (IOException ie) {
                ie.printStackTrace();
            }
        }
    }

    private void initFFmpegThread() {
        if (fFmpegThread == null) {
            fFmpegThread = new FFmpegThread(context, this);
            fFmpegThread.requestPipe();
        }
    }

    private void stopCodec() {
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
    }
}
