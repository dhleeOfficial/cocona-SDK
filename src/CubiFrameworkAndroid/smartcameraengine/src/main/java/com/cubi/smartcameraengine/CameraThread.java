package com.cubi.smartcameraengine;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.media.Image;
import android.content.Context;

import com.google.android.exoplayer2.analytics.AnalyticsListener;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by sudamasayuki on 2018/03/13.
 */

public class CameraThread extends Thread {


    private static final String TAG = "CameraThread";
    public static float zoom=0;


    private final Object readyFence = new Object();
    private CameraHandler handler;
    volatile boolean isRunning = false;

    private CameraDevice cameraDevice;
    private CaptureRequest.Builder requestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private Rect sensorArraySize;

    private SurfaceTexture surfaceTexture;

    private final OnStartPreviewListener listener;
    private final CameraRecordListener cameraRecordListener;
    private final CameraManager cameraManager;

    private Size cameraSize;
    private boolean isFlashTorch = false;
    private LensFacing lensFacing;

    private boolean flashSupport = false;
    private String mCameraId;
    private Rect arraySize;

    protected AutoEditing autoEditing = new AutoEditing();

    boolean isFirst = true;




    CameraThread(
            final CameraRecordListener cameraRecordListener,
            final OnStartPreviewListener listener,
            final SurfaceTexture surfaceTexture,
            final CameraManager cameraManager,
            final LensFacing lensFacing
    ) {
        super("Camera thread");
        this.listener = listener;
        this.cameraRecordListener = cameraRecordListener;
        this.surfaceTexture = surfaceTexture;
        this.cameraManager = cameraManager;
        this.lensFacing = lensFacing;

    }

