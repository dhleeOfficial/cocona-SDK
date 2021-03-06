package framework.Util;

public class MuxData {
    private byte[] buffer;
    private boolean isEOS;

    public MuxData(byte[] buffer, boolean isEOS) {
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
