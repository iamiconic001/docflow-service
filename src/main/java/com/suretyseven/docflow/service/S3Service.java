package com.suretyseven.docflow.service;

public interface S3Service {

    String generatePresignedPutUrl(String s3Path, String contentType);

    String generatePresignedGetUrl(String s3Path);

    void deleteObject(String s3Path);
}
