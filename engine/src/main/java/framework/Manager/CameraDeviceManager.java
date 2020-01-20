package framework.Manager;

import android.app.Activity;
import android.content.Context;

import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import framework.Enum.Exposure;

import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.GLES.EglCore;
import framework.GLES.FullFrameRect;
import framework.GLES.Texture2dProgram;
import framework.GLES.WindowSurface;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import framework.Util.FocusOverlayView;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class CameraDeviceManager extends HandlerThread implements SensorEventListener, SurfaceHolder.Callback, SurfaceTexture.OnFrameAvailableListener {
    private final String TAG = "CameraDeviceManager";

    private Context context;
    private InferenceOverlayView overlayView;
    private FocusOverlayView focusView;

    private Handler engineHandler;
    private EncoderHandler encoderHandler;

    private SurfaceView surfaceView;

    private Size previewSize;
    private String enableCameraId;

    // BACKGROUND THREAD
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // FLASH
    private boolean hasFlash;

    // CAMERA
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;

    // OBJECT DETECTION
    private ImageReader objectDetectionImageReader;
    private ObjectDetectionManager objectDetectionManager;

    // VIDEO RESOLUTION
    private EncoderManager encoderManager;
    private WindowSurface encoderSurface;

    private EncoderManager encoderManager1;
    private WindowSurface encoderSurface1;

    private EncoderManager encoderManager2;
    private WindowSurface encoderSurface2;

    // AUDIO MANAGER
    private AudioManager audioManager;

    // MUX MANAGER
    private MuxManager muxManager;

    // ZOOM
    private float spacing = 0f;
    private float zoomLevel = 1f;
    private Rect zoomRect;

    // EXPOSURE
    private double exposureLevel = 0.0;

    // CURRENT STATUS FLAGS
    private boolean isLocked = true;
    private boolean isRecording = false;
    private boolean isLiving = false;

    private LensFacing lensFacing;
    private RecordSpeed recordSpeed;
    private Mode mode = Mode.TRAVEL;

    private Semaphore cameraLock = new Semaphore(1);

    // SENSOR
    float motionX = 0;
    float motionY = 0;
    float motionZ = 0;
    private SensorManager sensorManager;
    private Sensor acceleroSensor;

    // OPENGL ES
    private EglCore eglCore;
    private WindowSurface displaySurface;
    private SurfaceTexture cameraTexture;  // receives the output from the camera preview
    private FullFrameRect fullFrameBlit;
    private final float[] tmpMatrix = new float[16];
    private int textureId;

    // ORIENTATION
    private int videoHeight = 1980;
    private int videoWidth = 1080;

    private int videoHeight1 = 1280;
    private int videoWidth1 = 720;

    private int videoHeight2 = 854;
    private int videoWidth2 = 480;

    private OrientationEventListener orientationEventListener;

    private int orientation = 0;

    private class EncoderHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE = 1;

        public EncoderHandler() {}

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE : {
                    drawFrame();
                }
            }
        }
    }

    private void drawFrame() {
        if (eglCore == null) {
            return;
        }

        // PREVIEW RENDERING
        displaySurface.makeCurrent();
        cameraTexture.updateTexImage();
        cameraTexture.getTransformMatrix(tmpMatrix);
        GLES20.glViewport(0, 0, surfaceView.getWidth(), surfaceView.getHeight());
        fullFrameBlit.drawFrame(textureId, tmpMatrix);
        displaySurface.swapBuffers();

        if (isRecording == true) {
            encoderSurface.makeCurrent();

            Util.rotateMatrix(orientation, tmpMatrix);
            GLES20.glViewport(0, 0, videoWidth, videoHeight);

            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            encoderManager.reFrame();
            encoderSurface.setPresentationTime(cameraTexture.getTimestamp());
            encoderSurface.swapBuffers();
        } else if (isLiving == true) {
            encoderSurface.makeCurrent();
            Util.rotateMatrix(orientation,tmpMatrix);
            GLES20.glViewport(0, 0, videoWidth, videoHeight);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            encoderManager.reFrame();
            encoderSurface.setPresentationTime(cameraTexture.getTimestamp());
            encoderSurface.swapBuffers();

            encoderSurface1.makeCurrent();
//            Util.rotateMatrix(orientation,tmpMatrix);
            GLES20.glViewport(0, 0, videoWidth1, videoHeight1);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            encoderManager1.reFrame();
            encoderSurface1.setPresentationTime(cameraTexture.getTimestamp());
            encoderSurface1.swapBuffers();

            encoderSurface2.makeCurrent();
//            Util.rotateMatrix(orientation,tmpMatrix);
            GLES20.glViewport(0, 0, videoWidth2, videoHeight2);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            encoderManager2.reFrame();
            encoderSurface2.setPresentationTime(cameraTexture.getTimestamp());
            encoderSurface2.swapBuffers();
        }
    }

    public void onSensorChanged(SensorEvent event) {
        if (!isLocked) {
            if (Math.abs(event.values[0] - motionX) > 1
                    || Math.abs(event.values[1] - motionY) > 1
                    || Math.abs(event.values[2] - motionZ) > 1) {
                try {
                    if (cameraCaptureSession != null) {
                        autoFocus();
                    }
                    isLocked = true;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        motionX = event.values[0];
        motionY = event.values[1];
        motionZ = event.values[2];
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    private CameraCaptureSession.CaptureCallback captureCallback =  new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            if (request.getTag() == "FOCUS") {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                try {
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                } catch (CameraAccessException ce) {
                    ce.printStackTrace();
                }
            }
        }
    };

    private CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = session;
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30));

            try {
                 cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException ce) {
                ce.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    @Override
    public boolean quit() {
        try {
            objectDetectionManager.join();
            audioManager.join();
            encoderManager.join();
            encoderManager1.join();
            encoderManager2.join();

            muxManager.join();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        backgroundThread.quitSafely();

        if (encoderSurface != null) {
            encoderSurface.release();
            encoderSurface = null;
        }

        if (encoderSurface1 != null) {
            encoderSurface1.release();
            encoderSurface1 = null;
        }

        if (encoderSurface2 != null) {
            encoderSurface2.release();
            encoderSurface2 = null;
        }

        return super.quit();
    }

    public CameraDeviceManager(Context context, View relativeLayout) {
        super("CameraDeviceManager");

        this.context = context;
        this.lensFacing = LensFacing.BACK;
        this.overlayView = new InferenceOverlayView(context);
        ((RelativeLayout) relativeLayout).addView(this.overlayView);

        this.focusView = new FocusOverlayView(context);
        ((RelativeLayout) relativeLayout).addView(this.focusView);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        acceleroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, acceleroSensor, SensorManager.SENSOR_DELAY_NORMAL);

        encoderHandler = new EncoderHandler();

        // CREATE ObjectDetectionManager
        objectDetectionManager = new ObjectDetectionManager(this.context, this.overlayView);
        objectDetectionManager.start();

        encoderManager = new EncoderManager("1920", 1920, 1080, 6000000, new EncoderManager.Callback() {
            @Override
            public void initDone() {
                encoderSurface = new WindowSurface(eglCore, encoderManager.getSurface(), true);

                if (mode != Mode.LIVE) {
                    isRecording = true;
                }
            }
        });

        encoderManager1 = new EncoderManager("1280", 1280, 720, 4000000, new EncoderManager.Callback() {
            @Override
            public void initDone() {
                encoderSurface1 = new WindowSurface(eglCore, encoderManager1.getSurface(), true);
            }
        });

        encoderManager2 = new EncoderManager("640", 854, 480, 1000000, new EncoderManager.Callback() {
            @Override
            public void initDone() {
                encoderSurface2 = new WindowSurface(eglCore, encoderManager2.getSurface(), true);

                if (mode == Mode.LIVE) {
                    isLiving = true;
                }
            }
        });

        audioManager = new AudioManager();
        muxManager = new MuxManager(context);

        audioManager.start();

        encoderManager.start();
        encoderManager1.start();
        encoderManager2.start();

        muxManager.start();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        startBackgroundThread();

        engineHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.EngineMessage.MSG_ENGINE_SETUP_PREVIEW : {
                        setUpPreview((SurfaceView) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_STOP_PREVIEW : {
                        stopPreview();

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_FLASH : {
                        flash((boolean) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_LENS : {
                        lensFacing((LensFacing) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_RECORD : {
                        record((boolean) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_ZOOM : {
                        zoom((float) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_EXPOSURE : {
                        exposure((Exposure) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_AREA_FOCUS : {
                        areaFocus((PointF) msg.obj);
                        drawCircle(true, (PointF) msg.obj);
                        isLocked = false;

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_LOCK_FOCUS : {
                        areaFocus((PointF) msg.obj);
                        Log.i(TAG, "Focus Locked");
                        drawCircle(false, (PointF) msg.obj);
                        isLocked = true;

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_FILTER : {
                        filter((Filter) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_SPEED_RECORD : {
                        speedRecord((RecordSpeed) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_MODE : {
                        mode((Mode) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_LIVE : {
                        live((boolean) msg.obj);
                    }
                }
                return false;
            }
        });
    }

    // Override from SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        eglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        displaySurface = new WindowSurface(eglCore, holder.getSurface(), false);
        displaySurface.makeCurrent();

        fullFrameBlit = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        textureId = fullFrameBlit.createTextureObject();
        cameraTexture = new SurfaceTexture(textureId);
        cameraTexture.setOnFrameAvailableListener(this);

        initCamera(surfaceView.getWidth(), surfaceView.getHeight());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
    }

    // Override from SurfaceTexture.onFrameAvailable
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        encoderHandler.sendEmptyMessage(EncoderHandler.MSG_FRAME_AVAILABLE);
    }

    public void startBackgroundThread() {
        backgroundThread = new HandlerThread("backgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public final Handler getEngineHandler() {
        return engineHandler;
    }

    public final Mode getMode() {
        return mode;
    }

    private synchronized void setUpPreview(final SurfaceView surfaceView) {
        if (this.surfaceView != surfaceView) {
            this.surfaceView = surfaceView;
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(this);
        }
    }

    private synchronized void stopPreview() {
        try {
            isRecording = false;
            isLiving = false;
            cameraLock.acquire();

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraTexture != null) {
                cameraTexture.release();
                cameraTexture = null;
            }
            if (displaySurface != null) {
                displaySurface.release();
                displaySurface = null;
            }
            if (encoderSurface != null) {
                encoderSurface.release();
                encoderSurface = null;
            }
            if (encoderSurface1 != null) {
                encoderSurface1.release();
                encoderSurface1 = null;
            }
            if (encoderSurface2 != null) {
                encoderSurface2.release();
                encoderSurface2 = null;
            }
            if (fullFrameBlit != null) {
                fullFrameBlit.release(false);
                fullFrameBlit = null;
            }
            if (eglCore != null) {
                eglCore.release();
                eglCore = null;
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } finally {
            cameraLock.release();
        }
    }

    private void flash(boolean isFlash) {
        if ((cameraDevice != null) && (hasFlash == true)){
            try {
                if (isFlash == true) {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                } else {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                }

                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException ce) {
                ce.printStackTrace();
            }
        }
    }

    private void lensFacing(LensFacing lensFacing) {
        this.lensFacing = lensFacing;

        stopPreview();
        initCamera(previewSize.getWidth(), previewSize.getHeight());
    }

    private void record(boolean isRecord) {
        Handler audioHandler = audioManager.getHandler();
        Handler muxHandler = muxManager.getHandler();
        Handler encoderHandler = encoderManager.getHandler();

        Handler inferenceHandler = objectDetectionManager.getHandler();

        if (isRecord == true) {
            muxManager.resetPipeList();

            MessageObject.VideoObject videoObj = new MessageObject.VideoObject(videoWidth, videoHeight, muxManager.requestPipe(), muxHandler);
            encoderHandler.sendMessage(encoderHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj));

            MessageObject.AudioRecord audioObj = new MessageObject.AudioRecord(muxManager.requestPipe(), muxHandler);
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, audioObj));

            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_START, 0, this.mode));
        } else {
            encoderHandler.sendMessage(encoderHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            isRecording = false;
        }

        inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.ODMessage.MSG_OD_SETRECORD, 0, isRecord));
    }

    private void zoom(float spacing) {
        if ((cameraManager == null) && (enableCameraId == null) && (captureRequestBuilder == null) && (cameraCaptureSession == null)) {
            return;
        }

        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
            float maxZoomLevel = cc.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            float delta = 0.05f;
            Rect rect = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            if (rect == null) {
                throw new NullPointerException("RECT NULL");
            }

            if (spacing != 0) {
                if (spacing > this.spacing) {
                    if ((maxZoomLevel - zoomLevel) <= delta) {
                        delta = maxZoomLevel - zoomLevel;
                    }
                    zoomLevel = zoomLevel + delta;
                } else if (spacing < this.spacing) {
                    if ((zoomLevel - delta)  < 1f) {
                        delta = zoomLevel - 1f;
                    }
                    zoomLevel = zoomLevel - delta;
                }

                float ratio = (float) 1 / zoomLevel;
                int cropWidth = rect.width() - Math.round((float) rect.width() * ratio);
                int cropHeight = rect.height() - Math.round((float) rect.height() * ratio);

                zoomRect = new Rect(cropWidth / 2, cropHeight / 2, (rect.width() - cropWidth / 2), (rect.height() - cropHeight / 2));
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
            }

            this.spacing = spacing;
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        } catch (NullPointerException ne) {
            ne.printStackTrace();
        }
    }

    private void exposure(Exposure exposure) {
        if ((captureRequestBuilder == null) && (cameraCaptureSession == null)) {
            return;
        }

        double delta = 0.1;

        if (exposure == Exposure.BRIGHT) {
            exposureLevel -= delta;
        } else if (exposure == Exposure.DARK) {
            exposureLevel += delta;
        }

        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
            Range<Integer> range = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int minExposure = range.getLower();
            int maxExposure = range.getUpper();

            if ((minExposure != 0) || (maxExposure != 0)) {
                if ((exposureLevel < minExposure) || (exposureLevel > maxExposure)) {
                    return;
                }

                float exposureValue = (exposureLevel >= 0) ?
                        (float) (minExposure * exposureLevel) : (float) (-maxExposure * exposureLevel);

                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) exposureValue);
                cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void drawCircle(boolean removeCircle, PointF pointF){
        if (removeCircle) {
            focusView.setFocus(true, pointF, Color.WHITE);

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    focusView.postInvalidate();
                }
            });
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    focusView.postInvalidate();
                }
            }, 300);

        } else {

            focusView.setFocus(true, pointF, Color.YELLOW);
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    focusView.postInvalidate();
                }
            });

        }
    }

    private void areaFocus(PointF pointF) {
        try {
            if (cameraCaptureSession != null) {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
                Rect rect = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

//                final int x = (int) ((pointF.y / (float) textureView.getHeight()) * (float) rect.width());
//                final int y = (int) ((pointF.x / (float) textureView.getWidth()) * (float) rect.height());
                final int x = (int) ((pointF.y / (float) surfaceView.getHeight()) * (float) rect.width());
                final int y = (int) ((pointF.x / (float) surfaceView.getWidth()) * (float) rect.height());

                final int halfWidth = 200;
                final int halfHeight = 200;

                MeteringRectangle meteringRectangle = new MeteringRectangle(Math.max(x - halfWidth, 0),
                        Math.max(y - halfHeight, 0),
                        halfWidth * 2,
                        halfHeight * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX - 1);
                cameraCaptureSession.stopRepeating();

                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);

                if ((cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) == true) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meteringRectangle});
                }

                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                captureRequestBuilder.setTag("FOCUS");

                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, backgroundHandler);
            }
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void autoFocus() {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void filter(Filter filter) {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_MODE_OFF);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);

            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, filter.getFilter());
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void speedRecord(RecordSpeed recordSpeed) {
//        Handler audioHandler = audioManager.getHandler();
//        Handler videoHandler = videoManager.getHandler();
//
//        try {
//            if (audioHandler != null) {
//                if (recordSpeed == RecordSpeed.SLOW) {
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(60, 60));
//                    videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_SLOW, 0, null));
//                    audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_SLOW, 0, null));
//                } else if (recordSpeed == RecordSpeed.NORMAL) {
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30));
//                    videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_NORMAL, 0, null));
//                    audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_NORMAL, 0, null));
//                } else if (recordSpeed == RecordSpeed.FAST) {
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(15, 15));
//                    videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_FAST, 0, null));
//                    audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_FAST, 0, null));
//                }
//                if (videoHandler != null) {
//                    if (recordSpeed == RecordSpeed.PAUSE) {
//                        videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_PAUSE, 0, null));
//                        audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_PAUSE, 0, null));
//                    } else if (recordSpeed == RecordSpeed.RESUME) {
//                        videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_RESUME, 0, null));
//                        audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_RESUME, 0, null));
//                    }
//                }
//            }
//
//            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
//        } catch (CameraAccessException ce) {
//            ce.printStackTrace();
//        }
    }

    private void mode(Mode mode) {
        this.mode = mode;

        Handler inferenceHandler = objectDetectionManager.getHandler();

        if (inferenceHandler != null) {
            inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.ODMessage.MSG_OD_SETMODE, 0, mode));
        }
    }

    private void live(boolean isStart) {
        Handler audioHandler = audioManager.getHandler();
        Handler muxHandler = muxManager.getHandler();
        Handler encoderHandler = encoderManager.getHandler();
        Handler encoderHandler1 = encoderManager1.getHandler();
        Handler encoderHandler2 = encoderManager2.getHandler();

        if (isStart == true) {
            muxManager.resetPipeList();

            MessageObject.VideoObject videoObj = new MessageObject.VideoObject(videoWidth, videoHeight, muxManager.requestPipe(), muxHandler);
            encoderHandler.sendMessage(encoderHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj));

            MessageObject.VideoObject videoObj1 = new MessageObject.VideoObject(videoWidth1, videoHeight1, muxManager.requestPipe(), muxHandler);
            encoderHandler1.sendMessage(encoderHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj1));

            MessageObject.VideoObject videoObj2 = new MessageObject.VideoObject(videoWidth2, videoHeight2, muxManager.requestPipe(), muxHandler);
            encoderHandler2.sendMessage(encoderHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj2));

            MessageObject.AudioRecord audioObj = new MessageObject.AudioRecord(muxManager.requestPipe(), muxHandler);
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, audioObj));

            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_LIVE_START, 0, this.mode));
        } else {
            encoderHandler.sendMessage(encoderHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            encoderHandler1.sendMessage(encoderHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            encoderHandler2.sendMessage(encoderHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            isLiving = false;
        }
    }

    private void initCamera(int width, int height) {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            enableCameraId = getCameraIdByLensFacing(lensFacing);
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);

            /* Camera Info */
            hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            exposureLevel = 0.0;
            recordSpeed = RecordSpeed.NORMAL;
//            sensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);

            mode(this.mode);

            StreamConfigurationMap scMap = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            /* Preview Size Setting */

            if ((width <= 0) || (height <= 0)) {
                previewSize = scMap.getOutputSizes(SurfaceTexture.class)[0];
            } else {
                previewSize = Util.getOptimalSize(Arrays.asList(scMap.getOutputSizes(SurfaceTexture.class)));
            }

            MessageObject.Box box = new MessageObject.Box(previewSize, 0, lensFacing);
            objectDetectionManager.getHandler().sendMessage(objectDetectionManager.getHandler().obtainMessage(0, ThreadMessage.ODMessage.MSG_OD_SETUP, 0, box));

            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out");
            }
            initOrientationListener();

            cameraManager.openCamera(enableCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.e(TAG, "onOpened()");

                    cameraLock.release();
                    cameraDevice = camera;

                    createPreviewRequest();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.e(TAG, "onDisconnected()");

                    cameraLock.release();
                    cameraDevice.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "onError()");

                    cameraLock.release();
                    cameraDevice.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        } catch (SecurityException se) {
            se.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    private String getCameraIdByLensFacing(LensFacing lensFacing) {
        try {
            if (cameraManager != null) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics cmc = cameraManager.getCameraCharacteristics(id);

                    if (cmc.get(CameraCharacteristics.LENS_FACING) == lensFacing.getValue()) {
                        return id;
                    }
                }
            }
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
        return null;
    }

    private void createPreviewRequest() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            cameraTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            Surface sf = new Surface(cameraTexture);

            captureRequestBuilder.addTarget(sf);

            objectDetectionImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            objectDetectionImageReader.setOnImageAvailableListener(objectDetectionManager, objectDetectionManager.getHandler());

            captureRequestBuilder.addTarget(objectDetectionImageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(sf, objectDetectionImageReader.getSurface()), captureStateCallback, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void initOrientationListener() {
        orientationEventListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int ori) {
                if (!isRecording && !isLiving) {
                    if (ori > 340 || ori < 20) {
                        orientation = 0;
                        videoWidth = 1080;
                        videoHeight = 1920;

                        videoWidth1 = 720;
                        videoHeight1 = 1280;

                        videoWidth2 = 480;
                        videoHeight2 = 854;
                    } else if (ori > 70 && ori < 110) {
                        orientation = 1;
                        videoWidth = 1920;
                        videoHeight = 1080;

                        videoWidth1 = 1280;
                        videoHeight1 = 720;

                        videoWidth2 = 854;
                        videoHeight2 = 480;
                    } else if (ori > 160 && ori < 200) {
                        orientation = 2;
                        videoWidth = 1080;
                        videoHeight = 1920;

                        videoWidth1 = 720;
                        videoHeight1 = 1280;

                        videoWidth2 = 480;
                        videoHeight2 = 854;
                    } else if (ori > 250 && ori < 290) {
                        orientation = 3;
                        videoWidth = 1920;
                        videoHeight = 1080;

                        videoWidth1 = 1280;
                        videoHeight1 = 720;

                        videoWidth2 = 854;
                        videoHeight2 = 480;
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            orientationEventListener.disable();
            Log.e(TAG,"ORIENTATION LISTENER ERROR");
        }
    }
}