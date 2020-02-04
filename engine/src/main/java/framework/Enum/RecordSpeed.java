package framework.Enum;

/**
 * RecordSpeed Enum : SLOW, NORMAL, FAST, PAUSE, RESUME
 */
public enum RecordSpeed {
    SLOW(0), NORMAL(1), FAST(2);

    private final int value;

    RecordSpeed(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}