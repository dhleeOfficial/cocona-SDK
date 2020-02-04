package framework.Engine;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

import java.io.File;

import framework.Enum.Exposure;
import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.Enum.TouchType;
import framework.Manager.CameraDeviceManager;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;

/**
 * Camera Engine class
 */
public class CameraEngine {
    private Context context;
    private View relativeLayout;
    private EngineObserver engineObserver;

    private CameraDeviceManager cameraDeviceManager;
    private Handler cameraHandler;

    /**
     * Camera Engine Util
     */
    public static class Util {
        public Util() {}

        /**
         * Handle double touch event
         * Calculation function required for 'Zoom'
         * @param event Motion event
         * @return Distance between two touches
         */
        public static float getFingerSpacing(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);

            return (float) Math.sqrt((x * x) +  (y * y));
        }

        /**
         * Handler single touch event
         */
        public static class SingleTouchEventHandler {
            private TouchType recentTouchType = TouchType.NOTHANDLE;

            private long startTouchTime = 0;
            private long endTouchTime = 0;

            private float dx = 100f;
            private float dy = 100f;
            private int dT = 800;

            private float prevX = 0;
            private float prevY = 0;

            /**
             * SingleTouchEventHandler Constructor
             */
            public SingleTouchEventHandler() {}

            /**
             * TouchType for single touch event (ex area focus, lock focus)
             * @param event Motion event
             * @return Enum TouchType value
             */
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

    /**
     * CameraEngine Constructor
     * @param context Activity context
     * @param relativeLayout inflate overlay view for drawing inference box
     * @param engineObserver engineObserver instance
     */
    public CameraEngine(Context context, View relativeLayout, EngineObserver engineObserver) {
        this.context = context;
        this.relativeLayout = relativeLayout;
        this.engineObserver = engineObserver;
    }

    /**
     * CameraEngine start
     * Expected to be called from Activity onCreate function
     */
    public void startEngine() {
        cameraDeviceManager = new CameraDeviceManager(context, relativeLayout, engineObserver);
        cameraDeviceManager.start();
    }

    /**
     * CameraEngine stop
     * Expected to be called from Activity onDestroy function
     */
    public void stopEngine() {
        cameraDeviceManager.quitSafely();
    }

    /**
     * Start camera preview on surface view
     * Expected to be called from Activity onResume function
     * @param surfaceView
     */
    public void startPreview(SurfaceView surfaceView) {
        cameraHandler  = cameraDeviceManager.getEngineHandler();

        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_SETUP_PREVIEW, 0, surfaceView));
        }
    }

    /**
     * Stop camera preview
     * Expected to be called from Activity onPause function
     */
    public void stopPreview() {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_STOP_PREVIEW, 0, null));
        }
    }

    /**
     * Active or Inactive camera flash function
     * @param isFlash Active : true / Inactive : false
     */
    public void flash(boolean isFlash) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_FLASH, 0, isFlash));
        }
    }

    /**
     * Set camera lens facing
     * @param lensFacing back lens facing : LensFacing::BACK / front lens facing : LensFacing::FRONT
     */
    public void lensFacing(LensFacing lensFacing) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_LENS, 0, lensFacing));
        }
    }

    /**
     * Start or stop recording function
     * @param isRecord record start : true / record stop : false
     */
    public void record(boolean isRecord) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_RECORD, 0, isRecord));
        }
    }

    /**
     * Change camera zoom
     * @param spacing If double touch event in screen occurs, call CameraUtil.getFingerSpacing to return value
     */
    public void zoom(float spacing) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_ZOOM, 0, spacing));
        }
    }

    /**
     * Set camera brightness
     * @param exposure brightness : Exposure::BRIGHT / darkness : Exposure::DARK
     */
    public void exposure(Exposure exposure) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_EXPOSURE, 0, exposure));
        }
    }

    /**
     * Activate focus where touched the screen
     * @param pointF
     */
    public void areaFocus(PointF pointF) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_AREA_FOCUS, 0, pointF));
        }
    }

    /**
     * Activate focus where touched the screen and locking
     * @param pointF
     */
    public void lockFocus(PointF pointF) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_LOCK_FOCUS, 0, pointF));
        }
    }

    /**
     * Apply to basic filter supported Android
     * TBD function
     * @param filter
     */
    public void filter(Filter filter) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_FILTER, 0, filter));
        }
    }

    /**
     * Set recording speed
     * @param recordSpeed normal : RecordSpeed::NORMAL / slow motion : RecordSpeed::SLOW / time lapse : RecordSpeed::FAST / pause : RecordSpeed::PAUSE / resume: RecordSpeed::RESUME
     */
    public void recordSpeed(RecordSpeed recordSpeed) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_SPEED_RECORD, 0, recordSpeed));
        }
    }

    /**
     * Set mode
     * @param mode travel : Mode.TRAVEL / daily : Mode.DAILY / event : Mode.EVENT / live : Mode.LIVE
     */
    public void mode(Mode mode) {
        if (cameraHandler != null) {
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_MODE, 0, mode));
        }
    }

    /**
     * Start and Stop Live streaming
     * @param isStart start : true / stop : false
     * @param liveStreamingData LiveStreamingData instance or null (live stop)
     */
    public void live(boolean isStart, LiveStreamingData liveStreamingData) {
        if (cameraHandler != null) {
            MessageObject.LiveObject liveObject = new MessageObject.LiveObject(isStart, liveStreamingData);
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_LIVE, 0, liveObject));
        }
    }

    /**
     * convert VOD file to HLS format
     * @param srcFile VOD file name
     * @param dstPath destination of HLS file
     */
    public void convertArchiveFormatToLiveFormat(String srcFile, String dstPath) {
        if (cameraHandler != null) {
            MessageObject.TransformObject transformObject = new MessageObject.TransformObject(srcFile, dstPath);
            cameraHandler.sendMessage(cameraHandler.obtainMessage(0, ThreadMessage.EngineMessage.MSG_ENGINE_CONVERT_FORMAT, 0, transformObject));
        }
    }

    /**
     * Return current mode enum
     * @return enum Mode current value
     */
    public final Mode getMode() {
        return cameraDeviceManager.getMode();
    }
}
