package com.cubi.smartcameraengine;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;
import android.content.Context;



import com.cubi.smartcameraengine.objectdetection.ImageUtils;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;
import org.opencv.android.Utils;
import org.opencv.android.OpenCVLoader;




public class AutoEditing {


    private Bitmap [] resizedBitmap;
    private Bitmap [] originalBitmap;
    Mat lap;
    Mat originalMat;
    MatOfDouble mean;
    MatOfDouble dev;
    private int no_frame  = 5;
    private int inputSize = 80;
    private int channel = 3;
    private int count = 0;
    private float[][][][] inputValues;
    private float [][] outputValues;
    private long lastCalculatedTimestamp;
    private long videoPresentTimestamp;
    private long startTimestamp;
    static Interpreter tflite;
    static String modelFile="video_score.tflite";

    private int w=1920;
    private int h=1080;
    private int seqcount =0;

    private String scorelist;
    static File file;
    static FileWriter fw = null;




    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
//            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
//            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }



    private void initMat()
    {
        lap = new Mat();
        originalMat = new Mat(w, h, CvType.CV_8UC3);
        mean = new MatOfDouble ();
        dev = new MatOfDouble ();
    }


    private void initVaraibles()
    {
        resizedBitmap   = new Bitmap[5];
        originalBitmap = new Bitmap[5];

        inputValues     = new float[no_frame][inputSize][inputSize][channel];
        outputValues    = new float[1][2];
    }

    // This is an simple example how to use tflite model for Auto Editing
    // input size (5, 80, 80, 3), output (1,2)
    // output score = output[0][1]


    private static MappedByteBuffer loadModelFile(Context context, String MODEL_FILE) throws IOException {

        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

    }


    private void convertImage() {
        for (int n = 0; n < 5; n++) {

            int[] intValues = new int[inputSize * inputSize];

            // Preprocess the image data from 0-255 int to normalized float based
            // on the provided parameters.

            Bitmap bitmap = resizedBitmap[n];
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            String output = String.format("%d resized Bitmap = %d, %d", n, width, height);
            Log.i("DEEP RUNNING", output);

            bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

            for (int i = 0; i < inputSize; ++i) {
                for (int j = 0; j < inputSize; ++j) {
                    int pixelValue = intValues[i * inputSize + j];

                    inputValues[n][i][j][0] = (float) ((((pixelValue >> 16) & 0xFF)) / 255.0);
                    inputValues[n][i][j][1] = (float) ((((pixelValue >> 8) & 0xFF)) / 255.0);
                    inputValues[n][i][j][2] = (float) (((pixelValue & 0xFF)) / 255.0);
                }
            }
        }
    }


    private double getBlurScore ()
    {
        initMat();

        double score=0;

//        loadoriginalImage();
        for (int i=0;i<5;i++){


            Utils.bitmapToMat(originalBitmap[i], originalMat);
            Imgproc.cvtColor(originalMat,originalMat,Imgproc.COLOR_BGR2GRAY);
            Imgproc.Laplacian(originalMat,lap,CvType.CV_64F);
            Core.meanStdDev(lap, mean, dev);
            double value = Math.pow(dev.get(0,0)[0],2);
            score+=value;


        }
        score=score/5;

        String out = String.format("blur score = %f", score);
        Log.i("blurscore", out);

        return score;
    }

