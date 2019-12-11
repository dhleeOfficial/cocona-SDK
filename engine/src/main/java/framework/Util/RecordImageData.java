package framework.Util;

public class RecordImageData {
    byte[] buffer;
    long presentationTimeUS;
    boolean isEOS;

    public RecordImageData(byte[] buffer, long presentationTimeUS, boolean isEOS) {
        this.buffer = buffer.clone();
        this.presentationTimeUS = presentationTimeUS;
        this.isEOS = isEOS;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public long getPresentationTimeUS() {
        return presentationTimeUS;
    }

    public boolean getIsEOS() {
        return isEOS;
    }
}
