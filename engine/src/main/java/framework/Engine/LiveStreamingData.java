package framework.Engine;

import com.amazonaws.services.s3.AmazonS3Client;

public class LiveStreamingData {
    private String bucketName;
    private String uploadKey;
    private String regionName;
    private AmazonS3Client s3Client;

    public LiveStreamingData(String bucketName, String uploadKey, String regionName, AmazonS3Client amazonS3Client) {
        this.bucketName = bucketName;
        this.uploadKey = uploadKey;
        this.regionName = regionName;
        this.s3Client = amazonS3Client;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getUploadKey() {
        return uploadKey;
    }

    public String getRegionName() {
        return regionName;
    }

    public AmazonS3Client getS3Client() {
        return s3Client;
    }
}
