package com.schedy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Cloudflare R2 client configuration.
 *
 * <p>R2 speaks the S3 protocol, so we use the AWS SDK v2 {@link S3Client}
 * pointed at a custom endpoint. The bean is always created so that
 * {@code @Autowired S3Client} works in any profile, but {@link #isConfigured()}
 * exposes whether the credentials are actually present — services that upload
 * use this flag to degrade gracefully in dev.
 *
 * <p>Required env / yaml:
 * <ul>
 *   <li>{@code schedy.r2.endpoint} — e.g. {@code https://<account>.r2.cloudflarestorage.com}</li>
 *   <li>{@code schedy.r2.access-key}</li>
 *   <li>{@code schedy.r2.secret-key}</li>
 *   <li>{@code schedy.r2.bucket}</li>
 *   <li>{@code schedy.r2.public-url-base} — public CDN base URL used to compose
 *       the final URL written into {@code testimonial.logo_url}</li>
 * </ul>
 *
 * <p><b>Security note:</b> the S3Client uses path-style access ({@code
 * bucket.endpoint/key}) because R2 subdomains aren't routable. This is a
 * conscious choice and matches Cloudflare's official AWS SDK examples.
 */
@Configuration
@Slf4j
public class R2Config {

    @Value("${schedy.r2.endpoint:}")                              private String endpoint;
    @Value("${schedy.r2.access-key:}")                            private String accessKey;
    @Value("${schedy.r2.secret-key:}")                            private String secretKey;
    @Value("${schedy.r2.bucket:schedy-public}")                   private String bucket;
    @Value("${schedy.r2.public-url-base:https://cdn.schedy.work}") private String publicUrlBase;
    @Value("${schedy.r2.region:auto}")                            private String region;
    /**
     * Object-key prefix for testimonial / org logos. In dev the default
     * resolves to {@code dev/assets/logos-org} (set via profile override
     * in application.yml), in prod to {@code assets/logos-org}. The prefix
     * never carries a leading/trailing slash — callers append the slash.
     */
    @Value("${schedy.prefixes.logos:assets/logos-org}")           private String logoPrefix;

    /**
     * V49 — prefix pour les photos personnelles d'utilisateurs (raster
     * JPG/PNG/WEBP). En dev : {@code dev/assets/photos-user}, en prod :
     * {@code assets/photos-user}.
     */
    @Value("${schedy.prefixes.photos:assets/photos-user}")        private String photoPrefix;

    /**
     * Returns true when all mandatory R2 settings are present. Consumed by the
     * upload service to short-circuit uploads with a clear 503 in dev if the
     * admin has not configured R2 yet.
     */
    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank()
            && accessKey != null && !accessKey.isBlank()
            && secretKey != null && !secretKey.isBlank()
            && bucket != null && !bucket.isBlank()
            && publicUrlBase != null && !publicUrlBase.isBlank();
    }

    public String getBucket() { return bucket; }

    /** Base URL used to compose the final public URL returned to the client. */
    public String getPublicUrlBase() {
        // Normalise: drop trailing slash so callers can always append "/{key}".
        if (publicUrlBase != null && publicUrlBase.endsWith("/")) {
            return publicUrlBase.substring(0, publicUrlBase.length() - 1);
        }
        return publicUrlBase;
    }

    /**
     * Normalised logo prefix — no leading slash, no trailing slash.
     * Callers append {@code "/"} + their filename.
     */
    public String getLogoPrefix() {
        return normalizePrefix(logoPrefix);
    }

    /** V49 — photos perso utilisateurs. Memes conventions que logo prefix. */
    public String getPhotoPrefix() {
        return normalizePrefix(photoPrefix);
    }

    private static String normalizePrefix(String raw) {
        String p = raw == null ? "" : raw.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (p.endsWith("/"))   p = p.substring(0, p.length() - 1);
        return p;
    }

    @Bean
    public S3Client r2S3Client() {
        if (!isConfigured()) {
            log.warn("R2 is not configured (schedy.r2.* missing). Upload endpoints will return 503.");
            // Return a "configured-but-unusable" client against localhost so the
            // bean wiring still succeeds. Any actual upload call will fail loudly.
            return S3Client.builder()
                    .region(Region.of("auto"))
                    .endpointOverride(URI.create("http://localhost:9999"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dev", "dev")))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        }

        log.info("R2Config: building S3Client for endpoint={}, bucket={}", endpoint, bucket);
        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * Non-blocking S3 client used by {@code R2StorageService} for async
     * uploads. Using the async variant means the HTTP worker thread is
     * released while R2 is talking back — important under load and the
     * right way to do S3 uploads in Spring Boot 3 / Java 21 reactive
     * world. Mirrors the sync client's endpoint + credentials config.
     */
    @Bean
    public S3AsyncClient r2S3AsyncClient() {
        if (!isConfigured()) {
            log.warn("R2 async client: not configured — uploads will fail fast.");
            return S3AsyncClient.builder()
                    .region(Region.of("auto"))
                    .endpointOverride(URI.create("http://localhost:9999"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dev", "dev")))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        }

        log.info("R2Config: building S3AsyncClient for endpoint={}, bucket={}, logoPrefix={}",
                endpoint, bucket, getLogoPrefix());
        return S3AsyncClient.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
