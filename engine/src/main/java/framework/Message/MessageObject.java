package framework.Message;

import android.os.Handler;
import android.util.Size;

import com.amazonaws.services.s3.AmazonS3Client;

import java.io.BufferedOutputStream;

import framework.Engine.LiveStreamingData;
import framework.Enum.LensFacing;
import framework.Enum.Mode;
import framework.Enum.RecordState;

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

    public static class LiveObject {
        private final boolean isLive;
        private final LiveStreamingData liveStreamingData;
        private final String srcDir;

        public LiveObject(boolean isLive, LiveStreamingData liveStreamingData, String srcDir) {
            this.isLive = isLive;
            this.liveStreamingData = liveStreamingData;
            this.srcDir = srcDir;
        }

        public boolean getIsLive() {
            return isLive;
        }

        public LiveStreamingData getLiveStreamingData() {
            return liveStreamingData;
        }

        public String getSrcDir() {
            return srcDir;
        }
    }

    public static class ThumbnailObject {
        private final boolean isLive;
        private final int orientation;
        private final String srcDir;

        public ThumbnailObject(boolean isLive, int orientation, String srcDir) {
            this.isLive = isLive;
            this.orientation = orientation;
            this.srcDir = srcDir;
        }

        public boolean getIsLive() {
            return isLive;
        }

        public int getOrientation() {
            return orientation;
        }

        public String getSrcDir() {
            return srcDir;
        }
    }

    public static class TransformObject {
        private final String srcFileName;
        private final String dstPath;

        public TransformObject(String srcFileName, String dstPath) {
            this.srcFileName = srcFileName;
            this.dstPath = dstPath;
        }

        public String getSrcFileName() {
            return srcFileName;
        }

        public String getDstPath() {
            return dstPath;
        }
    }

    public static class RecordObject {
        private final RecordState recordState;
        private final String srcDir;

        public RecordObject(RecordState recordState, String srcDir) {
            this.recordState = recordState;
            this.srcDir = srcDir;
        }

        public final RecordState getRecordState() {
            return recordState;
        }

        public final String getSrcDir() {
            return srcDir;
        }
    }

    public static class MuxObject {
        private final Mode mode;
        private final String srcDir;

        public MuxObject(Mode mode, String srcDir) {
            this.mode = mode;
            this.srcDir = srcDir;
        }

        public final Mode getMode() {
            return mode;
        }

        public final String getSrcDir() {
            return srcDir;
        }
    }
}
