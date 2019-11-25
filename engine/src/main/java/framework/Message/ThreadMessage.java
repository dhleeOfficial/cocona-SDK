package framework.Message;

public class ThreadMessage {
    public class EngineMessage {
        public static final int MSG_ENGINE_SETUP_PREVIEW = 0;
        public static final int MSG_ENGINE_STOP_PREVIEW = 1;
        public static final int MSG_ENGINE_FLASH = 2;
        public static final int MSG_ENGINE_LENS = 3;
        public static final int MSG_ENGINE_RECORD = 4;
        public static final int MSG_ENGINE_ZOOM = 5;
        public static final int MSG_ENGINE_EXPOSURE = 6;
        public static final int MSG_ENGINE_AREA_FOCUS = 7;
        public static final int MSG_ENGINE_LOCK_FOCUS = 8;
        public static final int MSG_ENGINE_FILTER = 9;
    }

    public class RecordMessage {
        public static final int MSG_RECORD_STATUS = 0;
    }

    public class FFmpegMessage {
        public static final int MSG_FFMPEG_START = 0;
        public static final int MSG_FFMPEG_STOP = 1;
    }
}
