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
        public static final int MSG_ENGINE_SPEED_RECORD = 10;
        public static final int MSG_ENGINE_MODE = 11;
        public static final int MSG_ENGINE_LIVE = 12;
        public static final int MSG_ENGINE_CONVERT_FORMAT = 13;
    }

    public class InferenceMessage {
        public static final int MSG_INFERENCE_SETUP = 0;
        public static final int MSG_INFERENCE_SETMODE = 1;
        public static final int MSG_INFERENCE_SETRECORD = 2;
        public static final int MSG_INFERENCE_SETLIVE = 3;
        public static final int MSG_INFERENCE_SETSPEED = 4;
        public static final int MSG_INFERENCE_SETPATH = 5;
    }

    public class RecordMessage {
        public static final int MSG_RECORD_START = 0;
        public static final int MSG_RECORD_STOP = 1;
        public static final int MSG_RECORD_SLOW = 2;
        public static final int MSG_RECORD_NORMAL = 3;
        public static final int MSG_RECORD_FAST = 4;
        public static final int MSG_RECORD_PAUSE = 5;
        public static final int MSG_RECORD_RESUME = 6;
        public static final int MSG_RECORD_FRAME_AVAILABLE = 7;
    }

    public class MuxMessage {
        public static final int MSG_MUX_START = 0;
        public static final int MSG_MUX_LIVE_START = 1;
        public static final int MSG_MUX_VIDEO_END = 2;
        public static final int MSG_MUX_AUDIO_END = 3;
        public static final int MSG_MUX_CONVERT_FORMAT = 4;
    }
}
