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
    String audioPipe;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int SAMPLE_RATE = 44100;
    private static final int CH_COUNT = 1;
    private static final int BIT_RATE = 32000;
    private static final int HEADER_SIZE = 7;
    private static final int TIMEOUT_USEC = 10000;

    AudioThread audioThread = null;

    MediaFormat audioFormat;
    MediaCodec audioCodec;

    Queue<RecordData> audioQueue = new LinkedList<RecordData>();
    BufferedOutputStream bufferedOutputStream = null;

    // STATUS
    boolean isStart = false;
    boolean isOpenPipe = false;
    boolean isEOS = false;
    boolean isNormal = true;


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
                if (isOpenPipe == true) {
                    if (bufferedOutputStream == null) {
                        try {
                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(audioPipe));
                        } catch (FileNotFoundException fe) {
                            fe.printStackTrace();
                        }
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        ByteBuffer out = codec.getOutputBuffer(index);

                        out.position(info.offset);
                        out.limit(info.offset + info.size);

                        byte[] outBytes = new byte[info.size + HEADER_SIZE];

                        addADTSHeader(outBytes, outBytes.length);

                        if (out != null) {
                            out.get(outBytes, HEADER_SIZE, info.size);
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
                }

                codec.releaseOutputBuffer(index, false);

                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    stopCodec();
                    isEOS = false;
                    FFmpegThread.getInstance().requestTerminate();
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
                    case ThreadMessage.RecordMessage.MSG_RECORD_NORMAL : {
                        isNormal = true;

                        return true;
                    }
                    case ThreadMessage.RecordMessage.MSG_RECORD_SPECIAL : {
                        isNormal = false;

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
    }

    @Override
    public void onOpenPipe() {
        isOpenPipe = true;
        if (audioThread == null) {
            audioThread = new AudioThread();
            audioThread.start();
        }
    }

    @Override
    public void onTerminatePipe() {
        audioThread.interrupt();
        audioThread = null;

        isOpenPipe = false;

        try {
            if (bufferedOutputStream != null) {
                bufferedOutputStream.close();
                bufferedOutputStream = null;
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    public final Handler getHandler() {
        return myHandler;
    }

    private void initMediaFormat() {
        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CH_COUNT);

        audioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        //audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 14000000);
    }

    private void initMediaCodec() {
        audioCodec = null;

        try {
            audioCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            //audioCodec.setCallback(audioCallback);
            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            audioCodec.start();
        } catch (IOException ie) {
            audioCodec.release();

            ie.printStackTrace();
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
    }

    private class AudioThread extends Thread {
        private final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int SAMPLES_PER_FRAME = 4096;

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
                    byte[] audioData = new byte[SAMPLES_PER_FRAME];

                    final int readByte = audioRecord.read(audioData, 0, SAMPLES_PER_FRAME);

                    if ((readByte > 0) && (isOpenPipe == true)) {
                        encode(audioData, 0, isEOS);
                        //audioQueue.add(new RecordData(audioData, 0, isEOS));
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
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, SAMPLES_PER_FRAME * 25);

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                return audioRecord;
            } else {
                audioRecord.release();
            }

            return null;
        }
    }

    // sync codec
    private void encode(byte[] buffer, long timeStamp, boolean isEOS) {
        for (; ;) {
            int inputBufferIndex = audioCodec.dequeueInputBuffer(-1);

//            if (inputBufferIndex == -1) {
//                inputBufferIndex = audioCodec.dequeueInputBuffer(TIMEOUT_USEC);
//            }

            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = audioCodec.getInputBuffer(inputBufferIndex);

                inputBuffer.clear();
                inputBuffer.put(buffer);

                if (isEOS == false) {
                    audioCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, timeStamp, 0);
                } else {
                    audioCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, timeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }

            } else {
                break;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//            int outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            int outputBufferIndex;

//            if (outputBufferIndex == -2) {
//                outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
//            }

            while ((outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)) >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    ByteBuffer out = audioCodec.getOutputBuffer(outputBufferIndex);

                    out.position(bufferInfo.offset);
                    out.limit(bufferInfo.offset + bufferInfo.size);

                    byte[] outBytes = new byte[bufferInfo.size + HEADER_SIZE];

                    addADTSHeader(outBytes, outBytes.length);

                    if (out != null) {
                        out.get(outBytes, HEADER_SIZE, bufferInfo.size);
                    }

//                    if (bufferedOutputStream == null) {
//                        try {
//                            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(audioPipe));
//                        } catch (FileNotFoundException fe) {
//                            fe.printStackTrace();
//                        }
//                    }
//
//                    if (bufferedOutputStream != null) {
//                        try {
//                            if (outBytes.length > 0) {
//                                bufferedOutputStream.write(outBytes);
//                            }
//                        } catch (IOException ie) {
//                            ie.printStackTrace();
//                        }
//                    }
                }
                audioCodec.releaseOutputBuffer(outputBufferIndex, false);

//                break;
//            } else {

//                break;
            }
        }
    }
}
