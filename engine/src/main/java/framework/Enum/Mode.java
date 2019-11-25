package framework.Enum;

public enum Mode {
    TRAVEL(0), EVENT(1);

    private final int value;

    Mode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
