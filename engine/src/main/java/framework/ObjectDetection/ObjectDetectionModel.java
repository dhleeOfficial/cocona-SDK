package framework.ObjectDetection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.tensorflow.lite.Interpreter;

public class ObjectDetectionModel implements Classifier {
    private static final int NUM_DETECTIONS = 10;
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    private static final int NUM_THREADS = 4;

    private boolean isModelQuantized;

    private int inputSize;
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    private float[][][] outputLocations;
    private float[][] outputClasses;
    private float[][] outputScores;
    private float[] numDetections;

    private ByteBuffer imageData;
    private Interpreter tfLite;

    private ObjectDetectionModel() {}

    private static MappedByteBuffer loadModelFile(AssetManager assetManager, String fileFd) {
        try {
            AssetFileDescriptor assetFileDescriptor = assetManager.openFd(fileFd);
            FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();

            return fileChannel.map(FileChannel.MapMode.READ_ONLY,
                                   assetFileDescriptor.getStartOffset(),
                                   assetFileDescriptor.getDeclaredLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Classifier create(final AssetManager assetManager,
                                    final Context context,
                                    final String modelFileName,
                                    final String labelFileName,
                                    final int inputSize,
                                    final boolean isModelQuantized) {
        final ObjectDetectionModel objectDetectionModel = new ObjectDetectionModel();

        try {
            InputStream labelInputStream = assetManager.open(labelFileName.split("file:///android_asset/")[1]);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(labelInputStream));

            String line;

            while((line = bufferedReader.readLine()) != null) {
                objectDetectionModel.labels.add(line);
            }

            bufferedReader.close();

            objectDetectionModel.inputSize = inputSize;
            objectDetectionModel.tfLite = new Interpreter(loadModelFile(assetManager, modelFileName));
            objectDetectionModel.isModelQuantized = isModelQuantized;

            int numBytesPerChannel = (isModelQuantized == true) ? 1 : 4;

            objectDetectionModel.imageData =
                    ByteBuffer.allocateDirect(1 * objectDetectionModel.inputSize * objectDetectionModel.inputSize * 3 * numBytesPerChannel);
            objectDetectionModel.imageData.order(ByteOrder.nativeOrder());
            objectDetectionModel.intValues = new int[objectDetectionModel.inputSize * objectDetectionModel.inputSize];
            objectDetectionModel.tfLite.setNumThreads(NUM_THREADS);
            objectDetectionModel.outputLocations = new float[1][NUM_DETECTIONS][4];
            objectDetectionModel.outputClasses = new float[1][NUM_DETECTIONS];
            objectDetectionModel.outputScores = new float[1][NUM_DETECTIONS];
            objectDetectionModel.numDetections = new float[1];

        } catch (IOException e) {
            e.printStackTrace();
        }
        return objectDetectionModel;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imageData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imageData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imageData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imageData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imageData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        Trace.endSection();

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Object[] inputArray = {imageData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("run");
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();

        // Show the best detections.
        // after scaling them back to the input size.

        final ArrayList<Recognition> recognitions = new ArrayList<>(NUM_DETECTIONS);
        for (int i = 0; i < NUM_DETECTIONS; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1] * inputSize,
                            outputLocations[0][i][0] * inputSize,
                            outputLocations[0][i][3] * inputSize,
                            outputLocations[0][i][2] * inputSize);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i,
                            labels.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            detection));
        }
        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    @Override
    public void enableStatLogging(boolean debug) {

    }

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {

    }

    @Override
    public void setNumThreads(int num_threads) {
        if (tfLite != null) {
            tfLite.setNumThreads(num_threads);
        }
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {
        if (tfLite != null) {
            tfLite.setUseNNAPI(isChecked);
        }
    }
}
