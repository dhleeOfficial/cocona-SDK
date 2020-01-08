package framework.Util;

import java.nio.ByteBuffer;

public class VideoData {
    private ByteBuffer yBuffer;
    private ByteBuffer uBuffer;
    private ByteBuffer vBuffer;

    private int yPixelStride;
    private int yRowStride;

    private int uPixelStride;
    private int uRowStride;

    private int vPixelStride;
    private int vRowStride;

    private long timeStamp;
    private boolean endOfStream;

    public VideoData(ByteBuffer y, ByteBuffer u, ByteBuffer v, int yp, int yr, int up, int ur, int vp, int vr, long ts, boolean eos) {
        yBuffer = y;
        uBuffer = u;
        vBuffer = v;

        yPixelStride = yp;
        yRowStride = yr;

        uPixelStride = up;
        uRowStride = ur;

        vPixelStride = vp;
        vRowStride = vr;

        timeStamp = ts;
        endOfStream = eos;
    }

    public ByteBuffer getYBuffer() {
        return yBuffer;
    }

    public ByteBuffer getUBuffer() {
        return uBuffer;
    }

    public ByteBuffer getVBuffer() {
        return vBuffer;
    }

    public int[] getIntValue() {
        int[] intValue = new int[6];

        intValue[0] = yPixelStride;
        intValue[1] = yRowStride;
        intValue[2] = uPixelStride;
        intValue[3] = uRowStride;
        intValue[4] = vPixelStride;
        intValue[5] = vRowStride;

        return intValue;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public boolean getEndOfStream() {
        return endOfStream;
    }
}
