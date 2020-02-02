package framework.Engine;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;

/**
 * LiveStreamingData Class
 */
public class LiveStreamingData {
    private String bucketName;
    private String uploadKey;
    private TransferUtility transferUtility;

    /**
     * LiveStreamingData Constructor
     * @param bucketName bucket name to Upload (Result of live-videos platform api call <key : thumbnailBucketName>)
     * @param uploadKey upload key to Upload (Result of live-videos platform api call <key : path>)
     * @param transferUtility transferUtility instance to Upload (example in test-app)
     */
    public LiveStreamingData(String bucketName, String uploadKey, TransferUtility transferUtility) {
        this.bucketName = bucketName;
        this.uploadKey = uploadKey;
        this.transferUtility = transferUtility;
    }

    /**
     * This function used by engine
     * @return bucket name
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * This function used by engine
     * @return upload key
     */
    public String getUploadKey() {
        return uploadKey;
    }

    /**
     * This function used by engine
     * @return transferUtility instance
     */
    public TransferUtility getTransferUtility() {
        return transferUtility;
    }
}
