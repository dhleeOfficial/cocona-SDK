package framework.Util;

public class Constant {
    public static class Camera {
        public static final float INIT_ZOOM_LEVEL = 1.0f;
        public static final float ZOOM_DELTA = 0.05f;

        public static final double EXPOSURE_DELTA = 0.1;

        public static final int AREA_FOCUS_SIZE = 200;
    }

    public static class Resolution {
        public static final int FHD_HEIGHT = 1920;
        public static final int FHD_WIDTH = 1080;
        public static final int FHD_BITRATE = 6000000;

        public static final int HD_HEIGHT = 1280;
        public static final int HD_WIDTH = 720;
        public static final int HD_BITRATE = 4000000;

        public static final int SD_HEIGHT = 854;
        public static final int SD_WIDTH = 480;
        public static final int SD_BITRATE = 1000000;

        public static final String FHD = "1080";
        public static final String HD = "720";
        public static final String SD = "480";
    }

    public static class BitRate{
        public static final int FHD_BITRATE = 6000*1024;
        public static final int HD_BITRATE = 2500*1024;
        public static final int SD_BITRATE = 1000*1024;
    }
}
