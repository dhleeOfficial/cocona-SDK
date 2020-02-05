package framework.SceneDetection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Pair;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

public class SceneDetection {
    private static String LABELS_FILE = "labels.txt";
    private static final String MODEL_FILE = "scene.tflite";
    private static final float MINIMUM_CONFIDENCE = 0.5f;
    private static int INPUT_SIZE = 224;

    private static Interpreter tfLite;
    private static String[] labels;

    private static Bitmap cropBitmap;
    private static float [][][][] inputValues = new float[1][INPUT_SIZE][INPUT_SIZE][3];

    public SceneDetection () {
    }

    public static void loadTFLite(Context context) {
        try {
            tfLite = new Interpreter(loadModelFile(context, MODEL_FILE));
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

    public static void readLabels(Context context) {
        try {
            InputStream labelsInput = context.getAssets().open(LABELS_FILE);
            BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
            String line;
            Vector<String> lines = new Vector<String>();
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            br.close();
            labels = Arrays.copyOf(lines.toArray(),
                    lines.size(),
                    String[].class);
        } catch (Exception ce) {
            ce.printStackTrace();
        }
    }

    public static ArrayList<Pair<String,Float>> recognizeImage(Bitmap bitmap) {
        convertImage(bitmap);
        float [][] outputValues = new float[1][labels.length];
        tfLite.run(inputValues, outputValues);
        ArrayList<Pair<String, Float>> results = new ArrayList<>();
        for (int i=0;i<labels.length;i++) {
            if (outputValues[0][i] > MINIMUM_CONFIDENCE) {
                results.add(new Pair<String, Float>(labels[i],outputValues[0][i]));
            }
        }
        return results;
    }

    private static void convertImage(Bitmap bitmap) {

        cropBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];

        int width = cropBitmap.getWidth();
        int height = cropBitmap.getHeight();

        cropBitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];

                inputValues[0][i][j][0] = (float) ((((pixelValue >> 16) & 0xFF)) / 255.0);
                inputValues[0][i][j][1] = (float) ((((pixelValue >> 8) & 0xFF)) / 255.0);
                inputValues[0][i][j][2] = (float) (((pixelValue & 0xFF)) / 255.0);
            }
        }

    }
}
