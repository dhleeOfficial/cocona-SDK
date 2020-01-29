package framework.Manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;

import framework.Engine.EngineObserver;
import framework.Enum.Mode;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.ObjectDetection.BoxDrawer;
import framework.ObjectDetection.Classifier;
import framework.ObjectDetection.ObjectDetectionModel;
import framework.SceneDetection.JsonResult;
import framework.SceneDetection.SceneData;
import framework.Thread.AutoEditThread;
import framework.Thread.InferenceThread;
import framework.Thread.SceneDetecThread;
import framework.Thread.ThumbnailThread;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class ObjectDetectionManager extends HandlerThread implements ImageReader.OnImageAvailableListener, InferenceThread.Callback, AutoEditThread.Callback {
    private static final int INPUT_SIZE = 300;
    private static final String MODEL_FILE = "travelmode.tflite";
    private static final String LABELS_FILE = "file:///android_asset/travelmode.txt";

    private static final String DAILY_MODEL_FILE = "dailymode.tflite";
    private static final String DAILY_LABELS_FILE = "file:///android_asset/dailymode.txt";

    private Handler myHandler;
    private Context context;
    private InferenceOverlayView inferenceOverlayView;
    private EngineObserver engineObserver;

    private Classifier classifier;
    private Classifier dailyClassifier;
    private Matrix transCropToFrame;
    private BoxDrawer boxDrawer;
    private InferenceThread inferenceThread;
    private AutoEditThread autoEditThread;

    private SceneDetecThread sceneDetecThread;
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

    private Mode mode = Mode.TRAVEL;

    public ObjectDetectionManager(Context context, InferenceOverlayView inferenceOverlayView, EngineObserver engineObserver) {
        super("ObjectDetectionManager");

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
                    case ThreadMessage.ODMessage.MSG_OD_SETUP : {
                        if (classifier == null) {
                            classifier = ObjectDetectionModel.create(context.getAssets(), context, MODEL_FILE, LABELS_FILE, INPUT_SIZE, true);
                            inferenceThread = new InferenceThread(classifier, INPUT_SIZE);
                        }
                        if (dailyClassifier == null) {
                            dailyClassifier = ObjectDetectionModel.create(context.getAssets(), context, DAILY_MODEL_FILE, DAILY_LABELS_FILE, INPUT_SIZE, true);
                        }

                        setUpOD((MessageObject.Box) msg.obj);

                        return true;
                    }
                    case ThreadMessage.ODMessage.MSG_OD_SETMODE : {
                        setMode((Mode) msg.obj);

                        return true;
                    }
                    case ThreadMessage.ODMessage.MSG_OD_SETRECORD : {
                        isRecord = (boolean) msg.obj;

                        if (isRecord == false) {
                            engineObserver.onCompleteLabelFile(jsonResult.createJSONFile());

                            sceneDetecThread = null;
                            jsonResult = null;

                            if (autoEditThread != null) {

                                autoEditThread.stop();
                                autoEditThread = null;
                            }

                        }

                        return true;
                    }
                    case ThreadMessage.ODMessage.MSG_OD_SETLIVE : {
                        isLiving = ((MessageObject.ThumbnailObject) msg.obj).getIsLive();
                        orientation = ((MessageObject.ThumbnailObject) msg.obj).getOrientation();

                        if (isLiving == true) {
                            frameIdx = 0;
                        }

                        return true;
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

                    int yRowStride = image.getPlanes()[0].getRowStride();
                    int uvRowStride = image.getPlanes()[1].getRowStride();
                    int uvPixelStride = image.getPlanes()[1].getPixelStride();
                    byte[][] bytes = Util.convertImageToBytes(image.getPlanes());

                    if (isLiving == true) {
                        if (mode == Mode.LIVE) {
                            if (isTNDone == true) {
                                if ((frameIdx % 300) == 0) {

                                    if (thumbnailThread == null) {
                                        thumbnailThread = new ThumbnailThread();

                                        thumbnailThread.setInfo(orientation, previewSize, bytes, yRowStride, uvRowStride, uvPixelStride);
                                        thumbnailThread.setCallback(new ThumbnailThread.Callback() {
                                            @Override
                                            public void onDone() {
                                                isTNDone = true;
                                                thumbnailThread = null;
                                            }
                                        });

                                        Thread t = new Thread(thumbnailThread);
                                        t.start();
                                        isTNDone = false;
                                    }
                                }
                            }
                        }
                    }

                    if (isRecord == true) {
                        if (mode != Mode.LIVE) {
                            if (isSDDone == true) {
                                if ((sceneDetecThread == null) && (jsonResult == null)) {
                                    sceneDetecThread = new SceneDetecThread();
                                    sceneDetecThread.loadModel(context);
                                    sceneDetecThread.setPreviewSize(previewSize);
                                    sceneDetecThread.setCallback(new SceneDetecThread.Callback() {
                                        @Override
                                        public void onSceneDetecDone(SceneData sceneData) {
                                            isSDDone = true;

                                            if (jsonResult != null) {
                                                jsonResult.inputData(sceneData);
                                                chunkIdx++;
                                            }
                                        }
                                    });

                                    jsonResult = new JsonResult();
                                    frameIdx = 0;
                                    timestamp = 0;
                                    chunkIdx = 0;
                                    startTime = System.nanoTime() / 1000000L;
                                }

                                // TODO : Slow, Fast considering
                                if ((frameIdx%30) == 0) {

                                    long curTime = System.nanoTime() / 1000000L;
                                    timestamp = timestamp + (int) (curTime - startTime);
                                    startTime = curTime;

                                    sceneDetecThread.setInfo(frameIdx, timestamp, chunkIdx, bytes, yRowStride, uvRowStride, uvPixelStride);

                                    Thread t = new Thread(sceneDetecThread);
                                    t.start();
                                    isSDDone = false;

                                }
                            }
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

                                    autoEditThread.setInfo(bytes, yRowStride, uvRowStride, uvPixelStride);

                                    Thread t = new Thread(autoEditThread);
                                    t.start();

                                    isAEDone = false;
                                }
                            }
                        }
                    }

                    if (mode == Mode.TRAVEL) {
                        if (isReady == true) {
                            if (inferenceThread != classifier) {
                                inferenceThread.setClassifier(classifier);
                            }
                            if (isODDone == true) {

                                inferenceThread.setInfo(bytes, yRowStride, uvRowStride, uvPixelStride);

                                Thread t = new Thread(inferenceThread);
                                t.start();
                                isODDone = false;

                            }
                        }
                    } else if (mode == Mode.DAILY) {
                        if (isReady == true) {
                            if (inferenceThread != dailyClassifier) {
                                inferenceThread.setClassifier(dailyClassifier);
                            }
                            if (isODDone == true) {

                                inferenceThread.setInfo(bytes, yRowStride, uvRowStride, uvPixelStride);

                                Thread t = new Thread(inferenceThread);
                                t.start();
                                isODDone = false;

                            }
                        }
                    }
//                    else if (mode == Mode.EVENT) {
//                        if (isRecord == true) {
//
//                        }
//                    }
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

    // Override from InferenceThread.Callback
    @Override
    public void onComplete() {
        inferenceOverlayView.postInvalidate();

        isODDone = true;
    }

    // Override from AutoEditThread.Callback
    @Override
    public void onDone() {
        isAEDone = true;
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private synchronized void setUpOD(MessageObject.Box boxMessageObject) {
        if (inferenceThread != null) {
            previewSize = boxMessageObject.getSize();

            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float ratio = (float) metrics.heightPixels / (float) metrics.widthPixels;
            Matrix transFrameToCrop = Util.getTransformationMatrix(new Size(previewSize.getWidth(), (int) (previewSize.getWidth() * ratio)),
                    new Size(INPUT_SIZE, INPUT_SIZE), 0, false);

            transCropToFrame = new Matrix();
            transFrameToCrop.invert(transCropToFrame);

            boxDrawer = new BoxDrawer(context, boxMessageObject.getSize(), boxMessageObject.getOrientation());

            inferenceThread.setPreviewSize(previewSize);
            inferenceThread.setLensFacing(boxMessageObject.getLensFacing());
            inferenceThread.setBoxDrawer(boxDrawer);
            inferenceThread.setCallback(this);
            inferenceThread.setMatrix(transCropToFrame);

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
    }
}