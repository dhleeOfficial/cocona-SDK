package framework.AutoEdit;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.Size;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import framework.Util.Util;

public class AutoEdit {
    private Bitmap[] originBitmap;
    private Bitmap[] resizeBitmap;

    private Mat originMat;
    private Mat lap;
    private MatOfDouble mean;
    private MatOfDouble dev;

    private int SKIP_COUNT = 5;
    private int CHANNEL = 3;
    private int INPUT_SIZE = 80;

    private float[][][][] inputValue;
    private float[][] outputValue;

    private long lastTS;
    private long currentTS;
    private long firstTS;

    private static Interpreter tfLite;
    private static String model = "video_score.tflite";

    private Size size;
    private String scoreList;
    private int imageCount;
    private int seqCount;
    private FileWriter fileWriter = null;
    private File outFile;

    static {
        if (OpenCVLoader.initDebug() == false) {
            Log.e("OPENCV", "INIT FAILED");
        } else {
            Log.e("OPENCV", "INIT SUCCESS");
        }
    }

    public AutoEdit() {
    }

    public void setImageSize(Size size) {
        this.size = size;
    }

    public void setFirstTS(long firstTS) {
        initVariable();
        this.firstTS = firstTS;
        //this.lastTS = firstTS;

        try {
            if (fileWriter == null) {
                outFile = Util.getOutputScoreFile();
                fileWriter = new FileWriter(outFile);

                fileWriter.write(String.format("start_time : %d", this.firstTS) + System.getProperty("line.separator"));
                fileWriter.write("seq,  blur,   score"+System.getProperty( "line.separator"));

            }
        } catch(IOException ie) {
            ie.printStackTrace();
        }
    }

    public void updateCurrentTS(long currentTS) {
        this.currentTS = currentTS;
    }

    public boolean isVerify() {
        long diff = currentTS - lastTS;

        if (diff >= 1000000L) {
            lastTS = currentTS;

            return true;
        }
        return false;
    }

    public String getScoreFile() {
        return outFile.getPath();
    }

    public void stop() {
        try {
            fileWriter.write("scoreFile = " + outFile.getPath() + System.getProperty("line.separator"));
            fileWriter.close();
            fileWriter = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void appendImage(Bitmap bitmap) {
        if (bitmap != null) {
            originBitmap[imageCount] = bitmap;
            resizeBitmap[imageCount] = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        }
        imageCount++;

        if (imageCount == 5) {
            scoreList = String.format("%d, %f, %f", seqCount, getBlurScore(), getEditingScore());

            try {
                fileWriter.write(scoreList + System.getProperty("line.separator"));
                Log.d("SCORE", scoreList);
            } catch (Exception e) {
                e.printStackTrace();
            }

            imageCount = 0;
            seqCount++;
        }
    }

    private void initMat() {
        lap = new Mat();
        originMat = new Mat(size.getWidth(), size.getHeight(), CvType.CV_8UC3);
        mean = new MatOfDouble();
        dev = new MatOfDouble();
    }

    private void initVariable() {
        resizeBitmap = null;
        originBitmap = null;
        inputValue = null;
        outputValue = null;

        resizeBitmap = new Bitmap[5];
        originBitmap = new Bitmap[5];

        inputValue = new float[SKIP_COUNT][INPUT_SIZE][INPUT_SIZE][CHANNEL];
        outputValue = new float[1][2];
    }

    public static void loadTFLite(Context context) {
        try {
            tfLite = new Interpreter(loadModelFile(context, model));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    private static MappedByteBuffer loadModelFile(Context context, String model) throws IOException {
        AssetFileDescriptor assetFileDescriptor = context.getAssets().openFd(model);
        FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void convertImage() {
        for (int i = 0; i < SKIP_COUNT; ++i) {
            int[] intValue = new int[INPUT_SIZE * INPUT_SIZE];

            resizeBitmap[i].getPixels(intValue, 0, resizeBitmap[i].getWidth(), 0, 0, resizeBitmap[i].getWidth(), resizeBitmap[i].getHeight());

            for (int j = 0; j < INPUT_SIZE; ++j) {
                for (int k = 0; k < INPUT_SIZE; ++k) {
                    int pixel = intValue[j * INPUT_SIZE + k];

                    inputValue[i][j][k][0] = (float) (((pixel >> 16) & 0xFF) / 255.0);
                    inputValue[i][j][k][1] = (float) (((pixel >> 8) & 0xFF) / 255.0);
                    inputValue[i][j][k][2] = (float) ((pixel & 0xFF) / 255.0);
                }
            }
        }
    }

    private double getBlurScore() {
        initMat();

        double score = 0;

        for (int i = 0; i < SKIP_COUNT; ++i) {
            Utils.bitmapToMat(originBitmap[i], originMat);
            Imgproc.cvtColor(originMat, originMat, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Laplacian(originMat, lap, CvType.CV_64F);

            Core.meanStdDev(lap, mean, dev);

            score += Math.pow(dev.get(0, 0)[0], 2);
        }

        score /= 5;

        return score;
    }

    private float getEditingScore() {
        float score = 0.0f;

        if (inputValue != null) {
            convertImage();
            tfLite.run(inputValue, outputValue);

            score = outputValue[0][1];
            initVariable();
        }
        return score;
    }
}
