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

import framework.AutoEdit.AutoEdit;
import framework.Engine.EngineObserver;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.Enum.RecordState;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.ObjectDetection.BoxDrawer;
import framework.ObjectDetection.Classifier;
import framework.ObjectDetection.ObjectDetectionModel;
import framework.SceneDetection.JsonResult;
import framework.SceneDetection.SceneData;
import framework.SceneDetection.SceneDetection;
import framework.Thread.AutoEditThread;
import framework.Thread.ImageConvertThread;
import framework.Thread.ObjectDetectionThread;
import framework.Thread.SceneDetectionThread;
import framework.Thread.ThumbnailThread;
import framework.Util.Constant;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class InferenceManager extends HandlerThread implements ImageReader.OnImageAvailableListener, ImageConvertThread.CallBack, ObjectDetectionThread.Callback, AutoEditThread.Callback {

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
    private int frameIdx=0;
    private int timestamp;
    private int chunkIdx;
    private int orientation;
    private Size previewSize;

    // STATUS
    private boolean isODDone = true;
    private boolean isAEDone = true;
    private boolean isSDDone = true;
    private boolean isTNDone = true;

    private boolean isRecord = false;
    private boolean isReady = false;
    private boolean isLiving = false;

    private boolean isImageProc = false;
    private boolean isRemainBox = false;

    private int lastAEframe = 0;
    private int lastSDframe = -2 * Constant.Inference.SCENE_INTERVAL;
    private int lastTNframe = -2 * Constant.Inference.THUMBNAIL_INTERVAL;

    private boolean fastCount = true;

    private RecordState recordState = RecordState.STOP;
    private Mode mode = Mode.TRAVEL;
    private RecordSpeed recordSpeed = RecordSpeed.NORMAL;


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
                            classifier = ObjectDetectionModel.create(context.getAssets(), context, Constant.Inference.TRAVEL_MODEL_FILE, Constant.Inference.TRAVEL_LABELS_FILE, Constant.Inference.OD_INPUT_SIZE, Constant.Inference.TRAVEL_IS_QUANTIZED);
                            objectDetectionThread = new ObjectDetectionThread(classifier, Constant.Inference.OD_INPUT_SIZE);
                        }
                        if (dailyClassifier == null) {
                            dailyClassifier = ObjectDetectionModel.create(context.getAssets(), context, Constant.Inference.DAILY_MODEL_FILE, Constant.Inference.DAILY_LABELS_FILE, Constant.Inference.OD_INPUT_SIZE, Constant.Inference.DAILY_IS_QUANTIZED);
                        }

                        setUpOD((MessageObject.Box) msg.obj);

                        AutoEdit.loadTFLite(context);
                        SceneDetection.loadTFLite(context);
                        SceneDetection.readLabels(context);

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETMODE : {
                        setMode((Mode) msg.obj);

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETRECORD : {
                        recordState = (RecordState) msg.obj;
                        if (recordState == RecordState.START || recordState == RecordState.RESUME) {
                            isRecord = true;
                        } else {
                            isRecord = false;
                        }

                        if (recordState == RecordState.STOP) {
                            frameIdx = 0;
                            lastAEframe = 0;
                            lastSDframe = -2 * Constant.Inference.SCENE_INTERVAL;
                            lastTNframe = -2 * Constant.Inference.THUMBNAIL_INTERVAL;
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
                            lastTNframe = -2 * Constant.Inference.THUMBNAIL_INTERVAL;
                        }

                        return true;
                    }
                    case ThreadMessage.InferenceMessage.MSG_INFERENCE_SETSPEED : {
                        recordSpeed = ((RecordSpeed) msg.obj);
                        fastCount = true;
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
                    if(isRecord == true || isLiving == true) {
                        if (recordSpeed == RecordSpeed.NORMAL) {
                            frameIdx++;
                        } else if (recordSpeed == RecordSpeed.SLOW) {
                            frameIdx = frameIdx + 2;
                        } else if (recordSpeed == RecordSpeed.FAST) {
                            if (fastCount) {
                                frameIdx = frameIdx + 1;
                                fastCount = false;
                            } else {
                                fastCount = true;
                            }
                        }
                    }
                    if (isRemainBox == true && mode != Mode.TRAVEL && mode != Mode.DAILY) {
                        boxDrawer.clearBoxDrawer();
                        inferenceOverlayView.postInvalidate();
                        isRemainBox = false;
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
                    if (objectDetectionThread.getClassifier() != classifier) {
                        objectDetectionThread.setClassifier(classifier);
                    }
                    objectDetectionThread.setData(rgb);

                    Thread thread = new Thread(objectDetectionThread);
                    thread.start();

                    isODDone = false;
                } else if (mode == Mode.DAILY) {
                    if (objectDetectionThread.getClassifier() != dailyClassifier) {
                        objectDetectionThread.setClassifier(dailyClassifier);
                    }
                    objectDetectionThread.setData(rgb);

                    Thread thread = new Thread(objectDetectionThread);
                    thread.start();

                    isODDone = false;
                }
                isRemainBox = true;
            }

            if ((isRecord == true) && (mode != Mode.LIVE)) {
                // SceneDetection
                if (isSDDone == true) {
                    if ((sceneDetectionThread == null) && (jsonResult == null)) {
                        sceneDetectionThread = new SceneDetectionThread();
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
                        timestamp = 0;
                        chunkIdx = 0;
                    }

                    if (frameIdx - lastSDframe >= Constant.Inference.SCENE_INTERVAL) {
                        lastSDframe = frameIdx;
                        timestamp = (frameIdx * Constant.Inference.CONVERT_MILLISECONDS) / Constant.Inference.FPS;

                        sceneDetectionThread.setData(frameIdx, timestamp, chunkIdx, rgb);

                        Thread thread = new Thread(sceneDetectionThread);
                        thread.start();

                        isSDDone = false;
                    } else {
                        isImageProc = false;
                    }
                }
                // AutoEdit
                if (isAEDone == true) {
                    long curTS = System.nanoTime() / Constant.Inference.CONVERT_MILLISECONDS;

                    if (autoEditThread == null) {
                        autoEditThread = new AutoEditThread();

                        autoEditThread.setFirstTS(curTS);
                        autoEditThread.setPreviewSize(previewSize);
                        autoEditThread.setCallback(this);
                    }

                    if (frameIdx - lastAEframe >= Constant.Inference.EVENT_INTERVAL) {
                        lastAEframe = frameIdx;
                        autoEditThread.setData(rgb);

                        Thread thread = new Thread(autoEditThread);
                        thread.start();

                        isAEDone = false;
                    } else {
                        isImageProc = false;
                    }
                }
            }

            // Thumbnail
            if ((isLiving == true) && (mode == Mode.LIVE) && (isTNDone == true)) {
                if (frameIdx - lastTNframe >= Constant.Inference.THUMBNAIL_INTERVAL) {
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
                        lastTNframe = frameIdx;

                        Thread thread = new Thread(thumbnailThread);
                        thread.start();

                        isTNDone = false;
                    }

                } else {
                    isImageProc = false;
                }
            } else {
                isImageProc = false;
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
                    new Size(Constant.Inference.OD_INPUT_SIZE, Constant.Inference.OD_INPUT_SIZE), 0, false);

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
//        inferenceOverlayView.postInvalidate();

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