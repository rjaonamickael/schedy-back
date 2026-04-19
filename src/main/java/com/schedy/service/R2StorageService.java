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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Cloudflare R2 upload service.
 *
 * <p>V48 refactor : deux pipelines coexistent
 * <ul>
 *   <li><b>SVG logos</b> (org + legacy testimonial) : sanitisation obligatoire via
 *       {@link SvgSanitizer}, prefix {@code r2Config.getLogoPrefix()}, MIME
 *       {@code image/svg+xml}, size max {@link SvgSanitizer#MAX_SVG_BYTES}.</li>
 *   <li><b>Raster photos</b> (user profile) : validation MIME + taille, pas de
 *       sanitisation possible sur binaire, prefix {@code r2Config.getPhotoPrefix()}.</li>
 * </ul>
 *
 * <p>Les deux pipelines partagent le meme S3AsyncClient, la meme URL publique,
 * et la meme logique {@link #isOwnedUrl(String)} pour valider qu'une URL vient
 * bien de notre bucket avant persistance cote temoignage.
 *
 * <p><b>Async upload.</b> {@link S3AsyncClient} libere le thread HTTP pendant
 * que R2 acknowledge le PUT. Les controleurs retournent {@link CompletableFuture}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    /** MIME types acceptes pour SVG — certains navigateurs envoient "text/xml". */
    private static final Set<String> ACCEPTED_SVG_MIME = Set.of(
            "image/svg+xml",
            "image/svg",
            "text/xml",
            "application/xml"
    );

    /** MIME types acceptes pour photo perso (raster). */
    private static final Set<String> ACCEPTED_PHOTO_MIME = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    /** Taille max photo perso = 2 Mo. SVG utilise {@link SvgSanitizer#MAX_SVG_BYTES}. */
    private static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;

    private final S3AsyncClient r2S3AsyncClient;
    private final R2Config r2Config;

    // =========================================================================
    // PIPELINE 1 — SVG logos (org brand, testimonial legacy)
    // =========================================================================

    /**
     * Sync wrapper pour compat retro (callers qui n'utilisent pas l'async).
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
     * Upload non-bloquant d'un logo SVG. Validation + sanitisation synchrones
     * (cpu-bound, rapide) pouvant lever {@link BusinessRuleException} (422)
     * avant creation du future. Le PUT est delegue a {@link S3AsyncClient}.
     */
    public CompletableFuture<String> uploadTestimonialLogoAsync(MultipartFile file) {
        return uploadSvgAsync(file, r2Config.getLogoPrefix());
    }

    /** V48 — alias explicite pour l'endpoint /organisation/me/logo. */
    public CompletableFuture<String> uploadOrgLogoAsync(MultipartFile file) {
        return uploadSvgAsync(file, r2Config.getLogoPrefix());
    }

    private CompletableFuture<String> uploadSvgAsync(MultipartFile file, String prefix) {
        ensureConfigured();
        validateSvg(file);

        final byte[] raw;
        try {
            raw = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("Impossible de lire le fichier téléversé.");
        }

        final String cleanSvg = SvgSanitizer.sanitize(raw);
        final byte[] cleanBytes = cleanSvg.getBytes(StandardCharsets.UTF_8);

        final String objectKey = prefix + "/" + UUID.randomUUID() + ".svg";
        return putToR2(objectKey, cleanBytes, "image/svg+xml", "SVG");
    }

    // =========================================================================
    // PIPELINE 2 — Raster photos (user profile)
    // =========================================================================

    /**
     * V49 — upload non-bloquant d'une photo perso utilisateur (JPG/PNG/WEBP).
     * Pas de sanitisation binaire possible — validation MIME + size uniquement.
     * Taille max = 2 Mo.
     */
    public CompletableFuture<String> uploadUserPhotoAsync(MultipartFile file) {
        ensureConfigured();
        ValidatedPhoto vp = validatePhoto(file);

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("Impossible de lire le fichier téléversé.");
        }

        final String objectKey = r2Config.getPhotoPrefix() + "/" + UUID.randomUUID() + vp.extension;
        return putToR2(objectKey, bytes, vp.contentType, "photo");
    }

    // =========================================================================
    // Helpers communs
    // =========================================================================

    private CompletableFuture<String> putToR2(String objectKey, byte[] bytes, String contentType, String kind) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(r2Config.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        return r2S3AsyncClient
                .putObject(putRequest, AsyncRequestBody.fromBytes(bytes))
                .thenApply(response -> {
                    String publicUrl = r2Config.getPublicUrlBase() + "/" + objectKey;
                    log.info("R2 uploaded {}: key={}, size={}B, publicUrl={}",
                            kind, objectKey, bytes.length, publicUrl);
                    return publicUrl;
                })
                .exceptionally(ex -> {
                    log.error("R2 upload failed for key={}: {}", objectKey, ex.getMessage(), ex);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Échec de l'envoi vers le stockage distant. Réessayez.");
                });
    }

    private void ensureConfigured() {
        if (!r2Config.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le stockage de médias n'est pas encore configuré sur ce serveur.");
        }
    }

    /**
     * Validates that a claimed URL was produced by our upload endpoints.
     * Accepts both the logo prefix (SVG) and photo prefix (raster).
     */
    public boolean isOwnedUrl(String url) {
        if (url == null || url.isBlank()) return true; // null is legit (no media)
        String base = r2Config.getPublicUrlBase();
        if (base == null || base.isBlank()) return false;
        return url.startsWith(base + "/" + r2Config.getLogoPrefix() + "/")
            || url.startsWith(base + "/" + r2Config.getPhotoPrefix() + "/");
    }

    /**
     * Best-effort deletion — no distinction between logo & photo pipelines
     * (isOwnedUrl accepts both, key derivation is identical).
     */
    public void deleteLogo(String publicUrl) {
        deleteBlob(publicUrl);
    }

    /** V48 — alias sémantique. */
    public void deleteBlob(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        if (!isOwnedUrl(publicUrl)) {
            log.warn("R2 delete skipped — URL is not owned by us: {}", publicUrl);
            return;
        }
        if (!r2Config.isConfigured()) {
            log.warn("R2 delete skipped — storage not configured");
            return;
        }

        String base = r2Config.getPublicUrlBase();
        String objectKey = publicUrl.substring(base.length() + 1);

        try {
            r2S3AsyncClient.deleteObject(DeleteObjectRequest.builder()
                            .bucket(r2Config.getBucket())
                            .key(objectKey)
                            .build())
                    .whenComplete((resp, ex) -> {
                        if (ex != null) {
                            log.warn("R2 delete failed for key={}: {}", objectKey, ex.getMessage());
                        } else {
                            log.info("R2 deleted object: key={}", objectKey);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("R2 delete threw for key={}: {}", objectKey, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Validators
    // ------------------------------------------------------------------

    /** Package-private for unit tests ({@code R2StorageServiceValidationTest}). */
    static void validateSvg(MultipartFile file) {
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
            if (!ACCEPTED_SVG_MIME.contains(lowered)) {
                throw new BusinessRuleException(
                        "Type de fichier non supporté (" + contentType + "). SVG uniquement.");
            }
        }
        String original = file.getOriginalFilename();
        if (original != null && !original.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            throw new BusinessRuleException("Extension non supportée — seul le .svg est accepté.");
        }
        // S18-BE-03 — magic-bytes check: the file content must actually look
        // like SVG (text-based, starts with <?xml or <svg after optional BOM
        // and whitespace). Blocks JPEG/PNG/WEBP/binary disguised as SVG.
        FileKind kind = detectFileSignature(file);
        if (kind != FileKind.SVG) {
            throw new BusinessRuleException(
                    "Le contenu du fichier ne correspond pas au type SVG déclaré.");
        }
    }

    record ValidatedPhoto(String contentType, String extension) {}

    /** Package-private for unit tests ({@code R2StorageServiceValidationTest}). */
    static ValidatedPhoto validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Aucun fichier fourni.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessRuleException(
                    "La photo dépasse la taille maximale (" + (MAX_PHOTO_BYTES / 1024 / 1024) + " Mo).");
        }
        String contentType = file.getContentType();
        String lowered = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ACCEPTED_PHOTO_MIME.contains(lowered)) {
            throw new BusinessRuleException(
                    "Type de fichier non supporté (" + contentType + "). JPG, PNG ou WEBP uniquement.");
        }
        // S18-BE-03 — magic-bytes check: the declared MIME must match the
        // actual binary signature. Blocks a malicious SVG renamed .jpg with
        // spoofed Content-Type: image/jpeg from reaching R2 and skipping
        // SvgSanitizer.
        FileKind kind = detectFileSignature(file);
        if (!matchesDeclaredPhotoMime(kind, lowered)) {
            throw new BusinessRuleException(
                    "Le contenu du fichier ne correspond pas au type annoncé (" + contentType + ").");
        }
        String ext = switch (lowered) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png"               -> ".png";
            case "image/webp"              -> ".webp";
            default                        -> ".bin";
        };
        return new ValidatedPhoto(lowered.equals("image/jpg") ? "image/jpeg" : lowered, ext);
    }

    // ------------------------------------------------------------------
    // S18-BE-03 — Magic-bytes signature detection (no external dep)
    // ------------------------------------------------------------------

    /** File-kind inferred from the first few bytes (NOT from the Content-Type header). */
    private enum FileKind { JPEG, PNG, WEBP, SVG, UNKNOWN }

    /**
     * Inspects the first 256 bytes of the uploaded file and returns the
     * detected kind. Uses industry-standard magic-bytes signatures:
     * <ul>
     *   <li>JPEG : {@code FF D8 FF}</li>
     *   <li>PNG  : {@code 89 50 4E 47 0D 0A 1A 0A}</li>
     *   <li>WEBP : {@code "RIFF" ???? "WEBP"} (bytes 0-3 and 8-11)</li>
     *   <li>SVG  : UTF-8 text starting with {@code <?xml} or {@code <svg}
     *             after an optional BOM and whitespace</li>
     * </ul>
     */
    private static FileKind detectFileSignature(MultipartFile file) {
        byte[] head;
        try {
            byte[] all = file.getBytes();
            int len = Math.min(256, all.length);
            head = new byte[len];
            System.arraycopy(all, 0, head, 0, len);
        } catch (java.io.IOException e) {
            log.warn("Could not read file bytes for magic-bytes detection: {}", e.getMessage());
            return FileKind.UNKNOWN;
        }
        if (head.length == 0) return FileKind.UNKNOWN;

        if (head.length >= 3
                && (head[0] & 0xFF) == 0xFF
                && (head[1] & 0xFF) == 0xD8
                && (head[2] & 0xFF) == 0xFF) {
            return FileKind.JPEG;
        }
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A) {
            return FileKind.PNG;
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return FileKind.WEBP;
        }
        // SVG fallback: text starting with <?xml or <svg after optional BOM.
        String text = new String(head, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String trimmed = text.stripLeading().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<svg")) {
            return FileKind.SVG;
        }
        return FileKind.UNKNOWN;
    }

    private static boolean matchesDeclaredPhotoMime(FileKind kind, String loweredMime) {
        return switch (loweredMime) {
            case "image/jpeg", "image/jpg" -> kind == FileKind.JPEG;
            case "image/png"               -> kind == FileKind.PNG;
            case "image/webp"              -> kind == FileKind.WEBP;
            default                        -> false;
        };
    }
}
