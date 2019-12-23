package framework.Message;

import android.os.Handler;
import android.util.Size;

import framework.Enum.LensFacing;

public class MessageObject {
    public static class Box {
        private final Size size;
        private final int orientation;
        private final LensFacing lensFacing;

        public Box(Size size, int orientation, LensFacing lensFacing) {
            this.size = size;
            this.orientation = orientation;
            this.lensFacing = lensFacing;
        }

        public final Size getSize() {
            return size;
        }

        public final int getOrientation() {
            return orientation;
        }

        public final LensFacing getLensFacing() {
            return lensFacing;
        }
    }

    public static class VideoRecord {
        private final Size size;
        private final String pipe;
        private final Handler muxHandler;

        public VideoRecord(Size size, String pipe, Handler muxHandler) {
            this.size = size;
            this.pipe = pipe;
            this.muxHandler = muxHandler;
        }

        public final Size getSize() {
            return size;
        }

        public final String getPipe() {
            return pipe;
        }

        public final Handler getMuxHandler() {
            return muxHandler;
        }
    }

    public static class AudioRecord {
        private final String pipe;
        private final Handler muxHandler;

        public AudioRecord(String pipe, Handler muxHandler) {
            this.pipe = pipe;
            this.muxHandler = muxHandler;
        }

        public final String getPipe() {
            return pipe;
        }

        public final Handler getMuxHandler() {
            return muxHandler;
        }
    }
}
