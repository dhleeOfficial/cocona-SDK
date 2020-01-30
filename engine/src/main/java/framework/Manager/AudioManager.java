package framework.Manager;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import framework.Enum.RecordSpeed;
import framework.Message.MessageObject;
import framework.Message.ThreadMessage;
import framework.Util.MuxData;

public class AudioManager extends HandlerThread {
    private Handler myHandler;
    private Handler muxHandler;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;

    private static final int SAMPLE_RATE = 44100;
    private static final int CH_COUNT = 1;
    private static final int BIT_RATE = 128000;
    private static final int HEADER_SIZE = 7;
    private static final int TIMEOUT_USEC = 10000;

    private AudioThread audioThread = null;

    private MediaFormat audioFormat;
    private MediaCodec audioCodec;
    private MediaCodecInfo mediaCodecInfo;

    private String audioPipe;
    private BufferedOutputStream bufferedOutputStream = null;
    private PipeOpener pipeOpener = null;

    // STATUS
    boolean isReady = false;
    boolean isEOS = false;
    boolean isPause = false;
    RecordSpeed recordSpeed = RecordSpeed.NORMAL;

    // WRITER Thread
    private MuxWriter muxWriter = null;
    private BlockingQueue<MuxData> muxList = new LinkedBlockingDeque<MuxData>();

    private byte[] rawBuffer;
    private byte[] muteBuffer;
    private byte[] muxBuffer;

