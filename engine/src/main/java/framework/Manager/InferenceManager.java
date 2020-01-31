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

import java.nio.Buffer;

import framework.Engine.EngineObserver;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.ObjectDetection.BoxDrawer;
import framework.ObjectDetection.Classifier;
import framework.ObjectDetection.ObjectDetectionModel;
import framework.SceneDetection.JsonResult;
import framework.SceneDetection.SceneData;
import framework.Thread.AutoEditThread;
import framework.Thread.ImageConvertThread;
import framework.Thread.ObjectDetectionThread;
import framework.Thread.SceneDetectionThread;
import framework.Thread.ThumbnailThread;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class InferenceManager extends HandlerThread implements ImageReader.OnImageAvailableListener, ImageConvertThread.CallBack, ObjectDetectionThread.Callback, AutoEditThread.Callback {
    private static final int INPUT_SIZE = 300;
    private static final String MODEL_FILE = "travelmode.tflite";
    private static final String LABELS_FILE = "file:///android_asset/travelmode.txt";

    private static final String DAILY_MODEL_FILE = "dailymode.tflite";
    private static final String DAILY_LABELS_FILE = "file:///android_asset/dailymode.txt";

    private Handler myHandler;
    private Context context;
    private InferenceOverlayView inferenceOverlayView;
    private EngineObserver engineObserver;

    private ImageConvertThread imageConvertThread;

    private Classifier classifier;
    private Classifier dailyClassifier;
    private Matrix transCropToFrame;
    private BoxDrawer boxDrawer;
    private ObjectDetectionThread objectDetectionThread;
    private AutoEditThread autoEditThread;

    private SceneDetectionThread sceneDetectionThread;
    private ThumbnailThread thumbnailThread;
    private JsonResult jsonResult;
    private int frameIdx;
    private int timestamp;
    private int chunkIdx;
    private long startTime;
    private int orientation;
    private Size previewSize;

    // STATUS
    private boolean isODDone = true;
    private boolean isAEDone = true;
    private boolean isSDDone = true;
    private boolean isTNDone = true;

    private boolean isReady = false;
    private boolean isRecord = false;
    private boolean isLiving = false;

    private boolean isImageProc = false;

    private RecordSpeed recordSpeed = RecordSpeed.RESUME;
    private Mode mode = Mode.TRAVEL;

    public InferenceManager(Context context, InferenceOverlayView inferenceOverlayView, EngineObserver engineObserver) {
        super("InferenceManager");

        this.context = context;
        this.inferenceOverlayView = inferenceOverlayView;
        this.engineObserver = engineObserver;
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    public boolean quitSafely() {
        return super.quitSafely();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETUP : {
                        if (classifier == null) {
                            classifier = ObjectDetectionModel.create(context.getAssets(), context, MODEL_FILE, LABELS_FILE, INPUT_SIZE, true);
                            objectDetectionThread = new ObjectDetectionThread(classifier, INPUT_SIZE);
                        }
                        if (dailyClassifier == null) {
                            dailyClassifier = ObjectDetectionModel.create(context.getAssets(), context, DAILY_MODEL_FILE, DAILY_LABELS_FILE, INPUT_SIZE, true);
                        }

                        setUpOD((MessageObject.Box) msg.obj);

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETMODE : {
                        setMode((Mode) msg.obj);

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETRECORD : {
                        isRecord = (boolean) msg.obj;

                        if (isRecord == false) {
                            engineObserver.onCompleteLabelFile(jsonResult.createJSONFile());

                            sceneDetectionThread = null;
                            jsonResult = null;

                            if (autoEditThread != null) {
                                engineObserver.onCompleteScoreFile(autoEditThread.getScoreFile());

                                autoEditThread.stop();
                                autoEditThread = null;
                            }
                        }

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETLIVE : {
                        isLiving = ((MessageObject.ThumbnailObject) msg.obj).getIsLive();
                        orientation = ((MessageObject.ThumbnailObject) msg.obj).getOrientation();

                        if (isLiving == true) {
                            frameIdx = 0;
                        }

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETPAUSE : {
                        recordSpeed = ((RecordSpeed) msg.obj);
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
            image = reader.acquireNextImage();
                if (image != null) {

                    if (isImageProc == false) {
                        int yRowStride = image.getPlanes()[0].getRowStride();
                        int uvRowStride = image.getPlanes()[1].getRowStride();
                        int uvPixelStride = image.getPlanes()[1].getPixelStride();
                        byte[][] bytes = Util.convertImageToBytes(image.getPlanes());

                        image.close();

                        if (imageConvertThread == null) {
                            imageConvertThread = new ImageConvertThread(previewSize.getWidth(), previewSize.getHeight(), yRowStride, uvRowStride, uvPixelStride, bytes, this);
                            imageConvertThread.start();
                        }
                    }
                    frameIdx++;
                }
        } catch (NullPointerException ne) {
            ne.printStackTrace();
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    // Override from ObjectDetectionThread.Callback
    @Override
    public void onObjectDetectionDone() {
        inferenceOverlayView.postInvalidate();

        isODDone = true;
        processImageFlag();
    }

    // Override from AutoEditThread.Callback
    @Override
    public void onAutoEditDone() {
        isAEDone = true;
        processImageFlag();
    }

    @Override
    public void imageProcessDone(int[] rgb) {
        isImageProc = true;

        if (rgb != null) {
            // ObjectDetection
            if ((isReady == true) && (isODDone == true)) {
                if (mode == Mode.TRAVEL) {
                    if (objectDetectionThread != classifier) {
                        objectDetectionThread.setClassifier(classifier);
                    }
                    objectDetectionThread.setData(rgb);

                    Thread thread = new Thread(objectDetectionThread);
                    thread.start();

                    isODDone = false;
                } else if (mode == Mode.DAILY) {
                    if (objectDetectionThread != dailyClassifier) {
                        objectDetectionThread.setClassifier(dailyClassifier);
                    }
                    objectDetectionThread.setData(rgb);

                    Thread thread = new Thread(objectDetectionThread);
                    thread.start();

                    isODDone = false;
                }
            }

            if ((isRecord == true) && (recordSpeed == RecordSpeed.RESUME) && (mode != Mode.LIVE)) {
                // SceneDetection
                if (isSDDone == true) {
                    if ((sceneDetectionThread == null) && (jsonResult == null)) {
                        sceneDetectionThread = new SceneDetectionThread();
                        sceneDetectionThread.loadModel(context);
                        sceneDetectionThread.setPreviewSize(previewSize);
                        sceneDetectionThread.setCallback(new SceneDetectionThread.Callback() {
                            @Override
                            public void onSceneDetectionDone(SceneData sceneData) {
                                if (jsonResult != null) {
                                    jsonResult.inputData(sceneData);
                                    chunkIdx++;
                                }

                                isSDDone = true;
                                processImageFlag();
                            }
                        });

                        jsonResult = new JsonResult();
                        frameIdx = 0;
                        timestamp = 0;
                        chunkIdx = 0;
                        startTime = System.nanoTime() / 1000000L;
                    }

                    // TODO : Slow, Fast considering
                    if ((frameIdx % 30) == 0) {
                        long curTime = System.nanoTime() / 1000000L;

                        timestamp = timestamp + (int) (curTime - startTime);
                        startTime = curTime;

                        sceneDetectionThread.setData(frameIdx, timestamp, chunkIdx, rgb);

                        Thread thread = new Thread(sceneDetectionThread);
                        thread.start();

                        isSDDone = false;
                    }
                }
                // AutoEdit
                if (isAEDone == true) {
                    long curTS = System.nanoTime() / 1000L;

                    if (autoEditThread == null) {
                        autoEditThread = new AutoEditThread();

                        autoEditThread.loadTFLite(context);
                        autoEditThread.setFirstTS(curTS);
                        autoEditThread.setPreviewSize(previewSize);
                        autoEditThread.setCallback(this);
                    }
                    autoEditThread.updateCurrentTS(curTS);

                    if (autoEditThread.isNextImage() == true) {
                        autoEditThread.setData(rgb);

                        Thread thread = new Thread(autoEditThread);
                        thread.start();

                        isAEDone = false;
                    }
                }
            }

            // Thumbnail
            if ((isLiving == true) && (mode == Mode.LIVE) && (isTNDone == true)) {
                if ((frameIdx % 300) == 0) {
                    if (thumbnailThread == null) {
                        thumbnailThread = new ThumbnailThread();

                        thumbnailThread.setData(orientation, previewSize, rgb);
                        thumbnailThread.setCallback(new ThumbnailThread.Callback() {
                            @Override
                            public void onThumbnailDone() {
                                isTNDone = true;
                                processImageFlag();

                                thumbnailThread = null;
                            }
                        });

                        Thread thread = new Thread(thumbnailThread);
                        thread.start();

                        isTNDone = false;
                    }
                }
            }
        }

        if (imageConvertThread != null) {
            imageConvertThread = null;
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private synchronized void setUpOD(MessageObject.Box boxMessageObject) {
        if (objectDetectionThread != null) {
            previewSize = boxMessageObject.getSize();

            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float ratio = (float) metrics.heightPixels / (float) metrics.widthPixels;
            Matrix transFrameToCrop = Util.getTransformationMatrix(new Size(previewSize.getWidth(), (int) (previewSize.getWidth() * ratio)),
                    new Size(INPUT_SIZE, INPUT_SIZE), 0, false);

            transCropToFrame = new Matrix();
            transFrameToCrop.invert(transCropToFrame);

            boxDrawer = new BoxDrawer(context, boxMessageObject.getSize(), boxMessageObject.getOrientation());

            objectDetectionThread.setPreviewSize(previewSize);
            objectDetectionThread.setLensFacing(boxMessageObject.getLensFacing());
            objectDetectionThread.setBoxDrawer(boxDrawer);
            objectDetectionThread.setCallback(this);
            objectDetectionThread.setMatrix(transCropToFrame);

            inferenceOverlayView.unRegisterAllDrawCallback();
            inferenceOverlayView.registerDrawCallback(new InferenceOverlayView.DrawCallback() {
                @Override
                public void onDraw(Canvas canvas) {
                    boxDrawer.draw(canvas);
                }
            });

            isReady = true;
        }
    }

    private synchronized void setMode(Mode mode) {
        this.mode = mode;
        inferenceOverlayView.postInvalidate();

        isODDone = true;
        isAEDone = true;
        isSDDone = true;
        isTNDone = true;

        processImageFlag();
    }

    private void processImageFlag() {
        if ((mode == Mode.TRAVEL) || (mode == Mode.DAILY)){
            if ((isODDone == true) || (isAEDone == true) || (isSDDone == true)) {
                isImageProc = false;
            }
        } else if (mode == Mode.EVENT) {
            if ((isAEDone == true) || (isSDDone == true)) {
                isImageProc = false;
            }
        } else if (mode == Mode.LIVE) {
            if (isTNDone == true) {
                isImageProc = false;
            }
        }
    }
}