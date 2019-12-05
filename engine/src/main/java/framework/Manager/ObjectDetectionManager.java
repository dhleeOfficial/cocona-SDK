package framework.Manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Size;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.ObjectDetection.BoxDrawer;
import framework.ObjectDetection.Classifier;
import framework.ObjectDetection.ObjectDetectionModel;
import framework.Thread.InferenceThread;
import framework.Util.IntenalOverlayView;
import framework.Util.Util;

public class ObjectDetectionManager extends HandlerThread implements ImageReader.OnImageAvailableListener, InferenceThread.Callback {
    private static final int INPUT_SIZE = 300;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/labelmap.txt";

    private Handler myHandler;
    private Context context;
    private IntenalOverlayView intenalOverlayView;

    private Classifier classifier;
    private Matrix transCropToFrame;
    private BoxDrawer boxDrawer;
    private InferenceThread inferenceThread;

    private Size previewSize;

    // STATUS
    private boolean isDone = true;
    private boolean isReady = false;

    private byte[][] yuvBytes = new byte[3][];

    public ObjectDetectionManager(Context context, IntenalOverlayView intenalOverlayView) {
        super("ObjectDetectionManager");
        this.context = context;
        this.intenalOverlayView = intenalOverlayView;
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        classifier = ObjectDetectionModel.create(context.getAssets(), context, MODEL_FILE, LABELS_FILE, INPUT_SIZE, false);
        inferenceThread = new InferenceThread(classifier, INPUT_SIZE);

        myHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.ODMessage.MSG_OD_SETUP : {
                        setUpOD((MessageObject.BoxMessageObject) msg.obj);
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = null;

        try {
            if (isReady == true) {
                image = reader.acquireNextImage();

                if (image != null) {
                    // TODO : CHECK RECORD
                    if (isDone == true) {
                        Image.Plane[] planes = image.getPlanes();
                        int yRowStride = planes[0].getRowStride();
                        int uvRowStride = planes[1].getRowStride();
                        int uvPixelStride = planes[1].getPixelStride();

                        Util.convertImageToBytes(planes, yuvBytes);

                        inferenceThread.setInfo(yuvBytes, yRowStride, uvRowStride, uvPixelStride);
                        Thread t = new Thread(inferenceThread);
                        t.start();

                        isDone = false;
                    }
                }
            }
        } catch (NullPointerException ne) {
            ne.printStackTrace();
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }


    @Override
    public void onComplete() {
        intenalOverlayView.postInvalidate();
        isDone = true;
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private synchronized void setUpOD(MessageObject.BoxMessageObject boxMessageObject) {
        previewSize = boxMessageObject.getSize();

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float ratio = (float) metrics.heightPixels / (float) metrics.widthPixels;

        // FIXME
        Matrix transFrameToCrop = Util.getTransformationMatrix(new Size(previewSize.getWidth(), (int) (previewSize.getWidth()*ratio)),
                                                               new Size(INPUT_SIZE, INPUT_SIZE), 0, false);

        transCropToFrame = new Matrix();
        transFrameToCrop.invert(transCropToFrame);
        //

        boxDrawer = new BoxDrawer(context, boxMessageObject.getSize(), boxMessageObject.getOrientation());

        inferenceThread.setPreviewSize(previewSize);
        inferenceThread.setLensFacing(boxMessageObject.getLensFacing());
        inferenceThread.setBoxDrawer(boxDrawer);
        inferenceThread.setCallback(this);

        intenalOverlayView.unRegisterAllDrawCallback();
        intenalOverlayView.registerDrawCallback(new IntenalOverlayView.DrawCallback() {
            @Override
            public void onDraw(Canvas canvas) {
                boxDrawer.draw(canvas);
            }
        });

        isReady = true;
    }
}