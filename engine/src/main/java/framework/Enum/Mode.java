package framework.Enum;

/**
 * Mode Enum : TRAVEL, EVENT, LIVE, DAILY
 */
public enum Mode {
    TRAVEL(0), EVENT(1), LIVE(2), DAILY(3);

    private final int value;

    Mode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
