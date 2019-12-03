package framework.Message;

import android.util.Size;

import framework.Enum.LensFacing;

public class MessageObject {
    public static class BoxMessageObject {
        private final Size size;
        private final int orientation;
        private final LensFacing lensFacing;

        public BoxMessageObject(Size size, int orientation, LensFacing lensFacing) {
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
}