    private float getEditingScore()
    {
        try {

            convertImage();
            Log.i("DEEP RUNNING", "resized Images");

            long startTime = System.currentTimeMillis();
            if(inputValues == null){
                Log.i("INPUT","NULL");
            }
            tflite.run(inputValues, outputValues);
            long endTime = System.currentTimeMillis();
            long diff = endTime - startTime;

            float score = outputValues[0][1];

            String out = String.format("score = %f duration = %d ms", score , diff);
            Log.i("DEEP RUNNING", out);


            resizedBitmap   = new Bitmap[5];
            originalBitmap = new Bitmap[5];
            inputValues     = new float[no_frame][inputSize][inputSize][channel];
            outputValues    = new float[1][2];

            return score;


        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void appendImages(Bitmap tempBmp) {

        if(tempBmp==null) {
            Log.i("ERROR","error");
        } else {
            originalBitmap[count] = tempBmp;
            resizedBitmap[count] = Bitmap.createScaledBitmap(tempBmp, inputSize, inputSize, true);
        }
        count += 1;


        if (count == 5) {
//            ImageUtils.saveBitmap(originalBitmap[4],seqcount);

            scorelist = String.format("%d, %f, %f", seqcount, getBlurScore(), getEditingScore());
            try{
                fw.write(scorelist + System.getProperty( "line.separator" ));

            } catch (Exception e) {
                e.printStackTrace();

            }

            count = 0;
            seqcount+=1;

        }
    }

    public void setStartVideoTimeStamp(long startTime)
    {
        initVaraibles();
        startTimestamp = startTime;
        lastCalculatedTimestamp = startTime;
        try {
            file = new File(CameraRecorder.txtPath);
            fw = new FileWriter (file);
            fw.write(String.format("start_time : %d", startTimestamp) + System.getProperty( "line.separator" ));
            fw.write("seq,  blur,   score"+System.getProperty( "line.separator" ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Boolean verifyNeedToSaveImageTime() {
        //이미지를 얻어와야 되는지를 확인
        long diff = videoPresentTimestamp - lastCalculatedTimestamp;

        if (diff >= 1000000L) {

            return true;
        } else {
            return false;
        }

    }

    public void setPresentVideoTimeStamp(long curTime)
    {

        videoPresentTimestamp = curTime;

    }

    public static void loadTFLite(Context context){

        //load tflite model
        try {

            tflite = new Interpreter(loadModelFile(context, modelFile));
            Log.i("load model",modelFile);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("Fail","Failed to load model");
        }
    }

    public void saveFile(){
        try {
//            Log.i("@@@@@@@@@@@@@@","@@@@@@@@@@@@@");

            fw.write("videoFile = " + CameraRecorder.videoPath+ System.getProperty( "line.separator" ));
            fw.write("scoreFile = " + CameraRecorder.txtPath+ System.getProperty( "line.separator" ));
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
//
//    public Bitmap imageToBitmap(Image image, float rotationDegrees) {
//        assert (image.getFormat() == ImageFormat.NV21);
//        // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
//        ByteBuffer ib = ByteBuffer.allocate(image.getHeight() * image.getWidth() * 2);
//        ByteBuffer y = image.getPlanes()[0].getBuffer();
//        ByteBuffer cr = image.getPlanes()[1].getBuffer();
//        ByteBuffer cb = image.getPlanes()[2].getBuffer();
//        ib.put(y);
//        ib.put(cb);
//        ib.put(cr);
//        YuvImage yuvImage = new YuvImage(ib.array(),
//                ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        yuvImage.compressToJpeg(new Rect(0, 0,
//                image.getWidth(), image.getHeight()), 100, out);
//        byte[] imageBytes = out.toByteArray();
//        Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//        Bitmap bitmap;
//        // On android the camera rotation and the screen rotation
//        // are off by 90 degrees, so if you are capturing an image
//        // in "portrait" orientation, you'll need to rotate the image.
//        if (rotationDegrees != 0) {
//            Matrix matrix = new Matrix();
//            matrix.postRotate(rotationDegrees);
//            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm,
//                    bm.getWidth(), bm.getHeight(), true);
//            bitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
//                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
//        } else {
//            bitmap = bm;
//        }
//        return bitmap;
//    }
    public void setTimestamp(){
        String out = String.format("last = %d, present = %d", lastCalculatedTimestamp, videoPresentTimestamp);
        Log.i("TIMESTAMP", out);
        lastCalculatedTimestamp = videoPresentTimestamp; //시간을 업데이트 한다.
    }
}