    public AudioManager() {
        super("AudioManager");
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch(msg.arg1) {
                    case ThreadMessage.RecordMessage.MSG_RECORD_START : {
                        initCodec((MessageObject.AudioRecord) msg.obj);

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_STOP : {
                        isEOS = true;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_SLOW : {
                        recordSpeed = RecordSpeed.SLOW;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_NORMAL : {
                        recordSpeed = RecordSpeed.NORMAL;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_FAST : {
                        recordSpeed = RecordSpeed.FAST;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_PAUSE : {
                        isPause = true;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_RESUME : {
                        isPause = false;

                        return true;
                    }
                }
                return false;
            }
        });
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void initCodec(MessageObject.AudioRecord audioObj) {
        audioPipe = audioObj.getPipe();
        muxHandler = audioObj.getMuxHandler();

        initMediaFormat();
        initMediaCodec();

        if (muxList.isEmpty() == false) {
            muxList.clear();
        }

        isReady = true;
        initAudioRecord();
    }

    private void initMediaFormat() {
        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CH_COUNT);

        audioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
    }

    private MediaCodecInfo getMediaCodecInfo() {
        int codecNum = MediaCodecList.getCodecCount();

        for (int i = 0; i < codecNum; ++i) {
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);

            if (mediaCodecInfo.isEncoder() == false) {
                continue;
            }

            String[] types = mediaCodecInfo.getSupportedTypes();

            for (int j = 0; j < types.length; ++j) {
                if (types[j].equalsIgnoreCase(MIME_TYPE)) {
                    return mediaCodecInfo;
                }
            }
        }

        return null;
    }
    private void initMediaCodec() {
        audioCodec = null;

        mediaCodecInfo = getMediaCodecInfo();

        try {
            audioCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioCodec.start();
        } catch (IOException ie) {
            audioCodec.release();

            ie.printStackTrace();
        }
    }

    private void initAudioRecord() {
        if (audioThread == null) {
            audioThread = new AudioThread();
            audioThread.start();
        }
    }

    private void addADTSHeader(byte[] header, int headerLen) {
        int sampleIndex = 4;

        header[0] = (byte) 0xFF;
        header[1] = (byte) 0xF9;
        header[2] = (byte) ((MediaCodecInfo.CodecProfileLevel.AACObjectLC - 1) << 6);
        header[2] |= (((byte) sampleIndex) << 2);
        header[2] |= (((byte) CH_COUNT) >> 2);
        header[3] = (byte) (((CH_COUNT & 3) << 6) | ((headerLen >> 11) & 0x03));
        header[4] = (byte) ((headerLen >> 3) & 0xFF);
        header[5] = (byte) (((headerLen & 0x07) << 5) | 0x1f);
        header[6] = (byte) 0xFC;
    }

    private void stopCodec() {
        audioCodec.stop();
        audioCodec.release();
        audioCodec = null;

        try {
            muxWriter.join();
            muxWriter = null;
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        try {
            if (bufferedOutputStream != null) {
                bufferedOutputStream.close();
                bufferedOutputStream = null;
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
        pipeOpener = null;
        audioThread.interrupt();
        audioThread = null;
    }

    private class AudioThread extends Thread {
        private final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int SAMPLES_PER_FRAME = 2048;

        private AudioRecord audioRecord;

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            audioRecord = createAudioRecord();

            if (audioRecord != null) {
                audioRecord.startRecording();
            }

            while (isReady) {
                WeakReference<byte[]> weakReference = new WeakReference<byte[]>(new byte[SAMPLES_PER_FRAME]);
                rawBuffer = weakReference.get();
                final int readByte = audioRecord.read(rawBuffer, 0, SAMPLES_PER_FRAME);

                if (readByte > 0) {
                    if (recordSpeed == RecordSpeed.NORMAL) {
                        encode(rawBuffer, 0, isEOS);
                    } else if (recordSpeed == RecordSpeed.SLOW) {
                        WeakReference<byte[]> weakReference1 = new WeakReference<byte[]>(new byte[readByte]);
                        muteBuffer = weakReference1.get();

                        if (isEOS == false) {
                            encode(muteBuffer, 0, isEOS);
                            encode(muteBuffer, 0, isEOS);
                        } else {
                            encode(muteBuffer, 0, false);
                            encode(muteBuffer, 0, isEOS);
                        }
                        muteBuffer = null;
                    } else if (recordSpeed == RecordSpeed.FAST) {
                        WeakReference<byte[]> weakReference1 = new WeakReference<byte[]>(new byte[readByte / 2]);
                        muteBuffer = weakReference1.get();

                        encode(muteBuffer, 0, isEOS);
                        muteBuffer = null;
                    }
                }
                rawBuffer = null;
            }

            audioRecord.stop();
            audioRecord.release();
        }

        private AudioRecord createAudioRecord() {
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, SAMPLES_PER_FRAME * 120);

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                return audioRecord;
            } else {
                audioRecord.release();
            }

            return null;
        }
    }

    private void encode(byte[] buffer, long timeStamp, boolean isEOS) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int inputBufferIndex = audioCodec.dequeueInputBuffer(0);

        if (inputBufferIndex >= 0) {
            if (isEOS == true) {
                audioCodec.queueInputBuffer(inputBufferIndex, 0, 0, timeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                muxing(true, bufferInfo);
            } else {
                final ByteBuffer in = audioCodec.getInputBuffer(inputBufferIndex);

                in.clear();
                in.put(buffer);

                audioCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, timeStamp, 0);;
                muxing(false, bufferInfo);
            }
        }
    }

    private void muxing(boolean isEOS, MediaCodec.BufferInfo bufferInfo) {
        while (true) {
            int outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (isEOS == false) {
                    break;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (pipeOpener == null) {
                    pipeOpener = new PipeOpener();
                    pipeOpener.start();
                }
            } else if (outputBufferIndex >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }

                boolean hasMuxing = false;

                if (bufferInfo.size != 0) {
                    final ByteBuffer out = audioCodec.getOutputBuffer(outputBufferIndex);

                    if (out != null) {
                        out.position(bufferInfo.offset);
                        out.limit(bufferInfo.offset + bufferInfo.size);

                        WeakReference<byte[]> weakReference = new WeakReference<byte[]>(new byte[bufferInfo.size + HEADER_SIZE]);

                        muxBuffer = weakReference.get();

                        addADTSHeader(muxBuffer, muxBuffer.length);
                        out.get(muxBuffer, HEADER_SIZE, bufferInfo.size);

                        if (muxBuffer.length > 0) {
                            MuxData data = new MuxData(muxBuffer, isEOS);
                            muxList.add(data);

                            if (muxWriter == null) {
                                muxWriter = new MuxWriter();
                                muxWriter.start();
                            }
                        }
                        muxBuffer = null;
                    }
                    hasMuxing = true;
                }

                audioCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (hasMuxing == false) {
                        if (isEOS == true) {
                            MuxData data = new MuxData(null, true);
                            muxList.add(data);
                        }
                    }

                    isReady = false;
                    isPause = false;
                    this.isEOS = false;
                    stopCodec();
                    break;
                }
            }
        }
    }

    private class PipeOpener extends Thread {
        @Override
        public void run() {
            if (bufferedOutputStream == null) {
                try {
                    if (audioPipe != null) {
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(audioPipe));
                    }
                } catch (FileNotFoundException fe) {
                    fe.printStackTrace();
                }
            }
        }
    }

    private class MuxWriter extends Thread {
        @Override
        public void run() {
            while(true) {
                try {
                    if (bufferedOutputStream != null) {
                        final MuxData data = muxList.take();

                        if (data != null) {
                            if (isPause == false) {
                                if (data.getBuffer() != null) {
                                    bufferedOutputStream.write(data.getBuffer());
                                    bufferedOutputStream.flush();
                                }
                            }
                        }
                        if (data.getIsEOS() == true) {
                            muxHandler.sendMessage(muxHandler.obtainMessage(0, ThreadMessage.MuxMessage.MSG_MUX_AUDIO_END, 0,null));
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }
}
