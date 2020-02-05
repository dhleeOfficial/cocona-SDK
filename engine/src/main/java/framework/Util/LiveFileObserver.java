package framework.Util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.Nullable;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.model.CannedAccessControlList;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import framework.Engine.EngineObserver;
import framework.Engine.LiveStreamingData;

public class LiveFileObserver extends FileObserver {
    private static final int mask = (FileObserver.CREATE | FileObserver.MODIFY | FileObserver.CLOSE_WRITE);

    private File observerFile;
    private String parseKey;
    private EngineObserver engineObserver;

    private String observerPath;
    private String writeDonePath;

    private TransferUtility transferUtility = null;
    private String bucketName;
    private String fileKey;

    private UploadFileManager uploadFileManager = null;
    private BlockingQueue<String> fileList = new LinkedBlockingDeque<String>();
    private boolean signalClose = false;

    public LiveFileObserver(File file, String parseKey, LiveStreamingData liveStreamingData, EngineObserver engineObserver) {
        super(file.getPath(), mask);

        if (liveStreamingData != null) {
            this.transferUtility = liveStreamingData.getTransferUtility();
            this.bucketName = liveStreamingData.getBucketName();
            this.fileKey = processFileKey(liveStreamingData.getUploadKey());
        }

        this.parseKey = parseKey;
        this.engineObserver = engineObserver;


        String tempPath = file.getPath();
        if (!tempPath.endsWith(File.separator)) {
            tempPath += File.separator;
        }

        observerFile = file;
        observerPath = tempPath;
        fileList.clear();

        uploadFileManager = new UploadFileManager();
        uploadFileManager.start();
    }

    private boolean skip = false;

    @Override
    public void onEvent(int event, @Nullable String path) {
        switch (event) {
            case FileObserver.CREATE : {
                if (writeDonePath != path) {
                    String replace;

                    if (writeDonePath != null) {
                        replace = writeDonePath.replace(".tmp", "");
                        fileList.add(replace);
                    }
                    writeDonePath = path;
                }
            }
        }
    }

    public void close() {
        signalClose = true;

        try {
            uploadFileManager.join();
            uploadFileManager = null;
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        engineObserver.onCompleteLiveUpload();

        File[] childrenList = observerFile.listFiles();

        if (childrenList.length > 0) {
            for (File file : childrenList) {
                file.delete();
            }
        }

        this.stopWatching();
        super.finalize();
    }

    private String processFileKey(String key) {
        int idx = key.lastIndexOf("/");

        return key.substring(0, idx);
    }

    private class UploadFileManager extends Thread {
        public UploadFileManager() {}

        @Override
        public void run() {
            while(true) {
                try {
                    final String path = fileList.take();

                    UploadTask uploadTask = new UploadTask(path);
                    uploadTask.execute();

                    if (fileList.isEmpty() && signalClose) {
                        break;
                    }
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    private class UploadTask extends AsyncTask<Void, Void, Void> {
        private String path;

        public UploadTask(String path) {
            this.path = path;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (path.isEmpty() == true) {
                return null;
            }

            final File file = new File(observerPath + path);

            if (file != null) {
                if (transferUtility != null) {
                    TransferObserver transferObserver;

                    if (parseKey != null) {
                        transferObserver = transferUtility.upload(bucketName, fileKey + "/" + parseKey + "/" + file.getName(), file, CannedAccessControlList.PublicRead);
                    } else {
                        transferObserver = transferUtility.upload(bucketName, fileKey + "/" + file.getName(), file, CannedAccessControlList.PublicRead);
                    }

                    transferObserver.setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            if (TransferState.COMPLETED == state) {
                                if (file.getPath().contains(".mp4") == true) {
                                    file.delete();
                                }
                                Log.e("UPLOAD", "SUCCESS");
                            } else if (TransferState.FAILED == state){
                                Log.e("UPLOAD", "FAILED");
                            }
                        }

                        @Override
                        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

                        }

                        @Override
                        public void onError(int id, Exception ex) {

                        }
                    });
                }
            }
            return null;
        }
    }
}
