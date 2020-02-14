package framework.Manager;

import android.content.Context;

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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import framework.Engine.EngineObserver;
import framework.Enum.DeviceOrientation;

import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.Enum.RecordState;
import framework.GLES.EglCore;
import framework.GLES.FullFrameRect;
import framework.GLES.Texture2dProgram;
import framework.GLES.WindowSurface;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import framework.Util.Constant;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class CameraDeviceManager extends HandlerThread implements SensorEventListener, SurfaceHolder.Callback, SurfaceTexture.OnFrameAvailableListener {
    private final String TAG = "CameraDeviceManager";

    private Context context;
    private EngineObserver engineObserver;
    private InferenceOverlayView overlayView;
    //private FocusOverlayView focusView;

    private Handler engineHandler;
    private FrameHandler frameHandler;

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
    private ImageReader inferenceImageReader;
    private InferenceManager inferenceManager;

    // VIDEO RESOLUTION
    private VideoManager videoManager;
    private WindowSurface videoSurface;

    private VideoManager videoManager1;
    private WindowSurface videoSurface1;

    private VideoManager videoManager2;
    private WindowSurface videoSurface2;

    // AUDIO MANAGER
    private AudioManager audioManager;

    // MUX MANAGER
    private MuxManager muxManager;

    // ZOOM
    private float spacing;
    private float zoomLevel = Constant.Camera.INIT_ZOOM_LEVEL;
    private Rect zoomRect;

    // EXPOSURE
    private double exposureLevel = 0.0;
    private double exposureValue = 0.0;

    // CURRENT STATUS FLAGS
    private boolean isLocked = true;
    private boolean isRecording = false;
    private boolean isLiving = false;
    private boolean isPause = false;
    private boolean lensSwitching = false;

    private LensFacing lensFacing;
    private RecordSpeed recordSpeed;
    private Mode mode = Mode.TRAVEL;

    private Semaphore cameraLock = new Semaphore(1);

    // SENSOR
    float motionX;
    float motionY;
    float motionZ;
    private SensorManager sensorManager;
    private Sensor accelerSensor;

    // OPENGL ES
    private EglCore eglCore;
    private WindowSurface displaySurface;
    private SurfaceTexture cameraTexture;
    private FullFrameRect fullFrameBlit;
    private final float[] tmpMatrix = new float[16];
    private int textureId;

    // ORIENTATION
    private int videoHeight = Constant.Resolution.FHD_HEIGHT;
    private int videoWidth = Constant.Resolution.FHD_WIDTH;

    private int videoHeight1 = Constant.Resolution.HD_HEIGHT;
    private int videoWidth1 = Constant.Resolution.HD_WIDTH;

    private int videoHeight2 = Constant.Resolution.SD_HEIGHT;
    private int videoWidth2 = Constant.Resolution.SD_WIDTH;

    private OrientationEventListener orientationEventListener;

    private DeviceOrientation orientation = DeviceOrientation.INIT;
    private int count = 0;

    private int vm_status = 0;
    private int vm1_status = 0;
    private int vm2_status = 0;
    private int am_status = 0;

    private int[] availableAFMode;

    private class FrameHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE = 1;

        public FrameHandler() {}

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE : {
                    drawFrame();
                }
            }
        }
    }

    private CameraCaptureSession.CaptureCallback captureCallback =  new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            if (request.getTag() == "FOCUS") {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                try {
                    captureRequestBuilder.setTag("FOCUSDONE");
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, null);

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

    public CameraDeviceManager(Context context, View relativeLayout, EngineObserver engineObserver) {
        super("CameraDeviceManager");

        this.context = context;
        this.engineObserver = engineObserver;

        this.lensFacing = LensFacing.BACK;
        this.overlayView = new InferenceOverlayView(context);
        ((RelativeLayout) relativeLayout).addView(this.overlayView);

//        this.focusView = new FocusOverlayView(context);
//        ((RelativeLayout) relativeLayout).addView(this.focusView);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerSensor, SensorManager.SENSOR_DELAY_NORMAL);

        frameHandler = new FrameHandler();

        // CREATE InferenceManager
        inferenceManager = new InferenceManager(this.context, overlayView, this.engineObserver);
        inferenceManager.start();

        videoManager = new VideoManager("FHD", Constant.Resolution.FHD_BITRATE, new VideoManager.Callback() {
            @Override
            public void initDone() {
                videoSurface = new WindowSurface(eglCore, videoManager.getSurface(), true);

                vm_status = 0;
                processCodecStatus();
            }
        });

        videoManager1 = new VideoManager("HD", Constant.Resolution.HD_BITRATE, new VideoManager.Callback() {
            @Override
            public void initDone() {
                videoSurface1 = new WindowSurface(eglCore, videoManager1.getSurface(), true);

                vm1_status = 0;
                processCodecStatus();
            }
        });

        videoManager2 = new VideoManager("SD", Constant.Resolution.SD_BITRATE, new VideoManager.Callback() {
            @Override
            public void initDone() {
                videoSurface2 = new WindowSurface(eglCore, videoManager2.getSurface(), true);

                vm2_status = 0;
                processCodecStatus();
            }
        });

        audioManager = new AudioManager(new AudioManager.CallBack() {
            @Override
            public void initDone() {
                am_status = 0;
                processCodecStatus();
            }
        });
        muxManager = new MuxManager(context, engineObserver);

        audioManager.start();
        videoManager.start();
        videoManager1.start();
        videoManager2.start();
        muxManager.start();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    public boolean quitSafely() {
        isRecording = false;
        isLiving = false;

        audioManager.quitSafely();
        muxManager.quitSafely();
        videoManager.quitSafely();
        videoManager1.quitSafely();
        videoManager2.quitSafely();
        inferenceManager.quitSafely();
        backgroundThread.quitSafely();

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

        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }

        if (videoSurface1 != null) {
            videoSurface1.release();
            videoSurface1 = null;
        }

        if (videoSurface2 != null) {
            videoSurface2.release();
            videoSurface2 = null;
        }

        if (fullFrameBlit != null) {
            fullFrameBlit.release(false);
            fullFrameBlit = null;
        }

        if (eglCore != null) {
            eglCore.release();
            eglCore = null;
        }
        return super.quitSafely();
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
                        record((RecordState) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_ZOOM : {
                        zoom((float) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_EXPOSURE : {
                        exposure((double) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_AREA_FOCUS : {
                        areaFocus((PointF) msg.obj);
                        //drawCircle(true, (PointF) msg.obj);
                        isLocked = false;

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_LOCK_FOCUS : {
                        areaFocus((PointF) msg.obj);
                        //drawCircle(false, (PointF) msg.obj);
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
                        autoFocus();

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_LIVE : {
                        live((MessageObject.LiveObject) msg.obj);

                        return true;
                    }
                    case ThreadMessage.EngineMessage.MSG_ENGINE_CONVERT_FORMAT : {
                        convertArchiveFormat((MessageObject.TransformObject) msg.obj);

                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
    }

    // Override from SurfaceTexture.onFrameAvailable
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        frameHandler.sendEmptyMessage(frameHandler.MSG_FRAME_AVAILABLE);
        if (lensSwitching){
            Handler audioHandler = audioManager.getHandler();
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_RESUME, 0, null));

            lensSwitching = false;
        }
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

    public final float getCurrentZoomLevel() {
        return zoomLevel;
    }

    public final double getCurrentExposureValue() {
        return exposureValue;
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
            isPause = true;
            cameraLock.acquire();

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }

        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } finally {
            cameraLock.release();
        }
    }

    private void sessionClose() {
        try {
            cameraLock.acquire();

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (lensSwitching) {
                Handler audioHandler = audioManager.getHandler();
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_PAUSE, 0, null));
            }
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }

        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } finally {
            cameraLock.release();

        }
    }

    private void drawFrame() {
        if (eglCore == null) {
            return;
        }

        if (isPause == true) {
            return;
        }
        // PREVIEW RENDERING
        if (displaySurface != null) {
            displaySurface.makeCurrent();
            cameraTexture.updateTexImage();
            cameraTexture.getTransformMatrix(tmpMatrix);
            GLES20.glViewport(0, 0, surfaceView.getWidth(), surfaceView.getHeight());
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            displaySurface.swapBuffers();
        }

        if ((isRecording == true) || (isLiving == true)) {
            Util.rotateMatrix(orientation.getValue(), tmpMatrix);

            if (recordSpeed == RecordSpeed.NORMAL) {
                drawSurfaceNormalSpeed();
            } else if (recordSpeed == RecordSpeed.SLOW) {
                drawSurfaceSlowSpeed();
            } else if (recordSpeed == RecordSpeed.FAST) {
                if (count == 0) {
                    drawSurfaceNormalSpeed();
                    count = 1;
                } else {
                    count = 0;
                }
            }
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
        lensSwitching = true;
        this.lensFacing = lensFacing;

        sessionClose();
        initCamera(previewSize.getWidth(), previewSize.getHeight());
    }

    private void record(RecordState recordState) {
        initCodecStatus();

        Handler audioHandler = audioManager.getHandler();
        Handler muxHandler = muxManager.getHandler();
        Handler videoHandler = videoManager.getHandler();

        Handler videoHandler1 = videoManager1.getHandler();
        Handler videoHandler2 = videoManager2.getHandler();

        Handler inferenceHandler = inferenceManager.getHandler();

        if (recordState == RecordState.START) {

            muxManager.resetPipeList();

            MessageObject.VideoObject videoObj = new MessageObject.VideoObject(videoWidth, videoHeight, muxManager.requestPipe(), muxHandler);
            videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj));

            MessageObject.VideoObject videoObj1 = new MessageObject.VideoObject(videoWidth1, videoHeight1, muxManager.requestPipe(), muxHandler);
            videoHandler1.sendMessage(videoHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj1));

            MessageObject.VideoObject videoObj2 = new MessageObject.VideoObject(videoWidth2, videoHeight2, muxManager.requestPipe(), muxHandler);
            videoHandler2.sendMessage(videoHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj2));

            MessageObject.AudioRecord audioObj = new MessageObject.AudioRecord(muxManager.requestPipe(), muxHandler);
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, audioObj));

            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_START, 0, this.mode));

        } else if (recordState == RecordState.STOP) {

            videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            videoHandler1.sendMessage(videoHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            videoHandler2.sendMessage(videoHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            isRecording = false;
            recordSpeed = RecordSpeed.NORMAL;

        } else if (recordState == RecordState.PAUSE) {
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_PAUSE, 0, null));
            isRecording = false;

        } else if (recordState == RecordState.RESUME) {
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_RESUME, 0, null));
            isRecording = true;
        }

        inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.InferenceMessage.MSG_INFERENCE_SETRECORD, 0, recordState));
    }

    private void zoom(float spacing) {
        if ((cameraManager == null) && (enableCameraId == null) && (captureRequestBuilder == null) && (cameraCaptureSession == null)) {
            return;
        }

        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
            float maxZoomLevel = cc.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            float delta = Constant.Camera.ZOOM_DELTA;
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
                    if ((zoomLevel - delta)  < Constant.Camera.INIT_ZOOM_LEVEL) {
                        delta = zoomLevel - Constant.Camera.INIT_ZOOM_LEVEL;
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

    private void exposure(double delta) {
        if ((captureRequestBuilder == null) && (cameraCaptureSession == null)) {
            return;
        }


        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
            Range<Integer> range = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int minExposure = range.getLower();
            int maxExposure = range.getUpper();

            if ((minExposure != 0) || (maxExposure != 0)) {
                if (exposureValue < minExposure) {
                    exposureValue = minExposure;

                    return;
                } else if (exposureValue > maxExposure) {
                    exposureValue = maxExposure;

                    return;
                }

                exposureValue += (delta >= 0) ?
                        (float) (minExposure * delta) : (float) (-maxExposure * delta);

                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) exposureValue);
                cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

//    private void drawCircle(boolean removeCircle, PointF pointF){
//        if (removeCircle) {
//            focusView.setFocus(true, pointF, Color.WHITE);
//
//            new Handler().post(new Runnable() {
//                @Override
//                public void run() {
//                    focusView.postInvalidate();
//                }
//            });
//            new Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    focusView.postInvalidate();
//                }
//            }, 300);
//
//        } else {
//
//            focusView.setFocus(true, pointF, Color.YELLOW);
//            new Handler().post(new Runnable() {
//                @Override
//                public void run() {
//                    focusView.postInvalidate();
//                }
//            });
//
//        }
//    }

    private void areaFocus(PointF pointF) {
        try {
            if (cameraCaptureSession != null) {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
                Rect rect = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                final int x = (int) ((pointF.y / (float) surfaceView.getHeight()) * (float) rect.width());
                final int y = (int) ((pointF.x / (float) surfaceView.getWidth()) * (float) rect.height());

                final int halfWidth = Constant.Camera.AREA_FOCUS_SIZE;
                final int halfHeight = Constant.Camera.AREA_FOCUS_SIZE;

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
            if (Arrays.asList(availableAFMode).contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            } else if (Arrays.asList(availableAFMode).contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            }
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
        this.recordSpeed = recordSpeed;
        Handler audioHandler = audioManager.getHandler();
        Handler inferenceHandler = inferenceManager.getHandler();

        if (audioHandler != null) {
            if (recordSpeed == RecordSpeed.SLOW) {
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_SLOW, 0, null));
            } else if (recordSpeed == RecordSpeed.NORMAL) {
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_NORMAL, 0, null));
            } else if (recordSpeed == RecordSpeed.FAST) {
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_FAST, 0, null));
            }
        }

        inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.InferenceMessage.MSG_INFERENCE_SETSPEED, 0, recordSpeed));
    }

    private void mode(Mode mode) {
        this.mode = mode;

        Handler inferenceHandler = inferenceManager.getHandler();

        if (inferenceHandler != null) {
            inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.InferenceMessage.MSG_INFERENCE_SETMODE, 0, mode));
        }
    }

    private void live(MessageObject.LiveObject liveObject) {
        initCodecStatus();

        boolean isLive = liveObject.getIsLive();
        MessageObject.ThumbnailObject thumbnailObj = new MessageObject.ThumbnailObject(isLive, orientation.getValue());

        Handler audioHandler = audioManager.getHandler();
        Handler muxHandler = muxManager.getHandler();
        Handler videoHandler = videoManager.getHandler();
        Handler videoHandler1 = videoManager1.getHandler();
        Handler videoHandler2 = videoManager2.getHandler();
        Handler inferenceHandler = inferenceManager.getHandler();

        if (isLive == true) {
            muxManager.resetPipeList();

            MessageObject.VideoObject videoObj = new MessageObject.VideoObject(videoWidth, videoHeight, muxManager.requestPipe(), muxHandler);
            videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj));

            MessageObject.VideoObject videoObj1 = new MessageObject.VideoObject(videoWidth1, videoHeight1, muxManager.requestPipe(), muxHandler);
            videoHandler1.sendMessage(videoHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj1));

            MessageObject.VideoObject videoObj2 = new MessageObject.VideoObject(videoWidth2, videoHeight2, muxManager.requestPipe(), muxHandler);
            videoHandler2.sendMessage(videoHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, videoObj2));

            MessageObject.AudioRecord audioObj = new MessageObject.AudioRecord(muxManager.requestPipe(), muxHandler);
            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, audioObj));

            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_LIVE_START, 0, liveObject));
        } else {
            videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            videoHandler1.sendMessage(videoHandler1.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            videoHandler2.sendMessage(videoHandler2.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));

            isLiving = false;
        }
        inferenceHandler.sendMessage(inferenceHandler.obtainMessage(0, ThreadMessage.InferenceMessage.MSG_INFERENCE_SETLIVE, 0, thumbnailObj));
    }

    private void convertArchiveFormat(MessageObject.TransformObject transformObject) {
        Handler muxHandler = muxManager.getHandler();

        if (muxHandler != null) {
            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_CONVERT_FORMAT, 0, transformObject));
        }
    }

    private void initCamera(int width, int height) {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            enableCameraId = getCameraIdByLensFacing(lensFacing);
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);

            hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            engineObserver.onCheckFlashSupport(hasFlash);

            availableAFMode = cc.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

            recordSpeed = RecordSpeed.NORMAL;
            isPause = false;
            mode(this.mode);

            StreamConfigurationMap scMap = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if ((width <= 0) || (height <= 0)) {
                previewSize = scMap.getOutputSizes(SurfaceTexture.class)[0];
            } else {
                previewSize = Util.getOptimalSize(Arrays.asList(scMap.getOutputSizes(SurfaceTexture.class)));
            }

            MessageObject.Box box = new MessageObject.Box(previewSize, 0, lensFacing);
            inferenceManager.getHandler().sendMessage(inferenceManager.getHandler().obtainMessage(0, ThreadMessage.InferenceMessage.MSG_INFERENCE_SETUP, 0, box));

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
                    engineObserver.onError();

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

            inferenceImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            inferenceImageReader.setOnImageAvailableListener(inferenceManager, inferenceManager.getHandler());

            captureRequestBuilder.addTarget(inferenceImageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(sf, inferenceImageReader.getSurface()), captureStateCallback, backgroundHandler);


        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void drawSurfaceNormalSpeed() {
        if (videoSurface != null) {
            videoSurface.makeCurrent();

            GLES20.glViewport(0, 0, videoWidth, videoHeight);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager.reFrame();
            videoSurface.swapBuffers();
        }

        if (videoSurface1 != null) {
            videoSurface1.makeCurrent();

            GLES20.glViewport(0, 0, videoWidth1, videoHeight1);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager1.reFrame();
            videoSurface1.swapBuffers();
        }

        if (videoSurface2 != null) {
            videoSurface2.makeCurrent();

            GLES20.glViewport(0, 0, videoWidth2, videoHeight2);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager2.reFrame();
            videoSurface2.swapBuffers();
        }
    }

    private void drawSurfaceSlowSpeed() {
        if (videoSurface != null) {
            videoSurface.makeCurrent();
            GLES20.glViewport(0, 0, videoWidth, videoHeight);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager.reFrame();
            videoSurface.swapBuffers();

            videoSurface.makeCurrent();
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager.reFrame();
            videoSurface.swapBuffers();
        }

        if (videoSurface1 != null) {
            videoSurface1.makeCurrent();
            GLES20.glViewport(0, 0, videoWidth1, videoHeight1);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager1.reFrame();
            videoSurface1.swapBuffers();

            videoSurface1.makeCurrent();
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager1.reFrame();
            videoSurface1.swapBuffers();
        }

        if (videoSurface2 != null) {
            videoSurface2.makeCurrent();

            GLES20.glViewport(0, 0, videoWidth2, videoHeight2);
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager2.reFrame();
            videoSurface2.swapBuffers();

            videoSurface2.makeCurrent();
            fullFrameBlit.drawFrame(textureId, tmpMatrix);
            videoManager2.reFrame();
            videoSurface2.swapBuffers();
        }
    }

    private void initOrientationListener() {
        orientationEventListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int ori) {
                DeviceOrientation tempOrientation;

                if (!isRecording && !isLiving) {
                    if (ori > 340 || ori < 20) {
                        tempOrientation = DeviceOrientation.PORTRAIT;
                    } else if (ori > 70 && ori < 110) {
                        tempOrientation = DeviceOrientation.LANDSCAPE_RIGHT;
                    } else if (ori > 160 && ori < 200) {
                        tempOrientation = DeviceOrientation.PORTRAIT_UPSIDEDOWN;
                    } else if (ori > 250 && ori < 290) {
                        tempOrientation = DeviceOrientation.LANDSCAPE_LEFT;
                    } else {
                        return;
                    }
                } else {
                    return;
                }

                if (orientation != tempOrientation) {
                    orientation = tempOrientation;
                    engineObserver.onChangeOrientation(orientation);
                }

                if ((orientation.getValue() % 2) == 0) {
                    videoWidth = Constant.Resolution.FHD_WIDTH;
                    videoHeight = Constant.Resolution.FHD_HEIGHT;

                    videoWidth1 = Constant.Resolution.HD_WIDTH;
                    videoHeight1 = Constant.Resolution.HD_HEIGHT;

                    videoWidth2 = Constant.Resolution.SD_WIDTH;
                    videoHeight2 = Constant.Resolution.SD_HEIGHT;
                } else {
                    videoWidth = Constant.Resolution.FHD_HEIGHT;
                    videoHeight = Constant.Resolution.FHD_WIDTH;

                    videoWidth1 = Constant.Resolution.HD_HEIGHT;
                    videoHeight1 = Constant.Resolution.HD_WIDTH;

                    videoWidth2 = Constant.Resolution.SD_HEIGHT;
                    videoHeight2 = Constant.Resolution.SD_WIDTH;
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

    private void initCodecStatus() {
        vm_status = 1;
        vm1_status = 1;
        vm2_status = 1;
        am_status = 1;
    }

    private void processCodecStatus() {
        if ((vm_status | vm1_status | vm2_status | am_status) == 0) {
            if (mode == Mode.LIVE) {
                isLiving = true;
            } else {
                isRecording = true;
            }
            engineObserver.onRecordStart();
        }
    }

}