package framework.Engine;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.TextureView;

import framework.Enum.Exposure;
import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Manager.CameraDeviceManager;
import framework.Message.ThreadMessage;

public class CameraEngine {
    private Context context;

    private CameraDeviceManager cameraDeviceManager;
    private Handler cameraHandler;

    public static class Util {
        public Util() {}

        public static float getFingerSpacing(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);

            return (float) Math.sqrt((x * x) +  (y * y));
        }
    }

    public CameraEngine(Context context) {
        this.context = context;
    }

    public void startEngine() {
        cameraDeviceManager = new CameraDeviceManager(context);
        cameraDeviceManager.start();
    }

    public void stopEngine() {
        cameraDeviceManager.quitSafely();
    }

    public void startPreview(TextureView textureView) {
        cameraHandler  = cameraDeviceManager.getHandler();

        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_SETUP_PREVIEW, 0, textureView));
        }
    }

    public void stopPreview() {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_STOP_PREVIEW, 0, null));
        }
    }

    public void flash(boolean isFlash) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_FLASH, 0, isFlash));
        }
    }

    public void lensFacing(LensFacing lensFacing) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_LENS, 0, lensFacing));
        }
    }

    public void record(boolean isRecord) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_RECORD, 0, isRecord));
        }
    }

    public void zoom(float spacing) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_ZOOM, 0, spacing));
        }
    }

    public void exposure(Exposure exposure) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_EXPOSURE, 0, exposure));
        }
    }

    public void areaFocus(PointF pointF) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_AREA_FOCUS, 0, pointF));
        }
    }

    public void lockFocus(PointF pointF) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_LOCK_FOCUS, 0, pointF));
        }
    }

    public void filter(Filter filter) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_FILTER, 0, filter));
        }
    }
}