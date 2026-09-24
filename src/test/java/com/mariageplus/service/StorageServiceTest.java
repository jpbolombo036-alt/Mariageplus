package com.mariageplus.service;

import com.mariageplus.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stockage objet S3-compatible : activation/désactivation selon la
 * configuration, puis upload / download / delete avec un client S3 mocké
 * (aucun appel réseau).
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService();
        ReflectionTestUtils.setField(storageService, "endpoint", "https://t3.storageapi.dev");
        ReflectionTestUtils.setField(storageService, "region", "auto");
        ReflectionTestUtils.setField(storageService, "bucket", "mariageplus-test");
        ReflectionTestUtils.setField(storageService, "accessKey", "tid_test");
        ReflectionTestUtils.setField(storageService, "secretKey", "tsec_test");
    }

    /* ============================ ACTIVATION ============================ */

    @Test
    void init_ShouldEnableStorage_WhenBucketAndAccessKeyDefined() {
        storageService.init();
        assertTrue(storageService.isEnabled());
    }

    @Test
    void init_ShouldDisableStorage_WhenBucketMissing() {
        ReflectionTestUtils.setField(storageService, "bucket", "");
        storageService.init();
        assertFalse(storageService.isEnabled());
    }

    @Test
    void init_ShouldDisableStorage_WhenAccessKeyMissing() {
        ReflectionTestUtils.setField(storageService, "accessKey", "");
        storageService.init();
        assertFalse(storageService.isEnabled());
    }

    /* ============================ UPLOAD ============================ */

    @Test
    void upload_ShouldPutObjectAndReturnKey_WhenEnabled() {
        useMockClient();

        String key = storageService.upload("drink-catalog/7/123.jpg", jpeg(), "image/jpeg");

        assertEquals("drink-catalog/7/123.jpg", key);
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("mariageplus-test", request.getValue().bucket());
        assertEquals("drink-catalog/7/123.jpg", request.getValue().key());
        assertEquals("image/jpeg", request.getValue().contentType());
        assertEquals("public, max-age=86400", request.getValue().cacheControl());
    }

    @Test
    void upload_ShouldThrowStorageException_WhenStorageDisabled() {
        assertThrows(StorageException.class,
                () -> storageService.upload("events/1/1.jpg", jpeg(), "image/jpeg"));
    }

    @Test
    void upload_ShouldThrowStorageException_WhenS3Fails() {
        useMockClient();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connexion refusee"));

        StorageException ex = assertThrows(StorageException.class,
                () -> storageService.upload("events/1/1.jpg", jpeg(), "image/jpeg"));
        assertTrue(ex.getMessage().contains("stockage objet indisponible"));
        assertNotNull(ex.getCause());
    }

    /* ============================ DOWNLOAD ============================ */

    @Test
    void download_ShouldReturnBytes_WhenObjectExists() {
        useMockClient();
        byte[] content = jpeg();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        assertArrayEquals(content, storageService.download("events/1/1.jpg"));
    }

    @Test
    void download_ShouldReturnNull_WhenObjectMissing() {
        useMockClient();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("introuvable").build());

        assertNull(storageService.download("events/1/absent.jpg"));
    }

    @Test
    void download_ShouldReturnNull_WhenStorageDisabled() {
        assertNull(storageService.download("events/1/1.jpg"));
        verifyNoInteractions(s3Client);
    }

    /* ============================ DELETE ============================ */

    @Test
    void delete_ShouldCallS3_WhenEnabled() {
        useMockClient();

        storageService.delete("avatars/1/1.jpg");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_ShouldSwallowS3Errors() {
        useMockClient();
        doThrow(SdkClientException.create("bucket supprime"))
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertDoesNotThrow(() -> storageService.delete("avatars/1/1.jpg"));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_ShouldDoNothing_WhenStorageDisabled() {
        assertDoesNotThrow(() -> storageService.delete("avatars/1/1.jpg"));
        verifyNoInteractions(s3Client);
    }

    /* ============================ HELPERS ============================ */

    /** Simule un stockage activé avec un client S3 mocké (aucun appel réseau). */
    private void useMockClient() {
        ReflectionTestUtils.setField(storageService, "s3Client", s3Client);
    }

    /** En-tête JPEG minimal (les services valident le format en amont). */
    private byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    }
}
