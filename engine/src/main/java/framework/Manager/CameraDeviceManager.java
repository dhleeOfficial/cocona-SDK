package framework.Manager;

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
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import framework.Enum.Exposure;

import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.RecordSpeed;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import framework.Thread.FFmpegThread;
import framework.Util.FocusOverlayView;
import framework.Util.InferenceOverlayView;
import framework.Util.Util;

public class CameraDeviceManager extends HandlerThread implements SensorEventListener {
    private final String TAG = "CameraDeviceManager";

    private Context context;
    private InferenceOverlayView overlayView;
    private FocusOverlayView focusView;

    private Handler myHandler;

    private TextureView textureView;
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

    // VIDEO MANAGER
    private ImageReader recordImageReader;
    private VideoManager videoManager;

    // AUDIO MANAGER
    private AudioManager audioManager;

    // ZOOM
    private float spacing = 0f;
    private float zoomLevel = 1f;
    private Rect zoomRect;

    // EXPOSURE
    private double exposureLevel = 0.0;

    // CURRENT STATUS FLAGS
    private boolean isLocked = true;
    private LensFacing lensFacing;
    private RecordSpeed recordSpeed;

    private Semaphore cameraLock = new Semaphore(1);

    // SENSOR
    float motionX = 0;
    float motionY = 0;
    float motionZ = 0;
    private SensorManager sensorManager;
    private Sensor sensor;


    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            initCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopPreview();

            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public void onSensorChanged(SensorEvent event) {
        if (!isLocked) {
            if (Math.abs(event.values[0] - motionX) > 1
                    || Math.abs(event.values[1] - motionY) > 1
                    || Math.abs(event.values[2] - motionZ) > 1) {

                Log.i(TAG, "Refocus");

                try {
                    autoFocus();
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
//            cameraHighSpeedCaptureSession = (CameraConstrainedHighSpeedCaptureSession) cameraCaptureSession;
            //speedRecord(recordSpeed);
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
        //FIXME
        try {
            objectDetectionManager.join();
            videoManager.join();
            audioManager.join();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        backgroundThread.quitSafely();

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
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // CREATE ObjectDetectionManager
        objectDetectionManager = new ObjectDetectionManager(this.context, this.overlayView);
        objectDetectionManager.start();

        // CREATE Video & Audio Manager
        FFmpegThread.getInstance().setContext(context);

        videoManager = new VideoManager();

        FFmpegThread.getInstance().addCallback(videoManager);

        audioManager = new AudioManager();
        FFmpegThread.getInstance().addCallback(audioManager);
        videoManager.setCallback(audioManager);

        audioManager.start();
        videoManager.start();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        startBackgroundThread();

        myHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.EngineMessage.MSG_ENGINE_SETUP_PREVIEW : {
                        setUpPreview((TextureView) msg.obj);

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
                }
                return false;
            }
        });
    }

    public void startBackgroundThread() {
        backgroundThread = new HandlerThread("backgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public final Handler getHandler() {
        return myHandler;
    }

    /*
        CameraDeviceManager private function
    */

    private synchronized void setUpPreview(final TextureView textureView) {
        if (this.textureView != textureView) {
            this.textureView = textureView;
        }

        if(this.textureView.isAvailable()){
            initCamera(this.textureView.getWidth(),this.textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private synchronized void stopPreview() {
        try {
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
        Handler videoHandler = videoManager.getHandler();
        Handler audioHandler = audioManager.getHandler();

        if ((videoHandler != null) && (audioHandler != null)){
            if (isRecord == true) {
                videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, previewSize));
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_START, 0, null));
            } else {
                videoHandler.sendMessage(videoHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_STOP, 0, null));
            }
        }
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
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);
            Rect rect = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            final int x = (int) ((pointF.y / (float) textureView.getHeight()) * (float) rect.width());
            final int y = (int) ((pointF.x / (float) textureView.getWidth()) * (float) rect.height());
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

            if ((cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) ==  true) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meteringRectangle});
            }

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            captureRequestBuilder.setTag("FOCUS");

            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, backgroundHandler);
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
        Handler audioHandler = audioManager.getHandler();

        try {
            cameraCaptureSession.stopRepeating();

            if (recordSpeed == RecordSpeed.SLOW) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(60, 60));
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_SPECIAL, 0, null));
            } else if (recordSpeed == RecordSpeed.NORMAL) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30));
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_NORMAL, 0, null));
            } else if (recordSpeed == RecordSpeed.FAST) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(15, 15));
                audioHandler.sendMessage(audioHandler.obtainMessage(0, ThreadMessage.RecordMessage.MSG_RECORD_SPECIAL, 0, null));
            }
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }

    private void initCamera(int width, int height) {
        if (textureView.isAvailable() == true) {
            try {
                cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

                enableCameraId = getCameraIdByLensFacing(lensFacing);
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(enableCameraId);

                /* Camera Info */
                hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                exposureLevel = 0.0;
                recordSpeed = RecordSpeed.NORMAL;

                StreamConfigurationMap scMap = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                /* Preview Size Setting */

                if ((width <= 0) || (height <= 0)) {
                    previewSize = scMap.getOutputSizes(SurfaceTexture.class)[0];
                } else {
                    previewSize = Util.getOptimalSize(Arrays.asList(scMap.getOutputSizes(SurfaceTexture.class)));
                }

                //Range<Integer>[] fpsRanges = scMap.getHighSpeedVideoFpsRanges();
                Range<Integer>[] fpsRanges = scMap.getHighSpeedVideoFpsRangesFor(previewSize);
                int max = 0;
                int min;

                for (final Range<Integer> fps : fpsRanges) {
                    if (max < fps.getUpper()) {
                        max = fps.getUpper();
                    }
                }

                min = max;

                for (final Range<Integer> fps : fpsRanges) {
                    if (min > fps.getLower()) {
                        min = fps.getLower();
                    }
                }

                System.out.println("min" + min + "max" + max);

                MessageObject.BoxMessageObject boxMessageObject = new MessageObject.BoxMessageObject(previewSize, 0, lensFacing);
                objectDetectionManager.getHandler().sendMessage(objectDetectionManager.getHandler().obtainMessage(0, ThreadMessage.ODMessage.MSG_OD_SETUP, 0, boxMessageObject));

                if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out");
                }

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
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface sf = new Surface(surfaceTexture);

            captureRequestBuilder.addTarget(sf);

            objectDetectionImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            objectDetectionImageReader.setOnImageAvailableListener(objectDetectionManager, objectDetectionManager.getHandler());

            captureRequestBuilder.addTarget(objectDetectionImageReader.getSurface());

            recordImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            recordImageReader.setOnImageAvailableListener(videoManager, videoManager.getHandler());

            captureRequestBuilder.addTarget(recordImageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(sf, objectDetectionImageReader.getSurface(), recordImageReader.getSurface()), captureStateCallback, backgroundHandler);
        } catch (CameraAccessException ce) {
            ce.printStackTrace();
        }
    }
}