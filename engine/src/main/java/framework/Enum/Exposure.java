package framework.Enum;

public enum Exposure {
    BRIGHT(0), DARK(1);

    private final int value;

    Exposure(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}