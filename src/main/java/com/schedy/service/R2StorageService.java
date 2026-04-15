package com.schedy.service;

import com.schedy.config.R2Config;
import com.schedy.exception.BusinessRuleException;
import com.schedy.util.SvgSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Cloudflare R2 upload service.
 *
 * <p>Phase 1 scope: testimonial / organisation logos. The service is
 * intentionally specialised — a generic "resource storage" abstraction
 * will come later once there's more than one caller.
 *
 * <p><b>Async upload.</b> We use {@link S3AsyncClient} so the HTTP worker
 * thread is released while R2 is acknowledging the PUT. The controller
 * returns a {@link CompletableFuture} and Spring MVC handles it natively.
 *
 * <p><b>Contract:</b> the caller passes a {@link MultipartFile}; the service
 * validates MIME/size, sanitizes the SVG content, uploads to R2, and returns
 * the public HTTPS URL to store on the testimonial row. The raw (unsanitized)
 * bytes never touch R2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    /** Accepted MIME types for SVG. Some browsers send "text/xml". */
    private static final java.util.Set<String> ACCEPTED_MIME = java.util.Set.of(
            "image/svg+xml",
            "image/svg",
            "text/xml",
            "application/xml"
    );

    private final S3AsyncClient r2S3AsyncClient;
    private final R2Config r2Config;

    /**
     * Synchronous wrapper kept for callers that don't need the non-blocking
     * path. Delegates to {@link #uploadTestimonialLogoAsync(MultipartFile)}
     * and joins the future, rethrowing the cause unwrapped.
     */
    public String uploadTestimonialLogo(MultipartFile file) {
        try {
            return uploadTestimonialLogoAsync(file).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    /**
     * Non-blocking upload. Validation + SVG sanitization happen synchronously
     * (cpu-bound, fast) and may throw {@link BusinessRuleException} before
     * the future is created — callers see those as regular HTTP 422s. Once
     * the bytes are clean, the actual PUT is handed to {@link S3AsyncClient}
     * which completes the returned future when R2 acknowledges.
     *
     * @throws ResponseStatusException 503 when R2 is not configured (fail-fast)
     * @throws BusinessRuleException   for validation / sanitation failures
     */
    public CompletableFuture<String> uploadTestimonialLogoAsync(MultipartFile file) {
        if (!r2Config.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le stockage de logos n'est pas encore configuré sur ce serveur.");
        }

        validate(file);

        final byte[] raw;
        try {
            raw = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("Impossible de lire le fichier téléversé.");
        }

        // Sanitize BEFORE uploading. Failure here surfaces as 422.
        final String cleanSvg = SvgSanitizer.sanitize(raw);
        final byte[] cleanBytes = cleanSvg.getBytes(StandardCharsets.UTF_8);

        final String objectKey = r2Config.getLogoPrefix() + "/" + UUID.randomUUID() + ".svg";
        final String bucket = r2Config.getBucket();

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("image/svg+xml")
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        return r2S3AsyncClient
                .putObject(putRequest, AsyncRequestBody.fromBytes(cleanBytes))
                .thenApply(response -> {
                    String publicUrl = r2Config.getPublicUrlBase() + "/" + objectKey;
                    log.info("R2 uploaded testimonial logo: key={}, size={}B, publicUrl={}",
                            objectKey, cleanBytes.length, publicUrl);
                    return publicUrl;
                })
                .exceptionally(ex -> {
                    log.error("R2 upload failed for key={}: {}", objectKey, ex.getMessage(), ex);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Échec de l'envoi vers le stockage distant. Réessayez.");
                });
    }

    /**
     * Validates that a claimed logo URL was actually produced by our upload
     * endpoint (i.e. points to our R2 public base under the configured logo
     * prefix). Called by the main testimonial submit flow to prevent a
     * client from setting {@code logoUrl} to an arbitrary third-party URL.
     */
    public boolean isOwnedUrl(String url) {
        if (url == null || url.isBlank()) return true; // null is legit (no logo)
        String base = r2Config.getPublicUrlBase();
        if (base == null || base.isBlank()) return false;
        return url.startsWith(base + "/" + r2Config.getLogoPrefix() + "/");
    }

    /**
     * Best-effort deletion of a previously uploaded logo object. Swallows
     * failures (missing config, network hiccup, already-gone key) and logs
     * them so DB cleanup is never blocked by R2 state. Called by the
     * testimonial delete flow; safe to call with a null or foreign URL
     * (no-op in both cases).
     */
    public void deleteLogo(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        if (!isOwnedUrl(publicUrl)) {
            log.warn("R2 deleteLogo skipped — URL is not owned by us: {}", publicUrl);
            return;
        }
        if (!r2Config.isConfigured()) {
            log.warn("R2 deleteLogo skipped — storage not configured");
            return;
        }

        // Strip the public base to recover the object key.
        String base = r2Config.getPublicUrlBase();
        String objectKey = publicUrl.substring(base.length() + 1); // +1 for the "/" separator

        try {
            r2S3AsyncClient.deleteObject(DeleteObjectRequest.builder()
                            .bucket(r2Config.getBucket())
                            .key(objectKey)
                            .build())
                    .whenComplete((resp, ex) -> {
                        if (ex != null) {
                            log.warn("R2 deleteLogo failed for key={}: {}", objectKey, ex.getMessage());
                        } else {
                            log.info("R2 deleted testimonial logo: key={}", objectKey);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("R2 deleteLogo threw for key={}: {}", objectKey, e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Aucun fichier fourni.");
        }
        if (file.getSize() > SvgSanitizer.MAX_SVG_BYTES) {
            throw new BusinessRuleException(
                    "Le fichier dépasse la taille maximale ("
                            + (SvgSanitizer.MAX_SVG_BYTES / 1024) + " KB).");
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            String lowered = contentType.toLowerCase(Locale.ROOT);
            if (!ACCEPTED_MIME.contains(lowered)) {
                throw new BusinessRuleException(
                        "Type de fichier non supporté (" + contentType + "). SVG uniquement.");
            }
        }
        String original = file.getOriginalFilename();
        if (original != null && !original.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            throw new BusinessRuleException("Extension non supportée — seul le .svg est accepté.");
        }
    }
}
