package com.cubi.smartcameraengine;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;
import android.content.Context;

import android.media.Image;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;
import org.opencv.android.Utils;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;




public class AutoEditing extends AppCompatActivity {


    // Bitmap [] orgBitmap = new Bitmap[5]; // 5 frame
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
    Interpreter tflite;
    String modelFile="video_score.tflite";

    private int w=1080;
    private int h=720;
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
//
//
//
//
//
//    public BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
//        @Override
//        public void onManagerConnected(int status) {
//            switch (status) {
//                case LoaderCallbackInterface.SUCCESS:
//                {
//                    Log.i("OPENCV", "OpenCV loaded successfully");
//                    //after loading OpenCV we can use getBlurScore();
//                    initMat(); // init Mat();
//
////                    double blurscore = getBlurScore();
//
//                } break;
//                default:
//                {
//                    super.onManagerConnected(status);
//                } break;
//            }
//        }
//    };
//    public void onResume()
//    {
//        super.onResume();
//        if (!OpenCVLoader.initDebug()) {
//            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
//            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
//        } else {
//            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
//            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//        }
//    }


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

//    private static void importModelFile()
//    {
//        String modelFile="video_score.tflite";
//        Interpreter tflite;
//
//        //load tflite model
//        try {
//            tflite = new Interpreter(AutoEditing.this);
//            Log.i("DEEP RUNNING", "Loaded ftfile");
//
//            //create input, output array
//            // 입력 데이터 2개 사용. [][]는 2차원 배열을 의미한다.
//            float[][][][] input = new float[5][80][80][3];
//            float[][] output = new float[1][2];
//
//            tflite.run(input, output);
//            String out = String.format("score = %f",  output[0][1]);
//            Log.i("DEEP RUNNING", out);
//
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }


    // This is an real example how to use tflite model for
    // in this example shows how to convert Bitmap image into input array (5 frame, 80 x 80, 3channel)

    private MappedByteBuffer loadModelFile(Context context, String MODEL_FILE) throws IOException {

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

//    private double getBlurScore()
//    {
//        double blurscore = blurscore(originalMat);
//
//        String out = String.format("Get Blur Score = %f", blurscore);
//        Log.i("OpenCV", out);
//
//        //blurscore는 80*80으로 rescale하지 않고 원본 크기 사용
//        return blurscore;
//    }

    private float getEditingScore()
    {
        try {

//            String modelFile="video_score.tflite";
//            Interpreter tflite;
//
//            //load tflite model
//
//            tflite=new Interpreter(loadModelFile(this, modelFile));
//
//            Log.i("DEEP RUNNING", "Loaded ftfile");

            // load images
//            loadImage();
//            Log.i("DEEP RUNNING", "Loaded Images");

            //converting bitmap to 80*80*3 (RGB)
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

    public void appendImages(Image image) {
        if (image == null){
            Log.i("Image","NULL");
        }

        String out = String.format("last = %d, present = %d", lastCalculatedTimestamp, videoPresentTimestamp);

        Bitmap tempBmp = imageToBitmap(image,90);

//        try {
//            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "file"+System.nanoTime()+".jpeg");
//            FileOutputStream outStream = new FileOutputStream(file);
//
//            tempBmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
//
//            outStream.close();
//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        Log.i("TIMESTAMP", out);
        lastCalculatedTimestamp = videoPresentTimestamp; //시간을 업데이트 한다.
        if(tempBmp==null) {
            Log.i("ERROR","error");
        } else {
            originalBitmap[count] = tempBmp;
            resizedBitmap[count] = Bitmap.createScaledBitmap(tempBmp, inputSize, inputSize, true);
        }
        count += 1;


        if (count == 5) {

            scorelist = String.format("%d, %f, %f", seqcount, getBlurScore(), getEditingScore());
            try{
                fw.write(scorelist + System.getProperty( "line.separator" ));
//                fw.close();

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

    public void loadTFLite(Context context){

        //load tflite model
        try {

            tflite = new Interpreter(loadModelFile(context, modelFile));
            Log.i("load model",modelFile);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("Fail","Failed to load model");
        }
    }
//
//    public Bitmap convertJPEGtoBitmap(Image img){
//        ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
//        jpegBuffer.rewind();
//        byte[] jpegData = new byte[jpegBuffer.limit()];
//        jpegBuffer.get(jpegData);
////        BitmapFactory.Options opts = new BitmapFactory.Options();
//////        opts.inSampleSize = SCALE_FACTOR;
//        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
//        return bitmap;
//    }

//
//
//    public void setFilepath(String filepath){
//        filePath = filepath;
//        File f = new File (filepath);
//        file = new File(f.getParent() + '/'+new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + "score.txt");
//
//    }

    public void saveFile(){
        try {
            Log.i("@@@@@@@@@@@@@@","@@@@@@@@@@@@@");

            fw.write("videoFile = " + CameraRecorder.videoPath+ System.getProperty( "line.separator" ));
            fw.write("scoreFile = " + CameraRecorder.txtPath+ System.getProperty( "line.separator" ));
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Bitmap imageToBitmap(Image image, float rotationDegrees) {
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
                image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Bitmap bitmap;
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
        } else {
            bitmap = bm;
        }
        return bitmap;
    }

}
