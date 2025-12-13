package run.runnable.kage.util;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class R2StorageAPI {

    private final AwsClientBuilder.EndpointConfiguration endpointConfiguration;
    private final AWSStaticCredentialsProvider awsStaticCredentialsProvider;
    private final String bucket;

    private AmazonS3 client;

    private R2StorageAPI(String apiUrl, String accessKey, String secretKey, String bucket) {
        this.endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(apiUrl, "auto");
        this.awsStaticCredentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        this.bucket = bucket;
        this.initialize();
    }

    public void initialize() {
        client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(this.endpointConfiguration)
                .withCredentials(this.awsStaticCredentialsProvider)
                .build();
    }

    public void deleteFile(String identifier) {
        this.client.deleteObject(new DeleteObjectRequest(this.bucket, identifier));
    }
    public void uploadFile(String identifier, InputStream inputStream, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(this.bucket, identifier, inputStream, metadata);
        this.client.putObject(putObjectRequest);
    }

    public void uploadFile(String identifier, InputStream inputStream) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(this.bucket, identifier, inputStream, null);
        this.client.putObject(putObjectRequest);
    }

    public void uploadFile(String identifier, File file) {
        this.client.putObject(new PutObjectRequest(this.bucket, identifier, file));
    }


    public void downloadFile(String identifier, File file) {
        S3Object object = this.client.getObject(new GetObjectRequest(this.bucket, identifier));
        try (InputStream inputStream = object.getObjectContent()) {
            try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public ObjectMetadata getMetadata(String identifier) {
        return this.client.getObject(new GetObjectRequest(this.bucket, identifier)).getObjectMetadata();
    }

    public static R2StorageAPI create(String apiUrl, String accessKey, String secretKey, String bucket) {
        return new R2StorageAPI(apiUrl, accessKey, secretKey, bucket);
    }

    /**
     * 获取随机前缀 和数量的文件
     * @param size
     * @return {@link List}<{@link S3ObjectSummary}>
     */
    public List<S3ObjectSummary> listRandomObjectByLimit(int size){
        ObjectListing objectListing;
        List<S3ObjectSummary> result = new ArrayList<>(size);
        String nextMarker = null;

        do{

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            listObjectsRequest.setBucketName(this.bucket);
            listObjectsRequest.setMaxKeys(size);
            listObjectsRequest.setMarker(nextMarker);
            listObjectsRequest.setPrefix(getRandomChar());

            objectListing = this.client.listObjects(listObjectsRequest);
            nextMarker = objectListing.getNextMarker();

            result.addAll(objectListing.getObjectSummaries());
        }while (nextMarker != null && result.size() < size);
        return result;
    }

    private String getRandomChar(){
        // 创建Random对象
        Random random = new Random();

        // 生成一个随机的整数，范围在0到25（对应字母A-Z的索引）
        int randomIndex = random.nextInt(26);

        // 将索引转换为字母（A的ASCII值是65）
        return (char) ('A' + randomIndex)+"";
    }

    /**
     * 获取指定数量的文件
     * @param size
     * @return {@link List}<{@link S3ObjectSummary}>
     */
    public List<S3ObjectSummary> listObjectByLimit(int size, String prefix){
        ObjectListing objectListing;
        List<S3ObjectSummary> result = new ArrayList<>(size);
        String nextMarker = null;
        do{
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            listObjectsRequest.setBucketName(this.bucket);
            listObjectsRequest.setMaxKeys(size);
            listObjectsRequest.setPrefix(prefix);
            listObjectsRequest.setMarker(nextMarker);

            objectListing = this.client.listObjects(listObjectsRequest);
            nextMarker = objectListing.getNextMarker();

            result.addAll(objectListing.getObjectSummaries());
        }while (nextMarker != null && result.size() < size);
        return result;
    }

    /**
     * 获取指定数量的文件
     * @param size
     * @return {@link List}<{@link S3ObjectSummary}>
     */
    public List<S3ObjectSummary> listObjectByLimit(int size){
        ObjectListing objectListing;
        List<S3ObjectSummary> result = new ArrayList<>(size);
        String nextMarker = null;
        do{
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            listObjectsRequest.setBucketName(this.bucket);
            listObjectsRequest.setMaxKeys(size);
            listObjectsRequest.setMarker(nextMarker);

            objectListing = this.client.listObjects(listObjectsRequest);
            nextMarker = objectListing.getNextMarker();

            result.addAll(objectListing.getObjectSummaries());
        }while (nextMarker != null && result.size() < size);
        return result;
    }

    public List<S3ObjectSummary> listAllObject(){
        ObjectListing objectListing;
        List<S3ObjectSummary> result = new ArrayList<>(1000);
        String nextMarker = null;
        do{
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            listObjectsRequest.setBucketName(this.bucket);
            listObjectsRequest.setMaxKeys(1000);
            listObjectsRequest.setMarker(nextMarker);

            objectListing = this.client.listObjects(listObjectsRequest);
            nextMarker = objectListing.getNextMarker();

            result.addAll(objectListing.getObjectSummaries());
        }while (nextMarker != null );
        return result;
    }

//    public static void main(String[] args) {
//        R2StorageAPI r2StorageAPI = create("https://8034b6f645143efa728dad5bdf39e7bd.r2.cloudflarestorage.com",
//                "fa87eff0175594f3612cd570f3f1af57",
//                "1c785c15f5fff925335feed721bb20fa826ac4e9a731b2d64bbd6d0af0ad25ef",
//                "img-runnable-run");
//
//        r2StorageAPI.uploadFile("blog/"+UUID.randomUUID() + "." + FilenameUtils.getExtension(file.getName()), file);
//    }
}
