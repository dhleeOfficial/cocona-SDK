package framework.Enum;

import android.hardware.camera2.CaptureRequest;

/**
 * Filter Enum : OFF, MONO, NEGATIVE, SOLARIZE, SEPIA, POSTERIZE, WHITEBOARD, BLACKBOARD, AQUA
 * TBD Enum function
 */
public enum Filter {
    OFF(0, CaptureRequest.CONTROL_EFFECT_MODE_OFF),
    MONO(1, CaptureRequest.CONTROL_EFFECT_MODE_MONO),
    NEGATIVE(2, CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE),
    SOLARIZE(3, CaptureRequest.CONTROL_EFFECT_MODE_SOLARIZE),
    SEPIA(4, CaptureRequest.CONTROL_EFFECT_MODE_SEPIA),
    POSTERIZE(5, CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE),
    WHITEBOARD(6, CaptureRequest.CONTROL_EFFECT_MODE_WHITEBOARD),
    BLACKBOARD(7, CaptureRequest.CONTROL_EFFECT_MODE_BLACKBOARD),
    AQUA(8, CaptureRequest.CONTROL_EFFECT_MODE_AQUA);

    private final int value;
    private int filter;

    Filter(int value, int filter) {
        this.value = value;
        this.filter = filter;
    }
    public void setFilter(Filter filter) { this.filter = filter.filter; }

    public int getValue() {
        return value;
    }
    public int getFilter() { return filter; }
}
