package framework.Enum;

/**
 * DeviceOrientation Enum : PORTRAIT, LANDSCAPE_RIGHT, PORTRAIT_UPSIDEDOWN, LANDSCAPE_LEFT, INIT
 */
public enum DeviceOrientation {
    PORTRAIT(0), LANDSCAPE_RIGHT(1), PORTRAIT_UPSIDEDOWN(2), LANDSCAPE_LEFT(3), INIT(4);

    private final int value;

    DeviceOrientation(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
