package framework.Util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Environment;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    public static byte[][] convertImageToBytes(Image.Plane[] planes) {
        byte[][] bytes = new byte[3][];

        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer byteBuffer = planes[i].getBuffer();

            bytes[i] = new byte[byteBuffer.capacity()];
            byteBuffer.get(bytes[i]);
        }

        return bytes;
    }

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

    public static Bitmap imageToBitmap(Image image, float rotationDegrees) {

        assert (image.getFormat() == ImageFormat.NV21);

        // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
        ByteBuffer ib = ByteBuffer.allocate(image.getHeight() * image.getWidth() * 2);

        ByteBuffer y = image.getPlanes()[0].getBuffer();
        ByteBuffer cr = image.getPlanes()[1].getBuffer();
        ByteBuffer cb = image.getPlanes()[2].getBuffer();
        ib.put(y);
        ib.put(cb);
        ib.put(cr);

        YuvImage yuvImage = new YuvImage(ib.array(),
                ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0,
                image.getWidth(), image.getHeight()), 50, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Bitmap bitmap = bm;

        // On android the camera rotation and the screen rotation
        // are off by 90 degrees, so if you are capturing an image
        // in "portrait" orientation, you'll need to rotate the image.
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm,
                    bm.getWidth(), bm.getHeight(), true);
            bitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    public static byte[] convertBitmapToByteArray(Bitmap bitmap){
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            return baos.toByteArray();
        }finally {
            if(baos != null){
                try {
                    baos.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static byte[] convertBitmapToByteArrayUncompressed(Bitmap bitmap){
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        return byteBuffer.array();
    }

    public static byte[] YUVtoByteArray(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        byte[] result = new byte[width * height * 3 / 2];
        int stride = planes[0].getRowStride();

        if (stride == width) {
            planes[0].getBuffer().get(result, 0, width);
        } else {
            for (int row = 0; row < height; row++) {
                planes[0].getBuffer().position(row * stride);
                planes[0].getBuffer().get(result, row * width, width);
            }
        }

        stride = planes[1].getRowStride();

        byte[] rowBytesCb = new byte[stride];
        byte[] rowBytesCr = new byte[stride];

        for (int row = 0; row < height / 2; row++) {
            int rowOffset = width * height + width / 2 * row;

            planes[1].getBuffer().position(row * stride);
            planes[1].getBuffer().get(rowBytesCb, 0, width / 2);
            planes[2].getBuffer().position(row * stride);
            planes[2].getBuffer().get(rowBytesCr, 0, width / 2);

            for (int col = 0; col < width / 2; col++) {
                result[rowOffset + col * 2] = rowBytesCr[col];
                result[rowOffset + col * 2 + 1] = rowBytesCb[col];
            }
        }
        return result;
    }

    public static void scale(int srcW, int srcH, int dstW, int dstH, byte[] src, int srcOffset, byte[] dst, int dstOffset) {
        for (int y = 0; y < dstH; ++y) {
            int dstRowOffset = y * dstW;
            int srcRowOffset = (y * srcH) / dstH;

            for (int x = 0; x < dstW; ++x) {
                int srcX = (x * srcW) / dstW;

                dst[dstRowOffset + dstOffset] = src[srcRowOffset + srcX + srcOffset];
            }
        }
    }

    public static byte[] imageToMat(Image image) {

        Image.Plane[] planes = image.getPlanes();

        ByteBuffer buffer0 = planes[0].getBuffer();
        ByteBuffer buffer1 = planes[1].getBuffer();
        ByteBuffer buffer2 = planes[2].getBuffer();

        int offset = 0;

        int width = image.getWidth();
        int height = image.getHeight();

        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData1 = new byte[planes[1].getRowStride()];
        byte[] rowData2 = new byte[planes[2].getRowStride()];

        int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;

        // loop via rows of u/v channels

        int offsetY = 0;

        int sizeY =  width * height * bytesPerPixel;
        int sizeUV = (width * height * bytesPerPixel) / 4;

        for (int row = 0; row < height ; row++) {

            // fill data for Y channel, two row
            {
                int length = bytesPerPixel * width;
                buffer0.get(data, offsetY, length);

                if ( height - row != 1)
                    buffer0.position(buffer0.position()  +  planes[0].getRowStride() - length);

                offsetY += length;
            }

            if (row >= height/2)
                continue;

            {
                int uvlength = planes[1].getRowStride();

                if ( (height / 2 - row) == 1 ) {
                    uvlength = width / 2 - planes[1].getPixelStride() + 1;
                }

                buffer1.get(rowData1, 0, uvlength);
                buffer2.get(rowData2, 0, uvlength);

                // fill data for u/v channels
                for (int col = 0; col < width / 2; ++col) {
                    // u channel
                    data[sizeY + (row * width)/2 + col] = rowData1[col * planes[1].getPixelStride()];

                    // v channel
                    data[sizeY + sizeUV + (row * width)/2 + col] = rowData2[col * planes[2].getPixelStride()];
                }
            }

        }

        return data;
    }

    public static byte[] shortToByte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];

        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }

        return bytes;
    }

    public static File getOutput1080File() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "1080");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + timeStamp + ".mp4");
        return mediaFile;
    }

    public static File getOutput720File() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "720");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + timeStamp + ".mp4");
        return mediaFile;
    }

    public static File getOutput480File() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "480");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + timeStamp + ".mp4");
        return mediaFile;
    }

    public static File getOutputVideoSegDir() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "VIDEO_SEG");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        return mediaStorageDir;
    }

    public static File getOutputTEXTFile() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "AUTOEDIT");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + timeStamp + ".txt");
        return mediaFile;
    }

    public static File getOutputTTTTFile() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "TTTT");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        File file = new File(mediaStorageDir.getPath() + File.separator + "TTT.mp4");

        return file;
    }
}
