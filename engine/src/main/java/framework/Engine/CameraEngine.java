package framework.Engine;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import framework.Enum.Exposure;
import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.TouchType;
import framework.Manager.CameraDeviceManager;
import framework.Message.ThreadMessage;

public class CameraEngine {
    private Context context;
    private View inferenceLayout;
    private View focusLayout;

    private CameraDeviceManager cameraDeviceManager;
    private Handler cameraHandler;

    public static class Util {
        public Util() {}

        // Using Double TouchEvent
        public static float getFingerSpacing(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);

            return (float) Math.sqrt((x * x) +  (y * y));
        }

        // Using Single TouchEvent
        public static class SingleTouchEventHandler {
            private TouchType recentTouchType = TouchType.NOTHANDLE;

            private long startTouchTime = 0;
            private long endTouchTime = 0;

            private float dx = 100f;
            private float dy = 100f;
            private int dT = 800;

            private float prevX = 0;
            private float prevY = 0;

            public SingleTouchEventHandler() {}

            public TouchType getTouchTypeFromTouchEvent(MotionEvent event) {
                final int mask = event.getActionMasked();

                if (mask == MotionEvent.ACTION_DOWN) {
                    startTouchTime = event.getEventTime();
                    prevX = event.getX();
                    prevY = event.getY();

                    recentTouchType = TouchType.NOTHANDLE;
                } else if (mask == MotionEvent.ACTION_MOVE) {
                    float curY = event.getY();

                    if (Math.abs(curY - prevY) > dy) {
                        recentTouchType = TouchType.EXPOSURECHANGE;
                    }

                } else if (mask == MotionEvent.ACTION_UP) {
                    if (recentTouchType == TouchType.NOTHANDLE) {
                        endTouchTime = event.getEventTime();

                        if (endTouchTime - startTouchTime > dT) {
                            recentTouchType = TouchType.LOCKFOCUS;
                        } else {
                            recentTouchType = TouchType.AREAFOCUS;
                        }
                    }
                }

                return recentTouchType;
            }
        }
    }

    public CameraEngine(Context context, View inferenceLayout, View focusLayout) {
        this.context = context;
        this.inferenceLayout = inferenceLayout;
        this.focusLayout = focusLayout;
    }

    public void startEngine() {
        cameraDeviceManager = new CameraDeviceManager(context, inferenceLayout, focusLayout);
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
