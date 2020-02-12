package framework.Util;

public class Constant {
    public static class Camera {
        public static final float INIT_ZOOM_LEVEL = 1.0f;
        public static final float ZOOM_DELTA = 0.05f;
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

    public static class Inference {

        public static final int OD_INPUT_SIZE = 300;
        public static final int OD_NUM_DETECTIONS = 10;
        public static final float OD_IMAGE_MEAN = 128.0f;
        public static final float OD_IMAGE_STD = 128.0f;
        public static final int OD_NUM_THREADS = 1;

        public static final boolean TRAVEL_IS_QUANTIZED = true;
        public static final String TRAVEL_MODEL_FILE = "travelmode.tflite";
        public static final String TRAVEL_LABELS_FILE = "file:///android_asset/travelmode.txt";

        public static final boolean DAILY_IS_QUANTIZED = true;
        public static final String DAILY_MODEL_FILE = "dailymode.tflite";
        public static final String DAILY_LABELS_FILE = "file:///android_asset/dailymode.txt";

        public static final int EVENT_INPUT_SIZE = 80;
        public static final String EVENT_MODEL_FILE = "video_score.tflite";
        public static final int EVENT_NUM_FRAMES = 5;


        public static final int SCENE_INPUT_SIZE = 224;
        public static final String SCENE_MODEL_FILE = "scene.tflite";
        public static final String SCENE_LABELS_FILE = "labels.txt";

        public static final float MIN_CONFIDENCE = 0.5f;

        public static final int FPS = 30;
        public static final int SCENE_INTERVAL = 30;
        public static final int EVENT_INTERVAL = 30;
        public static final int THUMBNAIL_INTERVAL = 300;
        public static final int CONVERT_MILLISECONDS = 1000;
    }

    public static class BitRate {
        public static final int FHD_BITRATE = 6000 * 1024;
        public static final int HD_BITRATE = 2500 * 1024;
        public static final int SD_BITRATE = 1000 * 1024;
    }

    public static class Video {
        public static final int FRAME_RATE = 30;
        public static final float IFRAME_INTERVAL = 0.5f;
    }

    public static class Audio {
        public static final int SAMPLE_RATE = 44100;
        public static final int CH_COUNT = 1;
        public static final int BIT_RATE = 128000;
        public static final int HEADER_SIZE = 7;
        public static final int TIMEOUT_USEC = 10000;
        public static final int SAMPLES_PER_FRAME = 1470;
        public static final int SPARE_BUFFER_SIZE = 120;
    }

    public static class Live {
        public static final String THUMBNAIL_FILE = "thumbnail.jpeg";
    }
}
