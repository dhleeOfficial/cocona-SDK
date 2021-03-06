package framework.Enum;

/**
 * LensFacing Enum : FRONT, BACK
 */
public enum LensFacing {
    FRONT(0), BACK(1), ETC(2);

    private final int value;

    LensFacing(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}