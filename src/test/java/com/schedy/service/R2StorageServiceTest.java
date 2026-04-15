package com.schedy.service;

import com.schedy.config.R2Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link R2StorageService#deleteLogo(String)}.
 *
 * <p>Upload flows are intentionally not covered here — they hit
 * {@link software.amazon.awssdk.services.s3.S3AsyncClient} and the SVG
 * sanitizer, both of which already have their own integration coverage.
 * The delete path is the one we added in this session, so it needs a
 * focused unit test to lock in the no-op guards and the happy-path
 * delete request shape.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("R2StorageService.deleteLogo() unit tests")
class R2StorageServiceTest {

    @Mock private S3AsyncClient r2S3AsyncClient;
    @Mock private R2Config r2Config;

    @InjectMocks private R2StorageService r2StorageService;

    private static final String PUBLIC_BASE = "https://cdn.example.com";
    private static final String LOGO_PREFIX = "logos";
    private static final String BUCKET = "schedy-assets";

    @BeforeEach
    void setUp() {
        lenient().when(r2Config.getPublicUrlBase()).thenReturn(PUBLIC_BASE);
        lenient().when(r2Config.getLogoPrefix()).thenReturn(LOGO_PREFIX);
        lenient().when(r2Config.getBucket()).thenReturn(BUCKET);
        lenient().when(r2Config.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("no-op when url is null — never touches the S3 client")
    void deleteLogo_noOp_whenUrlNull() {
        r2StorageService.deleteLogo(null);
        verifyNoInteractions(r2S3AsyncClient);
    }

    @Test
    @DisplayName("no-op when url is blank")
    void deleteLogo_noOp_whenUrlBlank() {
        r2StorageService.deleteLogo("   ");
        verifyNoInteractions(r2S3AsyncClient);
    }

    @Test
    @DisplayName("no-op when url is not owned by us (third-party host)")
    void deleteLogo_noOp_whenUrlNotOwned() {
        r2StorageService.deleteLogo("https://evil.example.com/logos/abc.svg");
        verifyNoInteractions(r2S3AsyncClient);
    }

    @Test
    @DisplayName("no-op when url is under our base but outside the logo prefix")
    void deleteLogo_noOp_whenUrlWrongPrefix() {
        r2StorageService.deleteLogo(PUBLIC_BASE + "/other-bucket/abc.svg");
        verifyNoInteractions(r2S3AsyncClient);
    }

    @Test
    @DisplayName("no-op when R2 is not configured — never tries to call the SDK")
    void deleteLogo_noOp_whenNotConfigured() {
        when(r2Config.isConfigured()).thenReturn(false);
        r2StorageService.deleteLogo(PUBLIC_BASE + "/" + LOGO_PREFIX + "/abc-123.svg");
        verifyNoInteractions(r2S3AsyncClient);
    }

    @Test
    @DisplayName("happy path: issues a DeleteObjectRequest with the right bucket and key")
    void deleteLogo_issuesDeleteRequest_forOwnedUrl() {
        when(r2S3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        DeleteObjectResponse.builder().build()));

        String publicUrl = PUBLIC_BASE + "/" + LOGO_PREFIX + "/abc-123.svg";
        r2StorageService.deleteLogo(publicUrl);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(r2S3AsyncClient).deleteObject(captor.capture());

        DeleteObjectRequest sent = captor.getValue();
        assertThat(sent.bucket()).isEqualTo(BUCKET);
        assertThat(sent.key()).isEqualTo(LOGO_PREFIX + "/abc-123.svg");
    }

    @Test
    @DisplayName("happy path swallows an R2 failure instead of throwing to the caller")
    void deleteLogo_swallowsR2Failure() {
        when(r2S3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("R2 down")));

        // Must not throw — the whole point of best-effort cleanup is that DB
        // delete stays authoritative regardless of R2 state.
        r2StorageService.deleteLogo(PUBLIC_BASE + "/" + LOGO_PREFIX + "/abc-123.svg");

        verify(r2S3AsyncClient).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("synchronous SDK exception is swallowed — caller never sees it")
    void deleteLogo_swallowsSyncException() {
        when(r2S3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("SDK boom"));

        r2StorageService.deleteLogo(PUBLIC_BASE + "/" + LOGO_PREFIX + "/abc-123.svg");

        verify(r2S3AsyncClient).deleteObject(any(DeleteObjectRequest.class));
    }
}
