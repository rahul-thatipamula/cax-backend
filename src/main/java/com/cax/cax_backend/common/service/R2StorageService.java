package com.cax.cax_backend.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    @PostConstruct
    public void initBucketCors() {
        try {
            log.info("Initializing Cloudflare R2 bucket CORS settings for bucket: {}", bucketName);
            
            CORSRule corsRule = CORSRule.builder()
                    .allowedHeaders("*")
                    .allowedMethods("PUT", "GET", "POST", "HEAD", "DELETE")
                    .allowedOrigins("*")
                    .exposeHeaders("ETag")
                    .maxAgeSeconds(3600)
                    .build();

            CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                    .corsRules(corsRule)
                    .build();

            PutBucketCorsRequest putBucketCorsRequest = PutBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .corsConfiguration(corsConfiguration)
                    .build();

            s3Client.putBucketCors(putBucketCorsRequest);
            log.info("Successfully configured CORS on R2 bucket: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to configure CORS on R2 bucket: {}, error: {}", bucketName, e.getMessage(), e);
        }
    }

    /**
     * Generates a pre-signed GET URL for secure viewing of files in the private bucket.
     *
     * @param fileUrl the public static URL stored in the database
     * @return a short-lived pre-signed URL to read/download the file
     */
    public String generatePresignedGetUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return fileUrl;
        try {
            String base = publicUrl;
            if (!base.endsWith("/")) {
                base += "/";
            }
            if (!fileUrl.startsWith(base)) {
                return fileUrl; // Return original if it doesn't match our storage URL base
            }
            String key = fileUrl.substring(base.length());

            log.debug("Generating pre-signed GET URL for key: {}", key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .getObjectRequest(getObjectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.debug("Successfully generated pre-signed GET URL");
            return presignedUrl;
        } catch (Exception e) {
            log.error("Failed to generate pre-signed GET URL for {}", fileUrl, e);
            return fileUrl;
        }
    }

    /**
     * Generates a pre-signed URL for client-direct uploads to Cloudflare R2 bucket.
     *
     * @param folder the bucket folder prefix
     * @param userId user identifier (optional, can be null or empty)
     * @param extension file extension (e.g., ".jpg", ".png")
     * @param contentType file content type (e.g., "image/jpeg")
     * @return a map containing the "uploadUrl" and the "publicUrl"
     */
    public Map<String, String> generatePresignedUploadUrl(String folder, String userId, String extension, String contentType) {
        String ext = extension.startsWith(".") ? extension : "." + extension;
        String filename = UUID.randomUUID().toString() + ext;
        String key = (userId == null || userId.isBlank())
                ? folder + "/" + filename
                : folder + "/" + userId + "/" + filename;

        log.debug("Generating pre-signed URL for R2 bucket: {}, key: {}, contentType: {}", bucketName, key, contentType);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        // Construct public URL
        String base = publicUrl;
        if (!base.endsWith("/")) {
            base += "/";
        }
        String finalUrl = base + key;
        
        log.info("Successfully generated pre-signed URL: {}", uploadUrl);
        return Map.of(
                "uploadUrl", uploadUrl,
                "publicUrl", finalUrl
        );
    }

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
