package framework.Util;

import android.util.Size;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Util {
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    public static Size getOptimalSize(List<Size> availSize) {
        return Collections.min(availSize, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                return diff(o1) - diff(o2);
            }

            private int diff(Size size) {
                return Math.abs(MAX_PREVIEW_WIDTH - size.getWidth()) + Math.abs(MAX_PREVIEW_HEIGHT - size.getHeight());
            }
        });
    }
}
