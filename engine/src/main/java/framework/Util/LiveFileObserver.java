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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LiveFileObserver extends FileObserver {
    private Context context;
    private static final int mask = (FileObserver.CREATE | FileObserver.MODIFY | FileObserver.CLOSE_WRITE);
    private File observerFile;
    private String observerPath;
    private String writeDonePath = "init_0.mp4";

    private AmazonS3Client s3Client = null;
    private String bucketName;
    private String fileKey;

    private UploadFileManager uploadFileManager = null;
    private BlockingQueue<String> fileList = new LinkedBlockingDeque<String>();
    private boolean signalClose = false;

    private int createCount;
    private int uploadCount;

    public LiveFileObserver(Context context, File file/*, AmazonS3Client s3Client*/) {
        super(file.getPath(), mask);
        this.context = context;

        createCount = 0;
        uploadCount = 0;

        String tempPath = file.getPath();
        if (!tempPath.endsWith(File.separator)) {
            tempPath += File.separator;
        }

        //this.s3Client = s3Client;
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
        // TODO : platform api call (LIVE END)

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

    private class UploadFileManager extends Thread {
        public UploadFileManager() {
//            if (s3Client != null) {
////                OkHttpClient client = new OkHttpClient().newBuilder()
////                        .build();
////                MediaType mediaType = MediaType.parse("application/json");
////                RequestBody body = RequestBody.create(mediaType, "{\n    \"regionId\": \"us-east-1\",\n    \"userApplication\": \"MOBILE_SERVER_LESS\",\n    \"scale\": \"S1920P\",\n    \"cameraPosition\": \"BACK\",\n    \"cameraOrientation\": \"PORTRAIT\",\n    \"videoShareType\": \"PUBLIC\",\n    \"title\": \"그룹 라이브 제목입니다.\",\n    \"description\": \"description 입니다\",\n    \"gpsSensorData\": {\n        \"hdop\": 2000,\n        \"time\": 1566199644895,\n        \"speed\": -1,\n        \"course\": -1,\n        \"fixAge\": 0,\n        \"altitude\": 48.15972137451172,\n        \"latitude\": 37.5277862548828,\n        \"longitude\": 126.92301940917963\n    }\n}");
////                Request request = new Request.Builder()
////                        .url("https://api.cubi-dev.com/api/v1.1/video-service/live-videos")
////                        .method("POST", body)
////                        .addHeader("Content-Type", "application/json")
////                        .addHeader("Authorization", "Bearer ")
////                        .build();
////                try {
////                    Response response = client.newCall(request).execute();
////
////                    Log.e("UPLOADFILE", response.toString());
////                } catch (IOException ie) {
////                    ie.printStackTrace();
////                }
//                // TODO : platform api call (LIVE START)
//                //LiveStart api call
//                // Bucket name set, file key(uuid) set, s3Client region set
//            } else {
        }

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

    // TODO : 1. S3Client parameter (DONE) 2. Live start platform api (bucket name, uuid) 3. TransferUtility created (DONE) 4. upload (DONE)
    // TODO : 5. Upload success : .mp4 remove 6. Upload complete call & .m3u8 file remove 7. thumnail?
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
//                if (s3Client == null) {
//                    Log.e("UPLOAD", "s3Client not setting!");
//                    return null;
//                }
                BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials("ACCESSKEY","PRIVATEKEY");
                s3Client = new AmazonS3Client(basicAWSCredentials);
                s3Client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_2));
                //s3Client.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build());

                bucketName = "dev-ap-northeast-2-test-aaron";

                TransferUtility transferUtility = new TransferUtility(s3Client, context);

                if (transferUtility != null) {
//                    if (fileKey.isEmpty() == true) {
//                        fileKey = file.getName();
//                    }

                    final TransferObserver observer = transferUtility.upload(bucketName, /*fileKey*/file.getName(), file, CannedAccessControlList.PublicRead);

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
