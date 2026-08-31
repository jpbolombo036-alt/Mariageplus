package com.mariageplus.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Service de stockage objet S3-compatible (AWS S3, Cloudflare R2, Backblaze B2,
 * MinIO, ou tout endpoint S3 exposé par Railway).
 *
 * <p>Activé uniquement si {@code S3_BUCKET} et {@code S3_ACCESS_KEY} sont définis.
 * Sinon, les appelants doivent retomber sur le stockage en base (comportement
 * historique des avatars).</p>
 */
@Service
@Slf4j
public class StorageService {

    @Value("${storage.s3.endpoint:}")
    private String endpoint;

    @Value("${storage.s3.region:us-east-1}")
    private String region;

    @Value("${storage.s3.bucket:}")
    private String bucket;

    @Value("${storage.s3.access-key:}")
    private String accessKey;

    @Value("${storage.s3.secret-key:}")
    private String secretKey;

    private S3Client s3Client;

    @PostConstruct
    void init() {
        boolean enabled = !bucket.isBlank() && !accessKey.isBlank();
        if (!enabled) {
            log.info("Stockage S3 desactive (S3_BUCKET / S3_ACCESS_KEY non definis) : stockage en base utilise");
            return;
        }
        var builder = S3Client.builder()
                .region(Region.of(region == null || region.isBlank() ? "us-east-1" : region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (endpoint != null && !endpoint.isBlank()) {
            // Endpoint custom (R2, B2, MinIO, gateway S3 sur Railway...)
            builder.endpointOverride(URI.create(endpoint));
        }
        s3Client = builder.build();
        log.info("Stockage S3 actif : bucket={}", bucket);
    }

    /** true si le stockage objet est configuré et utilisable. */
    public boolean isEnabled() {
        return s3Client != null;
    }

    /** Upload l'image et retourne la clé objet. */
    public String upload(String key, byte[] bytes, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .cacheControl("public, max-age=86400")
                        .build(),
                RequestBody.fromBytes(bytes));
        return key;
    }

    /** Télécharge l'objet ; retourne null si introuvable. */
    public byte[] download(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()).asByteArray();
        } catch (Exception e) {
            log.warn("Objet S3 introuvable ou illisible : {}", key);
            return null;
        }
    }

    /** Supprime l'objet (ignore les erreurs : suppression best-effort). */
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Impossible de supprimer l'objet S3 {} : {}", key, e.getMessage());
        }
    }
}
