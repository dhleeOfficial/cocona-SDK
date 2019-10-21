package com.cubi.smartcameraengine;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.camera2.CameraManager;
import android.opengl.GLSurfaceView;

import com.cubi.smartcameraengine.egl.filter.GlFilter;
import com.cubi.smartcameraengine.objectdetection.OverlayView;

public class CameraRecorderBuilder {


    private GLSurfaceView glSurfaceView;

    private LensFacing lensFacing = LensFacing.FRONT;
    private Resources resources;
    private Activity activity;
    private CameraRecordListener cameraRecordListener;
    private int fileWidth = 1920;
    private int fileHeight = 1080;
    private boolean flipVertical = false;
    private boolean flipHorizontal = false;
    private boolean mute = false;
    private boolean recordNoFilter = false;
    private int cameraWidth = 1920;
    private int cameraHeight = 1080;
    private GlFilter glFilter;
    private OverlayView overlay;

    public CameraRecorderBuilder(Activity activity, GLSurfaceView glSurfaceView) {
        this.activity = activity;
        this.glSurfaceView = glSurfaceView;
        this.resources = activity.getResources();
    }

    public CameraRecorderBuilder cameraRecordListener(CameraRecordListener cameraRecordListener) {
        this.cameraRecordListener = cameraRecordListener;
        return this;
    }

    public CameraRecorderBuilder filter(GlFilter glFilter) {
        this.glFilter = glFilter;
        return this;
    }

    public CameraRecorderBuilder videoSize(int fileWidth, int fileHeight) {
        this.fileWidth = fileWidth;
        this.fileHeight = fileHeight;
        return this;
    }

    public CameraRecorderBuilder cameraSize(int cameraWidth, int cameraHeight) {
        this.cameraWidth = cameraWidth;
        this.cameraHeight = cameraHeight;
        return this;
    }

    public CameraRecorderBuilder lensFacing(LensFacing lensFacing) {
        this.lensFacing = lensFacing;
        return this;
    }

    public CameraRecorderBuilder frameSet(OverlayView view) {
        this.overlay = view;
        return this;
    }

    public CameraRecorderBuilder flipHorizontal(boolean flip) {
        this.flipHorizontal = flip;
        return this;
    }

    public CameraRecorderBuilder flipVertical(boolean flip) {
        this.flipVertical = flip;
        return this;
    }

    public CameraRecorderBuilder mute(boolean mute) {
        this.mute = mute;
        return this;
    }

    public CameraRecorderBuilder recordNoFilter(boolean recordNoFilter) {
        this.recordNoFilter = recordNoFilter;
        return this;
    }

    public CameraRecorder build() {
        if (this.glSurfaceView == null) {
            throw new IllegalArgumentException("glSurfaceView and windowManager, multiVideoEffects is NonNull !!");
        }

        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        CameraRecorder cameraRecorder = new CameraRecorder(
                cameraRecordListener,
                glSurfaceView,
                fileWidth,
                fileHeight,
                cameraWidth,
                cameraHeight,
                lensFacing,
                flipHorizontal,
                flipVertical,
                mute,
                cameraManager,
                recordNoFilter,
                overlay,
                activity
        );

        cameraRecorder.setFilter(glFilter);
        activity = null;
        resources = null;
        return cameraRecorder;
    }

}
