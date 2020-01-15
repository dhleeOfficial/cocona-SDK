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

    // FIXME
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

    public static class VideoObject {
        private final String pipe;
        private final Handler muxHandler;

        public VideoObject(String pipe, Handler muxHandler) {
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

    public static class VideoLive {
        private final Size size;
        private final String pipe1;
        //private final String pipe2;
        //private final String pipe3;
        private final Handler muxHandler;

        public VideoLive(Size size, String pipe1, /*String pipe2, String pipe3,*/ Handler muxHandler) {
            this.size = size;
            this.pipe1 = pipe1;
            //this.pipe2 = pipe2;
            //this.pipe3 = pipe3;
            this.muxHandler = muxHandler;
        }

        public final Size getSize() {
            return size;
        }

        public final String getPipe1() {
            return pipe1;
        }

//        public final String getPipe2() {
//            return pipe2;
//        }
//
//        public final String getPipe3() {
//            return pipe3;
//        }

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
