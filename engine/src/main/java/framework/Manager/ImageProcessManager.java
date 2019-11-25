package framework.Manager;

import android.content.Context;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import framework.Message.ThreadMessage;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageProcessManager extends HandlerThread implements ImageReader.OnImageAvailableListener {
    private Handler myHandler;
    private Context context;

    private BufferedOutputStream bufferedOutputStream;
    private boolean isRecording = false;

    // FFMPEG MANAGER
    private FFmpegManager ffmpegManager;
    private Handler ffmpegHandler;
    private static boolean isExecute = false;

    public ImageProcessManager(Context context) {
        super("ImageProcessManager");
        this.context = context;

        ffmpegManager = new FFmpegManager(context);
        ffmpegManager.start();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(this.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.arg1) {
                    case ThreadMessage.RecordMessage.MSG_RECORD_STATUS : {
                        isRecording = (boolean) msg.obj;
                        processFFmpegManager();

                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireNextImage();

            if ((isRecording == true) && (ffmpegManager.getPipe() != null) && (isExecute == true)) {
                if (bufferedOutputStream == null) {
                    try {
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(ffmpegManager.getPipe()));
                    } catch (FileNotFoundException fe) {
                        fe.printStackTrace();
                    }
                }

                try {
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[byteBuffer.capacity()];

                    byteBuffer.get(bytes);
                    bufferedOutputStream.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (NullPointerException ne) {
            ne.printStackTrace();
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void processFFmpegManager() {
        ffmpegHandler = ffmpegManager.getHandler();

        if (isRecording == true) {
            ffmpegHandler.sendMessage(ffmpegHandler.obtainMessage(0, ThreadMessage.FFmpegMessage.MSG_FFMPEG_START, 0, null));
        } else {
            ffmpegHandler.sendMessage(ffmpegHandler.obtainMessage(0, ThreadMessage.FFmpegMessage.MSG_FFMPEG_STOP, 0, null));

            if (bufferedOutputStream != null) {
                try {
                    bufferedOutputStream.flush();
                    bufferedOutputStream.close();
                } catch (IOException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }
}
