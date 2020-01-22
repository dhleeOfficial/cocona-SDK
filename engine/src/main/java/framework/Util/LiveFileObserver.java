package framework.Util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.Nullable;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.auth.SessionCredentialsProviderFactory;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.CannedAccessControlList;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import framework.Engine.EngineObserver;
import framework.Engine.LiveStreamingData;

public class LiveFileObserver extends FileObserver {
    private static final int mask = (FileObserver.CREATE | FileObserver.MODIFY | FileObserver.CLOSE_WRITE);

    private Context context;
    private File observerFile;
    private EngineObserver engineObserver;

    private String observerPath;
    private String writeDonePath = "init_0.mp4";

    private AmazonS3Client s3Client = null;
    private String bucketName;
    private String fileKey;
    private String region;

    private UploadFileManager uploadFileManager = null;
    private BlockingQueue<String> fileList = new LinkedBlockingDeque<String>();
    private boolean signalClose = false;

    private int createCount;
    private int uploadCount;

    public LiveFileObserver(Context context, File file, LiveStreamingData liveStreamingData, EngineObserver engineObserver) {
        super(file.getPath(), mask);
        this.context = context;

        if (liveStreamingData != null) {
            this.s3Client = liveStreamingData.getS3Client();
            this.bucketName = liveStreamingData.getBucketName();
            this.fileKey = processFileKey(liveStreamingData.getUploadKey());
            this.region = liveStreamingData.getRegionName();
        }

        this.engineObserver = engineObserver;

        createCount = 0;
        uploadCount = 0;

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

    @Override
    public void onEvent(int event, @Nullable String path) {
        switch (event) {
            case FileObserver.CREATE : {
                if (writeDonePath != path) {
                    String replace = writeDonePath.replace(".tmp", "");

                    Log.e("CREATE", path);
                    fileList.add(replace);
                    createCount++;

                    writeDonePath = path;
                }
            }
        }
    }

    public void close() {
        signalClose = true;

        // FIXME
        while (createCount != uploadCount);

        engineObserver.onCompleteLiveUpload();

        try {
            uploadFileManager.join();
            uploadFileManager = null;
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

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

    // Stub code
    //BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials("ACCESSKEY","PRIVATEKEY");
    //s3Client = new AmazonS3Client(basicAWSCredentials);
    //bucketName = "dev-ap-northeast-2-test-aaron";

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
                s3Client.setRegion(Region.getRegion(Regions.fromName(region)));
                //s3Client.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build());

                TransferUtility transferUtility = new TransferUtility(s3Client, context);

                if (transferUtility != null) {
                    final TransferObserver observer = transferUtility.upload(bucketName, fileKey, file, CannedAccessControlList.PublicRead);

                    observer.setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            if (TransferState.COMPLETED == state) {
                                uploadCount++;

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
