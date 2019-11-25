package com.cubi.smartcameraengine;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import com.cubi.smartcameraengine.objectdetection.Classifier;
import com.cubi.smartcameraengine.objectdetection.ImageUtils;
import com.cubi.smartcameraengine.objectdetection.MultiBoxTracker;
import com.cubi.smartcameraengine.objectdetection.OverlayView;
import com.cubi.smartcameraengine.objectdetection.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;


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

    private boolean isFirst = true;
    private boolean startEvent = true;
    private boolean previewStarted = false;
    private boolean isRect = false;

    private ImageReader previewReader;

    private boolean isProcessingFrame = false;


    private long timestamp = 0;
    private OverlayView trackingOverlay;
    private boolean computingDetection = false;
    private Bitmap rgbFrameBitmap;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private MultiBoxTracker tracker;

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private Classifier detector;
    private static final boolean MAINTAIN_ASPECT = false;

    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes =new int[1920 * 1080];
    private int yRowStride;
    private Runnable imageConverter;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private Matrix matrix;




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

        handlerThread = new HandlerThread("background_running");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

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
            previewStarted = true;
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


        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        //찍는 FPS 변경
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

//        previewWidth = width;
//        previewHeight = height;

        try {

            if (cameraManager == null) return;
            for (String cameraId : cameraManager.getCameraIdList()) {
                Log.d("CAMERAID", cameraId);
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
                    newTracker();

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

        previewReader = ImageReader.newInstance(cameraSize.getWidth(), cameraSize.getHeight(), ImageFormat.YUV_420_888, 2);
        HandlerThread thread = new HandlerThread("Inference");
        thread.start();
        Handler inferenceHandler = new Handler(thread.getLooper());

        previewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                final Image image = imageReader.acquireNextImage();

                if (isRect && CameraRecorder.recordingMode != 0) {
                    if (tracker != null) {
                        trackingOverlay.postInvalidate();
                        tracker.clearRect();
                        trackingOverlay.postInvalidate();
                    }
                    isRect = false;
                }

                try {

                    if (CameraRecorder.recordingMode == 0) {


                        if (image == null) {
                            return;
                        }

                        if (isProcessingFrame) {
                            image.close();
                            return;
                        }


                        isProcessingFrame = true;
                        Trace.beginSection("imageAvailable");



                        final Image.Plane[] planes = image.getPlanes();
                        fillBytes(planes, yuvBytes);
                        yRowStride = planes[0].getRowStride();
                        final int uvRowStride = planes[1].getRowStride();
                        final int uvPixelStride = planes[1].getPixelStride();

                        imageConverter =
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageUtils.convertYUV420ToARGB8888(
                                                yuvBytes[0],
                                                yuvBytes[1],
                                                yuvBytes[2],
                                                cameraSize.getWidth(),
                                                cameraSize.getHeight(),
                                                yRowStride,
                                                uvRowStride,
                                                uvPixelStride,
                                                rgbBytes);
                                    }
                                };

                        processImage(image);

                        isProcessingFrame = false;

                    }

                    if (CameraRecorder.recordingMode == 1) {
                        if (image != null) {
                            processAutoEditing(image);
                            image.close();
                        }
                    }
                }
                finally {
                    if(image != null) {
                        image.close();
                    }
                }
            }
        }, inferenceHandler);
        requestBuilder.addTarget(previewReader.getSurface());


        try {
//            cameraDevice.createCaptureSession(Arrays.asList(surface/*, previewReader.getSurface()*/), cameraCaptureSessionCallback, null);
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
        previewStarted = false;
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
        tracker = null;
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
        if(previewStarted == true){
            if(lensFacing==LensFacing.FRONT){
                lensFacing=LensFacing.BACK;
            } else{
                lensFacing=LensFacing.FRONT;
            }
            stopPreview();
            startPreview(cameraSize.getWidth(),cameraSize.getHeight());
        }
    }

    interface OnStartPreviewListener {
        void onStart(Size previewSize, boolean flashSupport);
    }

    private void processAutoEditing(Image image){

        long curTime = System.nanoTime() / 1000L;

        if (CameraRecorder.started) {

            if (startEvent) {
                matrix = new Matrix();
                matrix.postRotate(90);
                autoEditing = new AutoEditing();
                autoEditing.setStartVideoTimeStamp(curTime);
                startEvent = false;
            }

            autoEditing.setPresentVideoTimeStamp(curTime);

            if (autoEditing.verifyNeedToSaveImageTime()) {

                final Image.Plane[] planes = image.getPlanes();
                fillBytes(planes, yuvBytes);
                yRowStride = planes[0].getRowStride();
                final int uvRowStride = planes[1].getRowStride();
                final int uvPixelStride = planes[1].getPixelStride();

                ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0],
                        yuvBytes[1],
                        yuvBytes[2],
                        cameraSize.getWidth(),
                        cameraSize.getHeight(),
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes);

                autoEditing.setTimestamp();

                runInBackground(
                        new Runnable() {
                            @Override
                            public void run() {

                                rgbFrameBitmap = Bitmap.createBitmap(cameraSize.getWidth(), cameraSize.getHeight(), Bitmap.Config.ARGB_8888);
                                rgbFrameBitmap.setPixels(rgbBytes, 0, cameraSize.getWidth(), 0, 0, cameraSize.getWidth(), cameraSize.getHeight());
                                croppedBitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0, rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight(), matrix, true);
                                autoEditing.appendImages(croppedBitmap);
                            }

                        });
            }
        } else if (!startEvent) {
            autoEditing.saveFile();
            startEvent = true;
        }

    }

    protected void processImage(Image image) {

        if (isFirst) {
            initObjectDetection ();
            isFirst = false;
        }



        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        if (computingDetection) {
            image.close();
            isProcessingFrame = false;
            return;
        }
        computingDetection = true;

        rgbFrameBitmap = Bitmap.createBitmap(cameraSize.getWidth(), cameraSize.getHeight(), Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, cameraSize.getWidth(), 0, 0, cameraSize.getWidth(), cameraSize.getHeight());
        image.close();
        isProcessingFrame = false;


        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        int count = 0;
                        long starttime = SystemClock.uptimeMillis();

//                            Matrix matrix = new Matrix();
//                            matrix.postRotate(90);
//
//                            rotatedBitmap = rgbFrameBitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0,
//                                    rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight(), matrix, true);

                        croppedBitmap = Bitmap.createScaledBitmap(rgbFrameBitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, true);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();
                        for (final Classifier.Recognition result : results) {
                            RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                count++;
                                location = rectRotate90(location);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        if (tracker != null) {
                            if (count == 0 || CameraRecorder.recordingMode != 0) {
//                                Log.i("CLEAR", "RECTS");
                                tracker.clearRect();
                                isRect = false;
                            } else {
                                isRect = true;
                                tracker.trackResults(mappedRecognitions, currTimestamp);
                            }
                        }

                        long detecttime = SystemClock.uptimeMillis() - starttime;
                        Log.i("DETECTTIME", String.format("%d ms",detecttime));
                        trackingOverlay.postInvalidate();
                        computingDetection = false;
                    }
                });
        }



    protected synchronized void runInBackground(final Runnable r) {
        if (backgroundHandler != null) {
            backgroundHandler.post(r);
        }
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected RectF rectRotate90 (RectF location) {
        RectF rotated;
        if (lensFacing==LensFacing.FRONT){
            rotated = new RectF(TF_OD_API_INPUT_SIZE - location.bottom, TF_OD_API_INPUT_SIZE - location.right, TF_OD_API_INPUT_SIZE - location.top, TF_OD_API_INPUT_SIZE - location.left);

        } else {
            rotated = new RectF(TF_OD_API_INPUT_SIZE - location.bottom, location.left, TF_OD_API_INPUT_SIZE - location.top, location.right);
        }

        return rotated;
    }

    protected void initObjectDetection () {
        newTracker();

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            CameraRecorder.setContext.getAssets(),
                            CameraRecorder.setContext,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
        }

        DisplayMetrics metrics = CameraRecorder.setContext.getResources().getDisplayMetrics();
        float ratio = ((float)metrics.heightPixels / (float)metrics.widthPixels);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        cameraSize.getWidth(), (int)(cameraSize.getWidth()*ratio),
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = CameraRecorder.overlay;
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        if (tracker != null) {
                            tracker.draw(canvas);
                        }
//                            if (isDebug()) {
//                                tracker.drawDebug(canvas);
//                            }
                    }
                });

    }

    protected void newTracker() {
        if (tracker == null){

            tracker = new MultiBoxTracker(CameraRecorder.setContext);
            tracker.setFrameConfiguration(cameraSize.getWidth(), cameraSize.getHeight(), 0);

        }

    }
}

