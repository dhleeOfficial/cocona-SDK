package framework.Util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Size;

import java.nio.ByteBuffer;
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

    public static Matrix getTransformationMatrix(final Size src, final Size dst, int applyRotation, boolean aspectRatio) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();

        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);
            matrix.postRotate(applyRotation);
        }

        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (aspectRatio) {
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    public static void convertImageToBytes(Image.Plane[] planes, byte[][] bytes) {
        int count = 0;

        for (final Image.Plane plane : planes) {
            ByteBuffer byteBuffer = plane.getBuffer();

            if (bytes[count] == null) {
                bytes[count] = new byte[byteBuffer.capacity()];
            }
            byteBuffer.get(bytes[count]);

            ++count;
        }
    }

//    public static void convertYUV420ToARGB8888(byte[] in, int width, int height, int[] out) {
//        for (int i = 0, yp = 0; i < height; ++i) {
//            int uvp = (width * height) + (i >> 1) * width;
//            int u = 0;
//            int v = 0;
//
//            for (int j = 0; j < width; ++j, ++yp) {
//                int y = 0xff & in[yp];
//
//                if ((j & 1) == 0) {
//                    v = 0xff & in[uvp++];
//                    u = 0xff & in[uvp++];
//                }
//                out[yp] = YuvToRGB(y, u, v);
//            }
//        }
//    }

    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YuvToRGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
            }
        }
    }

    private static int YuvToRGB(int y, int u, int v) {
        final int kMaxChannelValue = 262143;

        y = (y - 16) < 0 ? 0 : (y - 16);
        u -= 128;
        v -= 128;

        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }

    public static Bitmap byteArrayToBitmap(byte[] bytes) {
        Bitmap bitmap = null;

        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        return bitmap;
    }

    public static byte[] serialize(Image image) {
        if (image == null) {
            return null;
        }

        Image.Plane[] planes = image.getPlanes();
        byte[] bytes = new byte[image.getWidth() * image.getHeight()];
        int next = 0;

        for (Image.Plane plane : planes) {
            ByteBuffer byteBuffer = plane.getBuffer();
            byteBuffer.position(0);

            int nBytes = byteBuffer.remaining();

            plane.getBuffer().get(bytes, next, nBytes);
            next += nBytes;
        }

        return bytes;
    }

    public static Bitmap convertImageToBitmap(Image image) {
        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        final Image.Plane[] planes = image.getPlanes();
        ByteBuffer byteBuffer = planes[0].getBuffer();

        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - (pixelStride * width);

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);

        bitmap.copyPixelsFromBuffer(byteBuffer);

        return Bitmap.createBitmap(bitmap, 0, 0, width,height);
    }
}
