package framework.Util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import android.media.Image;
import android.os.Environment;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Util {
    private static final int MAX_PREVIEW_WIDTH = 1280;
    private static final int MAX_PREVIEW_HEIGHT = 720;

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

    public static File getOutputVODFolder(String folder) {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "CubiDir" + File.separator + "VOD" + folder);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        return mediaStorageDir;
    }


    public static ArrayList<File> getOutputVODFile(String srcDir) {
        ArrayList<String> resolutionList = new ArrayList<String>();

        resolutionList.add(Constant.Resolution.FHD);
        resolutionList.add(Constant.Resolution.HD);
        resolutionList.add(Constant.Resolution.SD);

        ArrayList<File> fileArrayList = new ArrayList<File>();
        int size = resolutionList.size();

        for (int i = 0; i < size; ++i) {
            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),srcDir + File.separator + "VOD" + resolutionList.get(i));

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

            fileArrayList.add(mediaFile);
        }

        return fileArrayList;
    }

    public static File getOutputLiveDir(String srcDir) {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                 srcDir + File.separator + "LIVE");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        return mediaStorageDir;
    }

    public static File getOutputLiveDirByResolution(String resolution) {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "CubiDir" + File.separator + "LIVE" + File.separator + resolution);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        return mediaStorageDir;
    }

    public static File getOutputHLSDir() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                "CubiDir" + File.separator + "HLS");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        return mediaStorageDir;
    }

    public static File getOutputScoreFile(String srcDir) {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                srcDir + File.separator + "SCORE");

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

    public static File getOutputLabelFile(String srcDir) {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                srcDir + File.separator + "LABEL");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + timeStamp + ".json");

        return mediaFile;
    }

    public static void rotateMatrix (int orientation, float[] tempMatrix) {
        if (orientation != 0) {
            android.opengl.Matrix.rotateM(tempMatrix, 0, orientation * 90, 0, 0, 1);
        }
        switch (orientation) {
            case 3 :
                android.opengl.Matrix.translateM(tempMatrix,0,-1,0,0);
                break;
            case 2 :
                android.opengl.Matrix.translateM(tempMatrix,0,-1,-1,0);
                break;
            case 1 :
                android.opengl.Matrix.translateM(tempMatrix,0,0, -1,0);
                break;
            default :
                break;
        }
    }

    public static void saveBitmap(File file, final Bitmap bitmap) {
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public static File getMasterM3u8(String path) {
        File file = new File(path+"/master.m3u8");
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(file);

            fileWriter.write("#EXTM3U\n#EXT-X-VERSION:7\n\n");

            fileWriter.write("#EXT-X-STREAM-INF:BANDWIDTH="+Constant.BitRate.FHD_BITRATE+",RESOLUTION=1080x1920\n");
            fileWriter.write("1080/1920x1080.m3u8\n\n");

            fileWriter.write("#EXT-X-STREAM-INF:BANDWIDTH="+Constant.BitRate.HD_BITRATE+",RESOLUTION=720x1280\n");
            fileWriter.write("720/1280x720.m3u8\n\n");

            fileWriter.write("#EXT-X-STREAM-INF:BANDWIDTH="+Constant.BitRate.SD_BITRATE+",RESOLUTION=480x854\n");
            fileWriter.write("480/854x480.m3u8");

            fileWriter.flush();
            fileWriter.close();
        } catch(IOException ie) {
            ie.printStackTrace();
        }

        return file;
    }
}
