package framework.Message;

import android.os.Handler;
import android.util.Size;

import java.io.BufferedOutputStream;

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

    public static class VideoObject {
        private final int width;
        private final int height;
        private final String pipe;
        private final Handler muxHandler;

        public VideoObject(int width, int height, String pipe, Handler muxHandler) {
            this.width = width;
            this.height = height;
            this.pipe = pipe;
            this.muxHandler = muxHandler;
        }

        public final int getWidth() {
            return width;
        }

        public final int getHeight() {
            return height;
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
