package framework.Engine;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;

public class LiveStreamingData {
    private String bucketName;
    private String uploadKey;
    private TransferUtility transferUtility;

    public LiveStreamingData(String bucketName, String uploadKey, TransferUtility transferUtility) {
        this.bucketName = bucketName;
        this.uploadKey = uploadKey;
        this.transferUtility = transferUtility;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getUploadKey() {
        return uploadKey;
    }

    public TransferUtility getTransferUtility() {
        return transferUtility;
    }
}
