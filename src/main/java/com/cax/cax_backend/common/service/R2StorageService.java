package com.cax.cax_backend.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    /**
     * Uploads a MultipartFile to Cloudflare R2 bucket.
     *
     * @param file the multipart file to upload
     * @param folder the folder directory within the bucket (e.g., "id-cards/userId")
     * @return the public URL of the uploaded file
     * @throws IOException if reading the file stream fails
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Generate a unique filename using UUID
        String filename = UUID.randomUUID().toString() + extension;
        String key = folder + "/" + filename;

        log.debug("Uploading file to R2 bucket: {}, key: {}, contentType: {}", bucketName, key, file.getContentType());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // Construct public URL
        String base = publicUrl;
        if (!base.endsWith("/")) {
            base += "/";
        }
        String finalUrl = base + key;
        log.info("Successfully uploaded file to R2. URL: {}", finalUrl);
        return finalUrl;
    }

    /**
     * Deletes a file from Cloudflare R2 bucket by its public URL.
     *
     * @param fileUrl the public URL of the file to delete
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            // Extract the key from the public URL
            String base = publicUrl;
            if (!base.endsWith("/")) {
                base += "/";
            }
            if (!fileUrl.startsWith(base)) {
                log.warn("Cannot delete file: URL does not match R2 public URL. url={}", fileUrl);
                return;
            }
            String key = fileUrl.substring(base.length());

            log.debug("Deleting file from R2 bucket: {}, key: {}", bucketName, key);
            s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.info("Successfully deleted file from R2. key: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete file from R2: {}", fileUrl, e);
        }
    }
}
