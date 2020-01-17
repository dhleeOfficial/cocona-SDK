//package framework.Manager;
//
//import android.graphics.Bitmap;
//import android.graphics.ImageFormat;
//import android.graphics.Rect;
//import android.media.Image;
//import android.media.ImageReader;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaCodecList;
//import android.media.MediaFormat;
//
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.Message;
//import android.util.Log;
//import android.util.Size;
//
//import androidx.annotation.NonNull;
//import androidx.collection.CircularArray;
//
//import java.io.BufferedOutputStream;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.lang.ref.WeakReference;
//import java.nio.ByteBuffer;
//import java.util.LinkedList;
//import java.util.Queue;
//
//import framework.Enum.Mode;
//import framework.Enum.RecordSpeed;
//import framework.Message.MessageObject;
//import framework.Message.ThreadMessage;
//import framework.Util.Util;
//import framework.Util.VideoMuxData;
//
//// FIXME : Deprecated this class
//public class VideoManager extends HandlerThread implements ImageReader.OnImageAvailableListener {
//    private Handler myHandler;
//    private Size previewSize;
//    private Handler muxHandler;
//
//    // VIDEO
//    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
//    private static final int BIT_RATE = 4000000;
//    private static final int FRAME_RATE = 30;
//    private static final float IFRAME_INTERVAL = 0.5f;
//
//    private MediaCodecInfo videoCodecInfo;
//
//    private MediaFormat videoFormat1080;
//    private MediaCodec videoCodec1080;
//    private String videoPipe1080;
//    private BufferedOutputStream bufferedOutputStream1080 = null;
//
//    // STATUS
//    private boolean isReady = false;
//    private boolean isEOS = false;
//    private boolean isPause = false;
//    private boolean isSlow = false;
//    private boolean isLive = false;
//    private Mode mode = Mode.TRAVEL;
//
//    // WRITER Thread
//    private MuxWriter muxWriter = null;
//    private Queue<VideoMuxData> muxList = new LinkedList<VideoMuxData>();
//
//    private byte[] rawBuffer;
//    private byte[] muxBuffer;
//
//    static {
//        System.loadLibrary("image-convert");
//    }
//
//    public native byte[] YUVtoBytes(ByteBuffer y, ByteBuffer u, ByteBuffer v, int yPixelStride, int yRowStride,
//                                    int uPixelStride, int uRowStride, int vPixelStride, int vRowStride, int imgWidth, int imgHeight);
//
//    public VideoManager() {
//        super("VideoManager");
//    }
//
//    @Override
//    protected void onLooperPrepared() {
//        super.onLooperPrepared();
//
//        myHandler = new Handler(new Handler.Callback() {
//            @Override
//            public boolean handleMessage(@NonNull Message msg) {
//                switch(msg.arg1) {
//                    case ThreadMessage.RecordMessage.MSG_RECORD_START : {
//                        initCodec1080((MessageObject.VideoRecord) msg.obj);
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_STOP : {
//                        isEOS = true;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_SLOW : {
//                        isSlow = true;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_NORMAL : {
//                        isSlow = false;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_FAST : {
//                        isSlow = false;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_PAUSE : {
//                        isPause = true;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_RESUME : {
//                        isPause = false;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_MODE : {
//                        mode = (Mode) msg.obj;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_LIVE_START : {
//                        initCodecs((MessageObject.VideoLive) msg.obj);
//                        isLive = true;
//
//                        return true;
//                    }
//                    case ThreadMessage.RecordMessage.MSG_RECORD_LIVE_STOP : {
//                        isEOS = true;
//                        isLive = false;
//
//                        return true;
//                    }
//                }
//                return false;
//            }
//        });
//    }
//
//    @Override
//    public void onImageAvailable(ImageReader reader) {
//        Image image = null;
//        try {
//            image = reader.acquireNextImage();
//
//            if (isReady == true) {
//                if (image != null) {
//                    if (isEOS == true) {
//                        isReady = false;
//                    }
////                    if ((imgWidth == 0) && (imgHeight == 0)) {
////                        imgWidth = image.getWidth();
////                        imgHeight = image.getHeight();
////                    }
//
//                    final Image.Plane[] planes = image.getPlanes();
//
//                    final Image.Plane yPlane = planes[0];
//                    final Image.Plane uPlane = planes[1];
//                    final Image.Plane vPlane = planes[2];
//                    final int width = image.getWidth();
//                    final int height = image.getHeight();
//                    final long timestamp = image.getTimestamp();
//
//                    if (mode != Mode.LIVE) {
//
//////

////                        WeakReference<byte[]> weakReference = new WeakReference<byte[]>(YUVtoBytes(yPlane.getBuffer(),
////                                uPlane.getBuffer(),
////                                vPlane.getBuffer(),
////                                yPlane.getPixelStride(),
////                                yPlane.getRowStride(),
////                                uPlane.getPixelStride(),
////                                uPlane.getRowStride(),
////                                vPlane.getPixelStride(),
////                                vPlane.getRowStride(),
////                                width,
////                                height));
////
////                        rawBuffer = weakReference.get();
////
////                        encode(rawBuffer, timestamp, isEOS);
//                        encode(Util.imageToMat(image), timestamp, isEOS);
//                        rawBuffer = null;
//
//                    } else {
//                        WeakReference<byte[]> weakReference = new WeakReference<byte[]>(YUVtoBytes(yPlane.getBuffer(),
//                                uPlane.getBuffer(),
//                                vPlane.getBuffer(),
//                                yPlane.getPixelStride(),
//                                yPlane.getRowStride(),
//                                uPlane.getPixelStride(),
//                                uPlane.getRowStride(),
//                                vPlane.getPixelStride(),
//                                vPlane.getRowStride(),
//                                width,
//                                height));
//
//                        rawBuffer = weakReference.get();
//
//                        encode(rawBuffer, timestamp, isEOS);
//                        rawBuffer = null;
//                    }
//                }
//            }
//        } catch (NullPointerException ne) {
//            ne.printStackTrace();
//        } finally {
//            if (image != null) {
//                image.close();
//            }
//        }
//    }
//
//    public final Handler getHandler() {
//        return myHandler;
//    }
//
//    private void initMediaFormat1080() {
//        videoFormat1080 = MediaFormat.createVideoFormat(MIME_TYPE, previewSize.getWidth(), previewSize.getHeight());
//
//        videoFormat1080.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);;
//        videoFormat1080.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
//        videoFormat1080.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//        videoFormat1080.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
//        videoFormat1080.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51);
//        videoFormat1080.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
//    }
//
//    private void initCodec1080(MessageObject.VideoRecord recordObj) {
//        videoPipe1080 = recordObj.getPipe();
//        previewSize = recordObj.getSize();
//        muxHandler = recordObj.getMuxHandler();
//
//        initMediaFormat1080();
//        videoCodecInfo = initMeidaCodeInfo();
//        initMediaCodec1080();
//
//        if (muxList.isEmpty() == false) {
//            muxList.clear();
//        }
////
////        if (encodeList.isEmpty() == false) {
////            encodeList.clear();
////        }
//
////        if (rawList.isEmpty() == false) {
////            rawList.clear();
////        }
//
//        isReady = true;
//    }
//
//    private void initMediaCodec1080() {
//        videoCodec1080 = null;
//
//        if (videoCodecInfo != null) {
//            try {
//                videoCodec1080 = MediaCodec.createByCodecName(videoCodecInfo.getName());
//
//                videoCodec1080.configure(videoFormat1080, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//                //videoCodec.setCallback(callback);
//                videoCodec1080.start();
//            } catch (IOException ie) {
//                ie.printStackTrace();
//            }
//        }
//    }
////
////    private void initCodecs(MessageObject.VideoLive obj) {
////        previewSize = obj.getSize();
////        muxHandler = obj.getMuxHandler();
////
////        videoPipe1080 = obj.getPipe1();
//////        videoPipe720 = obj.getPipe2();
//////        videoPipe360 = obj.getPipe3();
////
////        initMediaFormats();
////        videoCodecInfo = initMeidaCodeInfo();
////        initMediaCodecs();
////
////        isReady = true;
////    }
//
//    private void initMediaFormats() {
//        initMediaFormat1080();
//
//    }
//
//    private void initMediaCodecs() {
//        videoCodec1080 = null;
////        videoCodec720 = null;
////        videoCodec360 = null;
//
//        if (videoCodecInfo != null) {
//            try {
//                videoCodec1080 = MediaCodec.createByCodecName(videoCodecInfo.getName());
////                videoCodec720 = MediaCodec.createByCodecName(videoCodecInfo.getName());
////                videoCodec360 = MediaCodec.createByCodecName(videoCodecInfo.getName());
//
//                videoCodec1080.configure(videoFormat1080, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
////                videoCodec720.configure(videoFormat720, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
////                videoCodec360.configure(videoFormat360, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//
//                videoCodec1080.start();
////                videoCodec720.start();
////                videoCodec360.start();
//            } catch (IOException ie) {
//                ie.printStackTrace();
//            }
//        }
//    }
//
//    private MediaCodecInfo initMeidaCodeInfo() {
//        int codecNum = MediaCodecList.getCodecCount();
//
//        for (int i = 0; i < codecNum; ++i) {
//            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
//
//            if (mediaCodecInfo.isEncoder() == false) {
//                continue;
//            }
//
//            String[] types = mediaCodecInfo.getSupportedTypes();
//
//            for (int j = 0; j < types.length; ++j) {
//                if (types[j].equalsIgnoreCase(MIME_TYPE)) {
//                    return mediaCodecInfo;
//                }
//            }
//        }
//
//        return null;
//    }
//
//    private void stopCodec() {
//
//
//        videoCodec1080.stop();
//        videoCodec1080.release();
//        videoCodec1080 = null;
//
//        try {
//            //muxWriter.setIsRun(false);
//            muxWriter.join();
//            //rawEncoder = null;
//            muxWriter = null;
//        } catch (InterruptedException ie) {
//            ie.printStackTrace();
//        }
//
//        try {
//            if (bufferedOutputStream1080 != null) {
//                bufferedOutputStream1080.close();
//                bufferedOutputStream1080 = null;
//            }
//        } catch (IOException ie) {
//            ie.printStackTrace();
//        }
//                    //rawEncoder = null;
//
//    }
//
//    private void encode(byte[] buffer, long timeStamp, boolean isEOS) {
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//        int inputBufferIndex = videoCodec1080.dequeueInputBuffer(0);
//
//        if (inputBufferIndex >= 0) {
//            if (isEOS == true) {
//                videoCodec1080.queueInputBuffer(inputBufferIndex, 0, 0, timeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                muxing(true, bufferInfo);
//            } else {
//                final ByteBuffer in = videoCodec1080.getInputBuffer(inputBufferIndex);
//
//                in.clear();
//                in.put(buffer);
//
//                videoCodec1080.queueInputBuffer(inputBufferIndex, 0, buffer.length, timeStamp, 0);
//
//                muxing(false, bufferInfo);
//            }
//        }
//    }
//
//    private void muxing(boolean isEOS, MediaCodec.BufferInfo bufferInfo) {
//        while (true) {
//            int outputBufferIndex = videoCodec1080.dequeueOutputBuffer(bufferInfo, 0);
//
//            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                if (isEOS == false) {
//                    break;
//                }
//            } else  if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                if (bufferedOutputStream1080 == null) {
//                    try {
//                        Log.e("VIDEOMANAGER", "pipe name : " + videoPipe1080);
//                        Log.e("VIDEOMANAGER", "FileOutputStream create");
//                        bufferedOutputStream1080 = new BufferedOutputStream(new FileOutputStream(videoPipe1080));
//                        //bufferedOutputStream1080 = new BufferedOutputStream(new FileOutputStream(Util.getOutputTTTTFile()));
//                        Log.e("VIDEOMANAGER", "CREATE DONE");
//                    } catch (FileNotFoundException fe) {
//                        fe.printStackTrace();
//                    }
//                }
//            } else if (outputBufferIndex >= 0) {
//                if (bufferInfo.size != 0) {
//                    if (bufferedOutputStream1080 != null) {
//                        final ByteBuffer out = videoCodec1080.getOutputBuffer(outputBufferIndex);
//
//                        if (out != null) {
//                            WeakReference<byte[]> weakReference = new WeakReference<byte[]>(new byte[bufferInfo.size]);
//                            muxBuffer = weakReference.get();
//
//                            out.get(muxBuffer);
//
//                            if (muxBuffer.length > 0) {
//                                VideoMuxData data = new VideoMuxData(muxBuffer, isEOS);
//
//                                muxList.add(data);
//                                //encodeList.add(muxBuffer);
//
//                                if (muxWriter == null) {
//                                    muxWriter = new MuxWriter();
//                                    muxWriter.start();
//                                }
//                                muxBuffer = null;
//                            }
//                        }
//                    }
//                }
//
//                videoCodec1080.releaseOutputBuffer(outputBufferIndex, false);
//
//                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    isReady = false;
//                    this.isEOS = false;
//
////                    if (isLive == false) {
////                        muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0, null));
////                    }
//
//                    stopCodec();
//                    break;
//                }
//            }
//        }
//    }

////
//    private class MuxWriter extends Thread {
//        private boolean isRun = true;
//
//        @Override
//        public void run() {
//            while(isRun || !muxList.isEmpty()) {
//                try {
//                    final VideoMuxData data = muxList.poll();
//
//                    if (data != null) {
//                        if (isPause == false) {
//                            bufferedOutputStream1080.write(data.getBuffer());
//                        }
//                        if (data.getIsEOS() == true) {
//                            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0,null));
//
//                            break;
//                        }
//                    }
////                    else {
////                        if (isRun == false) {
////                            break;
////                        }
////                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            //muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_VIDEO_END, 0,null));
//        }
//
//        public void setIsRun(boolean isRun) {
//            this.isRun = isRun;
//        }
//    }
//}
