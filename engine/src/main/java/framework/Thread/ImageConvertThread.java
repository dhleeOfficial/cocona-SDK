package framework.Thread;

import framework.Util.Util;

public class ImageConvertThread extends Thread {
    private CallBack callBack;

    private int width;
    private int height;
    private int yRowStride;
    private int uvRowStride;
    private int uvPixelStride;
    private byte[][] bytes;

    public ImageConvertThread(int width, int height, int yRowStride, int uvRowStride, int uvPixelStride, byte[][] bytes, CallBack callBack) {
        super("ImageConvertThread");

        this.width = width;
        this.height = height;
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
        this.bytes = bytes;
        this.callBack = callBack;
    }

    public interface CallBack {
        void imageProcessDone(int[] rgb);
    }

    @Override
    public void run() {
        int[] rgbBytes = new int[width * height];

        Util.convertYUV420ToARGB8888(bytes[0], bytes[1], bytes[2], width, height, yRowStride, uvRowStride, uvPixelStride, rgbBytes);

        bytes = null;

        if (callBack != null) {
            callBack.imageProcessDone(rgbBytes);
        }
    }
}
