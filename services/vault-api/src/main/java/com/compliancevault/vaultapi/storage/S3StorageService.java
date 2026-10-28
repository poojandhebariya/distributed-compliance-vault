
package com.compliancevault.vaultapi.storage;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String rawBucket;

    public S3StorageService(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket-raw}") String rawBucket) {
        this.s3Client = s3Client;
        this.rawBucket = rawBucket;
    }

    public String store(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();

        String extension = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = originalFilename.substring(dotIndex);
            }
        }

        String objectKey = UUID.randomUUID() + extension;

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(rawBucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()));

        return objectKey;
    }
}
