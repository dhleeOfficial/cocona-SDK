package framework.Enum;

public enum TouchType {
    // TODO : EXPOSURECHANGE
    NOTHANDLE(0), AREAFOCUS(1), LOCKFOCUS(2), EXPOSURECHANGE(3);

    private final int value;

    TouchType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
