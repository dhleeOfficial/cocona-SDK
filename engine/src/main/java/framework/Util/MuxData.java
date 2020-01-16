package framework.Util;

public class VideoMuxData {
    private byte[] buffer;
    private boolean isEOS;

    public VideoMuxData(byte[] buffer, boolean isEOS) {
        this.buffer = buffer;
        this.isEOS = isEOS;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public boolean getIsEOS() {
        return isEOS;
    }
}
