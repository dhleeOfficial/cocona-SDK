package framework.Enum;

public enum RecordSpeed {
    SLOW(0), NORMAL(1), FAST(2), PAUSE(3), RESUME(4);

    private final int value;

    RecordSpeed(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}