    public CameraHandler getHandler() {
        synchronized (readyFence) {
            try {
                readyFence.wait();
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
        return handler;
    }

    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "cameraDeviceCallback onOpened");
            CameraThread.this.cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "cameraDeviceCallback onDisconnected");
            camera.close();
            CameraThread.this.cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.d(TAG, "cameraDeviceCallback onError");
            camera.close();
            CameraThread.this.cameraDevice = null;
        }
    };

    private CameraCaptureSession.StateCallback cameraCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            cameraCaptureSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            // Toast.makeText(activity, "onConfigureFailed", Toast.LENGTH_LONG).show();
        }
    };


    private void updatePreview() {

        //stabilization
//        requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
//        requestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON);

//        requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(zoom,zoom,2000-zoom,3500-zoom));


        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

//        requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(60, 60));


        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    /**
     * message loop
     * prepare Looper and create Handler for this thread
     */
    @Override
    public void run() {
        Log.d(TAG, "Camera thread start");
        Looper.prepare();
        synchronized (readyFence) {
            handler = new CameraHandler(this);
            isRunning = true;
            readyFence.notify();
        }
        Looper.loop();
        Log.d(TAG, "Camera thread finish");
        if (cameraRecordListener != null) {
            cameraRecordListener.onCameraThreadFinish();
        }
        synchronized (readyFence) {
            handler = null;
            isRunning = false;
        }
    }

    /**
     * start camera preview
     *
     * @param width
     * @param height
     */
    @SuppressLint("MissingPermission")
    final void startPreview(final int width, final int height) {
        Log.v(TAG, "startPreview:");

        try {

            if (cameraManager == null) return;
            for (String cameraId : cameraManager.getCameraIdList()) {
                Log.d("!!!!!!",cameraId);
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                //zoom을 위한 화면 크기 조사
                arraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));


                if (characteristics.get(CameraCharacteristics.LENS_FACING) == null || characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == null) {
                    continue;
                }
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing.getFacing()) {
                    sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                    flashSupport = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    if (width < 0 || height < 0) {
                        cameraSize = map.getOutputSizes(SurfaceTexture.class)[0];
                    } else {
                        cameraSize = getClosestSupportedSize(Arrays.asList(map.getOutputSizes(SurfaceTexture.class)), width, height);
                    }
                    Log.v(TAG, "cameraSize =" + cameraSize);

                    HandlerThread thread = new HandlerThread("OpenCamera");
                    thread.start();
                    Handler backgroundHandler = new Handler(thread.getLooper());

                    cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler);
                    mCameraId = cameraId;
                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void createCaptureSession() {
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        Surface surface = new Surface(surfaceTexture);

        try {
            requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        requestBuilder.addTarget(surface);
        final ImageReader previewReader = ImageReader.newInstance(
                cameraSize.getWidth(), cameraSize.getHeight(), ImageFormat.YUV_420_888, 2);

        previewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = previewReader.acquireNextImage();
                try {
                    if (CameraRecorder.isEvent) {
                        processAutoEditing(image);
                    }
                } finally {
                    image.close();
                }
            }
        }, handler);
        requestBuilder.addTarget(previewReader.getSurface());


        try {
            cameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()), cameraCaptureSessionCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        listener.onStart(cameraSize, flashSupport);

    }

    private static Size getClosestSupportedSize(List<Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
        return Collections.min(supportedSizes, new Comparator<Size>() {

            private int diff(final Size size) {
                return Math.abs(requestedWidth - size.getWidth()) + Math.abs(requestedHeight - size.getHeight());
            }

            @Override
            public int compare(final Size lhs, final Size rhs) {
                return diff(lhs) - diff(rhs);
            }
        });

    }

    /**
     * stop camera preview
     */
    void stopPreview() {
        Log.v(TAG, "stopPreview:");
        isFlashTorch = false;
        if (requestBuilder != null) {
            requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            try {
                cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
                cameraDevice.close();
                Log.v(TAG, "stopPreview: cameraDevice.close()");
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void setExposure(double exposureAdjustment) {


        CameraCharacteristics mCameraCharacteristics = null;
        try {
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Range<Integer> range1 = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);

        int minExposure = range1.getLower();
        int maxExposure = range1.getUpper();

        if (minExposure != 0 || maxExposure != 0) {
            float newCalculatedValue = 0;
            if (exposureAdjustment >= 0) {
                newCalculatedValue = (float) (minExposure * exposureAdjustment);
            } else {
                newCalculatedValue = (float) (maxExposure * -1 * exposureAdjustment);
            }

            if (requestBuilder != null) {
                try { cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null,null);
                    requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) newCalculatedValue);
                    cameraCaptureSession.capture(requestBuilder.build(), null,null);
                } catch (CameraAccessException e) {
                }
            }
        }
    }


    /**
     * change focus
     */
    void changeManualFocusPoint(float eventX, float eventY, int viewWidth, int viewHeight) {

        final int y = (int) ((eventX / (float) viewWidth) * (float) sensorArraySize.height());
        final int x = (int) ((eventY / (float) viewHeight) * (float) sensorArraySize.width());
        final int halfTouchWidth = 400;
        final int halfTouchHeight = 400;
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);

        requestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);


        //then we ask for a single request (not repeating!)
        try {
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
            if (focusAreaTouch.getX()*focusAreaTouch.getY() > 0) {
                lockFocus();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    // フラッシュ切り替え
    void switchFlashMode() {
        if (!flashSupport) return;

        try {
            if (isFlashTorch) {
                isFlashTorch = false;
                requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            } else {
                isFlashTorch = true;
                requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            }

            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void changeAutoFocus() {
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

        //then we ask for a single request (not repeating!)
        try {
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void lockFocus() {
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED);
        requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

        //then we ask for a single request (not repeating!)
        try {
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void setZoom(){
        requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, new Rect((int) zoom,(int) zoom,(int) (arraySize.width()-zoom*arraySize.width()/arraySize.height()),(int) (arraySize.height()-zoom)));
        try {
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void switchLensFacing(){
        if(lensFacing==LensFacing.FRONT){
            lensFacing=LensFacing.BACK;
        } else{
            lensFacing=LensFacing.FRONT;
        }
        stopPreview();
        startPreview(4032,3024);

    }

    interface OnStartPreviewListener {
        void onStart(Size previewSize, boolean flashSupport);
    }


    private void processAutoEditing(Image image){

        long curTime = System.nanoTime() / 1000L;
        if (isFirst){
//            autoEditing.setFilepath(CameraRecorder.filepath);
            autoEditing.loadTFLite(CameraRecorder.setContext);
            autoEditing.setStartVideoTimeStamp(curTime);
            isFirst = false;
        }
        autoEditing.setPresentVideoTimeStamp(curTime);
        if (autoEditing.verifyNeedToSaveImageTime()) {
            autoEditing.appendImages(image);
        }

        if (!CameraRecorder.started) {
            autoEditing.saveFile();
            CameraRecorder.setEvent(false);
            isFirst = true;
            autoEditing = new AutoEditing();
        }

    }

}

