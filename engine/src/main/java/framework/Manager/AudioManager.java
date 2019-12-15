package framework.Manager;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
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
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import framework.Message.ThreadMessage;
import framework.Thread.FFmpegThread;
import framework.Util.RecordData;

public class AudioManager extends HandlerThread implements FFmpegThread.Callback {
    private Handler myHandler;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int SAMPLE_RATE = 44100;
    private static final int CH_COUNT = 1;
    private static final int BIT_RATE = 128000;

    AudioThread audioThread = null;

    MediaFormat audioFormat;
    MediaCodec audioCodec;

    Queue<RecordData> audioQueue = new LinkedList<RecordData>();
    BufferedOutputStream bufferedOutputStream = null;

    // STATUS
    boolean isStart = false;
    boolean isOpenPipe = false;
    boolean isEOS = false;

    String audioPipe;

    private MediaCodec.Callback audioCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            ByteBuffer in = codec.getInputBuffer(index);

            final RecordData data = audioQueue.poll();

            if ((data != null) && (in != null)) {
                in.clear();
                in.put(data.getBuffer());

                if (data.getIsEOS() == false) {
                    codec.queueInputBuffer(index, 0, data.getBuffer().length, data.getPresentationTimeUS(), 0);
                } else {
                    codec.queueInputBuffer(index, 0, data.getBuffer().length, data.getPresentationTimeUS(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            } else {
                codec.queueInputBuffer(index, 0, 0, 0, 0);
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if (index >= 0) {
                ByteBuffer out = codec.getOutputBuffer(index);

                out.position(info.offset);
                out.limit(info.offset + info.size);

                byte[] outBytes = new byte[info.size + 7];

                addADTSHeader(outBytes, outBytes.length);

                if (out != null) {
                    out.get(outBytes, 7, info.size);
                }

                if (isOpenPipe == true) {
                    if (bufferedOutputStream == null) {
                        try {
                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(audioPipe));
                        } catch (FileNotFoundException fe) {
                            fe.printStackTrace();
                        }
                    }

                    if (bufferedOutputStream != null) {
                        try {
                            if (outBytes.length > 0) {
                                bufferedOutputStream.write(outBytes);
                            }
                        } catch (IOException ie) {
                            ie.printStackTrace();
                        }
                    }
                }

                codec.releaseOutputBuffer(index, false);

                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    stopCodec();
                    isEOS = false;
                }
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

        }
    };

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
                        initMediaFormat();
                        initMediaCodec();

                        isStart = true;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_STOP : {
                        isEOS = true;

                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onReadyPipe(String videoPipe, String audioPipe) {
        this.audioPipe = audioPipe;

        if (audioThread == null) {
            audioThread = new AudioThread();
            audioThread.start();
        }
    }

    @Override
    public void onOpenPipe() {
        isOpenPipe = true;
    }

    @Override
    public void onTerminatePipe() {
        audioThread.interrupt();
        audioThread = null;

        isOpenPipe = false;

        try {
            bufferedOutputStream.close();
            bufferedOutputStream = null;
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void initMediaFormat() {
        audioFormat = new MediaFormat();

        audioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CH_COUNT);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
    }

    private void initMediaCodec() {
        audioCodec = null;

        try {
            audioCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            audioCodec.setCallback(audioCallback);
            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            audioCodec.start();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    private void addADTSHeader(byte[] header, int headerLen) {
        int profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
        int sampleIndex = 4; // 44100

        header[0] = (byte) 0xFF;
        header[1] = (byte) 0xF9;
        header[2] = (byte) (((profile - 1) << 6) + (sampleIndex << 2) +(CH_COUNT >> 2));
        header[3] = (byte) (((CH_COUNT & 0x3) << 6) + (headerLen >> 11));
        header[4] = (byte) ((headerLen & 0x7FF) >> 3);
        header[5] = (byte) (((headerLen & 0x7) << 5) + 0x1F);
        header[6] = (byte) 0xFC;
    }

    private void stopCodec() {
        audioCodec.stop();
        audioCodec.release();
        audioCodec = null;
    }

    private class AudioThread extends Thread {
        private final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private final int[] SAMPLING_RATE = {44100, 11025, 16000, 22050, 8000};

        //private byte[] buf;
        private ByteBuffer byteBuffer;
        private AudioRecord audioRecord;

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            audioRecord = createAudioRecord();

            if (audioRecord != null) {
                if (isStart == true) {
                    audioRecord.startRecording();
                }

                while (isStart) {
                    byteBuffer.clear();

                    final int readByte = audioRecord.read(byteBuffer, byteBuffer.remaining());

                    if (readByte > 0) {
                        audioQueue.add(new RecordData(byteBuffer.array(), 0, isEOS));
                    }

                    if (isEOS == true) {
                        isStart = false;
                    }
                }
                audioRecord.stop();
                audioRecord.release();
                audioCallback = null;
            }
        }

        private AudioRecord createAudioRecord() {
            for (final int rate : SAMPLING_RATE) {
                final int minBufSize = AudioRecord.getMinBufferSize(rate, CHANNEL_CONFIG, AUDIO_FORMAT);

                if (minBufSize == AudioRecord.ERROR_BAD_VALUE) {
                    continue;
                }

                final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, CHANNEL_CONFIG, AUDIO_FORMAT, minBufSize);

                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    byteBuffer = ByteBuffer.allocateDirect(minBufSize);
                    //buf = new byte[minBufSize];

                    return audioRecord;
                } else {
                    audioRecord.release();
                }
            }
            return null;
        }
    }
}
