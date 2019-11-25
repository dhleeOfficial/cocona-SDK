package com.cubi.smartcameraengine;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import com.cubi.smartcameraengine.capture.DelegateInterface;
import com.cubi.smartcameraengine.capture.MediaAudioEncoder;
import com.cubi.smartcameraengine.capture.MediaEncoder;
import com.cubi.smartcameraengine.capture.MediaMuxerCaptureWrapper;
import com.cubi.smartcameraengine.capture.MediaVideoEncoder;
import com.cubi.smartcameraengine.egl.GlPreviewRenderer;
import com.cubi.smartcameraengine.egl.filter.GlFilter;
import com.cubi.smartcameraengine.objectdetection.OverlayView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CameraRecorder {
    private GlPreviewRenderer glPreviewRenderer;
    private final CameraRecordListener cameraRecordListener;
    private static final String TAG = "CameraRecorder";

    public static boolean started = false;
//    public static boolean isEvent = false;
    private CameraHandler cameraHandler = null;
    private GLSurfaceView glSurfaceView;

    private boolean flashSupport = false;

    private MediaMuxerCaptureWrapper muxer;
    private int fileWidth;
    private int fileHeight;

    private final int cameraWidth;
    private final int cameraHeight;
    private LensFacing lensFacing;
    private final boolean flipHorizontal;
    private final boolean flipVertical;
    private final boolean mute;
    private final CameraManager cameraManager;
    public enum Orientation{
        PORTRAIT, LEFT, RIGHT, UPSIDEDOWN;
    };
    public Orientation orientation;
    private int degrees;
    private final boolean recordNoFilter;
    public static int fastSlowMode=0;
    public static Context setContext;
    public static String videoPath;
    public static String txtPath;
    public static int recordingMode = 0;

    public DelegateInterface delegate = null;
    public static OverlayView overlay;



    CameraRecorder(
            CameraRecordListener cameraRecordListener,
            final GLSurfaceView glSurfaceView,
            final int fileWidth,
            final int fileHeight,
            final int cameraWidth,
            final int cameraHeight,
            LensFacing lensFacing,
            final boolean flipHorizontal,
            final boolean flipVertical,
            final boolean mute,
            final CameraManager cameraManager,
            final boolean recordNoFilter,
            final OverlayView overlay,
            final Context setContext
    ) {


        this.cameraRecordListener = cameraRecordListener;

        glSurfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR);
        this.glSurfaceView = glSurfaceView;

        this.fileWidth = fileWidth;
        this.fileHeight = fileHeight;
        this.cameraWidth = cameraWidth;
        this.cameraHeight = cameraHeight;
        this.lensFacing = lensFacing;
        this.flipHorizontal = flipHorizontal;
        this.flipVertical = flipVertical;
        this.mute = mute;
        this.cameraManager = cameraManager;
        this.recordNoFilter = recordNoFilter;
        this.overlay = overlay;
        this.setContext = setContext;
        AutoEditing.loadTFLite(CameraRecorder.setContext);

        // create preview Renderer
        if (null == glPreviewRenderer) {
            glPreviewRenderer = new GlPreviewRenderer(glSurfaceView);
        }

        glPreviewRenderer.setSurfaceCreateListener(new GlPreviewRenderer.SurfaceCreateListener() {
            @Override
            public void onCreated(SurfaceTexture surfaceTexture) {
                startPreview(surfaceTexture);
            }
        });
    }

    public void setFileSize(int fileWidth_, int fileHeight_){
        fileWidth=fileWidth_;
        fileHeight=fileHeight_;
    }


    public void setDelegate(DelegateInterface delegate) {
        this.delegate = delegate;
    }

    private synchronized void startPreview(SurfaceTexture surfaceTexture) {
        if (cameraHandler == null) {
            final CameraThread thread = new CameraThread(cameraRecordListener, new CameraThread.OnStartPreviewListener() {
                @Override
                public void onStart(Size previewSize, boolean flash) {

                    Log.d(TAG, "previewSize : width " + previewSize.getWidth() + " height = " + previewSize.getHeight());
                    if (glPreviewRenderer != null) {
                        glPreviewRenderer.setCameraResolution(new Resolution(previewSize.getWidth(), previewSize.getHeight()));
                    }

                    flashSupport = flash;
                    if (cameraRecordListener != null) {
                        cameraRecordListener.onGetFlashSupport(flashSupport);
                    }

                    final float previewWidth = previewSize.getWidth();
                    final float previewHeight = previewSize.getHeight();

                    glSurfaceView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (glPreviewRenderer != null) {
                                degrees=0;
                                glPreviewRenderer.setAngle(degrees);
                                glPreviewRenderer.onStartPreview(previewWidth, previewHeight, orientation);
                            }
                        }
                    });

                    if (glPreviewRenderer != null) {
                        final SurfaceTexture st = glPreviewRenderer.getPreviewTexture().getSurfaceTexture();
                        st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    }
                }
            }, surfaceTexture, cameraManager, lensFacing);


            thread.start();

            cameraHandler = thread.getHandler();
        }
        cameraHandler.startPreview(cameraWidth, cameraHeight);
    }


    public void setFilter(final GlFilter filter) {
        if (filter == null) return;
        glPreviewRenderer.setGlFilter(filter);
    }

    /**
     * change focus
     */
    public void changeManualFocusPoint(float eventX, float eventY, int viewWidth, int viewHeight) {
        if (cameraHandler != null) {
            cameraHandler.changeManualFocusPoint(eventX, eventY, viewWidth, viewHeight);
        }
    }

    public void changeAutoFocus() {
        if (cameraHandler != null) {
            cameraHandler.changeAutoFocus();
        }
    }

    public void setZoom(){
        if (cameraHandler != null) {
            cameraHandler.setZoom();
        }
    }

    public void setFastSlowMode(int i){
        fastSlowMode=i;
        Log.d("fast slow mode change", String.valueOf(i));
    }

    public void pause(){
        fastSlowMode = 3;
    }


    public void switchFlashMode() {
        if (!flashSupport) return;
        if (cameraHandler != null) {
            cameraHandler.switchFlashMode();
        }
    }

    public void adjustBrightness(double brightness) {
        if (cameraHandler != null) {
            cameraHandler.adjustBrightness(brightness);
        }
    }

    public void setGestureScale(float scale) {
        if (glPreviewRenderer != null) {
            glPreviewRenderer.setGestureScale(scale);
        }
    }

    public void switchLensFacing(){
        if (cameraHandler != null) {
            cameraHandler.switchLensFacing();
        }
    }

    public void setOrientation(Orientation orientation_){
        orientation=orientation_;
    }


    public boolean isFlashSupport() {
        return flashSupport;
    }


    private void destroyPreview() {
        if (glPreviewRenderer != null) {
            glPreviewRenderer.release();
            glPreviewRenderer = null;
        }
        if (cameraHandler != null) {
            // just request stop prviewing
            cameraHandler.stopPreview(false);
        }
    }

    public Orientation getOrientation(){
        return orientation;
    }

    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            Log.v("TAG", "onPrepared:encoder=" + encoder);
            if (encoder instanceof MediaVideoEncoder) {
                if (glPreviewRenderer != null) {
                    glPreviewRenderer.setVideoEncoder((MediaVideoEncoder) encoder);
                }
            }

        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            Log.v("TAG", "onStopped:encoder=" + encoder);
            if (encoder instanceof MediaVideoEncoder) {
                if (glPreviewRenderer != null) {
                    glPreviewRenderer.setVideoEncoder(null);
                }
                Log.d(TAG, "============ encode stopped ==========");
                if(delegate != null) {
                    delegate.onCall(videoPath, txtPath);
                }
                Log.i("delegatelog", "!!!!!!!!!!onCall!!!!!!!!!!");
            }
        }
    };

    /**
     * Start data processing
     */

    public void start(final String filePath) {
        if (started) return;
        if(CameraRecorder.fastSlowMode==3){
            CameraRecorder.fastSlowMode=0;
        }
        videoPath = filePath;
        File f = new File (videoPath);
        txtPath = f.getParent() + '/'+new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + "score.txt";

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    int degrees=0;
                    if(orientation == Orientation.PORTRAIT){
                        degrees=0;
                    } else if(orientation == Orientation.RIGHT){
                        degrees=270;
                    } else if(orientation == Orientation.UPSIDEDOWN){
                        degrees=180;
                    } else if(orientation == Orientation.LEFT){
                        degrees=90;
                    }
                    muxer = new MediaMuxerCaptureWrapper(filePath,degrees);

                    // for video capturing
                    new MediaVideoEncoder(
                            muxer,
                            mediaEncoderListener,
                            fileWidth,
                            fileHeight,
                            flipHorizontal,
                            flipVertical,
                            orientation,
                            glSurfaceView.getMeasuredWidth(),
                            glSurfaceView.getMeasuredHeight(),
                            recordNoFilter,
                            glPreviewRenderer.getFilter()
                    );
                    if (!mute) {
                        // for audio capturing
                        new MediaAudioEncoder(muxer, mediaEncoderListener);
                    }
                    muxer.prepare();
                    muxer.startRecording();

                    if (cameraRecordListener != null) {
                        cameraRecordListener.onRecordStart();
                    }
                } catch (Exception e) {
                    notifyOnError(e);
                }

            }
        });

        started = true;
    }

    /**
     * Stops capturing.
     */
    public void stop() {
        if (!started) return;
        try {

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    // stop recording and release camera
                    try {
                        // stop the recording
                        if (muxer != null) {
                            muxer.stopRecording();
                            muxer = null;
                            //  you should not wait here
                        }
                    } catch (Exception e) {
                        // RuntimeException is thrown when stop() is called immediately after start().
                        // In this case the output file is not properly constructed ans should be deleted.
                        Log.d("TAG", "RuntimeException: stop() is called immediately after start()");
                        //noinspection ResultOfMethodCallIgnored
                        notifyOnError(e);
                    }

                    notifyOnDone();
                }
            });

        } catch (Exception e) {
            notifyOnError(e);
            e.printStackTrace();
        }


        started = false;
    }

    public void release() {
        // destroy everithing
        try {
            // stop the recording
            if (muxer != null) {
                muxer.stopRecording();
                muxer = null;
            }
        } catch (Exception e) {
            // RuntimeException is thrown when stop() is called immediately after start().
            // In this case the output file is not properly constructed ans should be deleted.
            Log.d("TAG", "RuntimeException: stop() is called immediately after start()");
        }

        destroyPreview();
    }


//    public boolean isStarted() {
//        return started;
//    }

    public static void setRecordingMode(int mode) {
        recordingMode = mode;
    }

    private void notifyOnDone() {
        if (cameraRecordListener == null) return;
        cameraRecordListener.onRecordComplete();

    }
    @Deprecated
    public void frameSet(OverlayView overlayview) {
        Log.i("DEPRECATED","This function is deprecated");
    }

    @Deprecated
    public void contextSet(Context context) {
        Log.i("DEPRECATED","This function is deprecated");
    }



    private void notifyOnError(Exception e) {
        if (cameraRecordListener == null) return;
        cameraRecordListener.onError(e);
    }


}

