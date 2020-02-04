package framework.Enum;

public enum RecordState {
    STOP(0), START(1), PAUSE(2), RESUME(3);

    private final int value;

    RecordState(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
