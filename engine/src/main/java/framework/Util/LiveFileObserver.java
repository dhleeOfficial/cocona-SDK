package framework.Util;

import android.app.Activity;
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
import com.amazonaws.services.s3.model.CannedAccessControlList;

import java.io.File;

public class LiveFileObserver extends FileObserver {
    private Context context;
    private static final int mask = (FileObserver.CREATE | FileObserver.MODIFY | FileObserver.CLOSE_WRITE);
    private File observerFile;
    private String observerPath;

    private String writeDonePath = "init.mp4";

    public LiveFileObserver(Context context, File file) {
        super(file.getPath(), mask);
        this.context = context;


        String tempPath = file.getPath();
        if (!tempPath.endsWith(File.separator)) {
            tempPath += File.separator;
        }

        observerFile = file;
        observerPath = tempPath;
    }

    @Override
    public void onEvent(int event, @Nullable String path) {
        switch (event) {
            case FileObserver.CREATE : {
                // s3 upload & file remove call
                Log.e("CREATE EVENT", path);

                if (writeDonePath != path) {
                    Log.e("WRITE DONE", writeDonePath);
                    String replace = writeDonePath.replace(".tmp", "");

                    Log.e("REPLACE DONE", replace);
                    UploadTask uploadTask = new UploadTask(replace);
                    uploadTask.execute();

                    writeDonePath = path;
                }
            }
        }
    }

    public void close() {
//        File[] childrenList = observerFile.listFiles();
//
//        if (childrenList.length > 0) {
//            for (File file : childrenList) {
//                file.delete();
//            }
//        }
        this.stopWatching();
        super.finalize();
        //TODO MUXMANAGER CALL INTERFACE
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
            File file = new File(observerPath + path);

            if (file != null) {
                //if (file.exists() == true) {
                CognitoCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(context, "us-east-1:b29bba36-b3f3-488a-8ee3-f832c561542a", Regions.US_EAST_1);
                BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials("ACCESSKEY","SECRETKEY");

                AmazonS3Client s3Client = new AmazonS3Client(basicAWSCredentials);

                s3Client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_2));

                TransferUtility transferUtility = new TransferUtility(s3Client, context);

                if (transferUtility != null) {
                    String bucketName = "dev-ap-northeast-2-test-aaron";
                    final TransferObserver observer = transferUtility.upload(bucketName, file.getName(), file, CannedAccessControlList.PublicRead);

                    observer.setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            if (TransferState.COMPLETED == state) {
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
