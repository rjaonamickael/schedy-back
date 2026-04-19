package com.schedy.service;

import com.schedy.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S18-BE-03 — Unit tests for the magic-bytes validation added to
 * {@link R2StorageService#validateSvg(org.springframework.web.multipart.MultipartFile)}
 * and {@link R2StorageService#validatePhoto(org.springframework.web.multipart.MultipartFile)}.
 *
 * <p>The tests lock in the rule : <b>the declared Content-Type header alone
 * is never sufficient</b> — the file binary signature must match. This
 * blocks a SVG-with-{@code <script>} uploaded as {@code photo.jpg} with
 * spoofed {@code Content-Type: image/jpeg} from bypassing
 * {@link com.schedy.util.SvgSanitizer}.</p>
 */
@DisplayName("R2StorageService — magic-bytes validation (S18-BE-03)")
class R2StorageServiceValidationTest {

    private static final byte[] JPEG_HEADER = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1 };
    private static final byte[] PNG_HEADER  = new byte[] { (byte) 0x89, 'P', 'N', 'G',
            0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
    private static final byte[] WEBP_HEADER = new byte[] { 'R', 'I', 'F', 'F',
            0x20, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    private static final byte[] SVG_XML = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] SVG_DIRECT = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] SVG_WITH_BOM;
    static {
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] combined = new byte[bom.length + SVG_XML.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(SVG_XML, 0, combined, bom.length, SVG_XML.length);
        SVG_WITH_BOM = combined;
    }

    // ──────────────────────────────────────────────────────────────────────
    // validatePhoto — JPEG/PNG/WEBP must match declared Content-Type
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validatePhoto")
    class ValidatePhoto {

        @Test
        @DisplayName("JPEG with matching Content-Type passes")
        void jpegValid_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", JPEG_HEADER);
            R2StorageService.ValidatedPhoto result = R2StorageService.validatePhoto(file);
            assertThat(result.contentType()).isEqualTo("image/jpeg");
            assertThat(result.extension()).isEqualTo(".jpg");
        }

        @Test
        @DisplayName("PNG with matching Content-Type passes")
        void pngValid_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", PNG_HEADER);
            R2StorageService.ValidatedPhoto result = R2StorageService.validatePhoto(file);
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.extension()).isEqualTo(".png");
        }

        @Test
        @DisplayName("WEBP with matching Content-Type passes")
        void webpValid_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.webp", "image/webp", WEBP_HEADER);
            R2StorageService.ValidatedPhoto result = R2StorageService.validatePhoto(file);
            assertThat(result.contentType()).isEqualTo("image/webp");
            assertThat(result.extension()).isEqualTo(".webp");
        }

        @Test
        @DisplayName("SVG spoofed as JPEG (Content-Type image/jpeg + SVG bytes) is rejected")
        void svgSpoofedAsJpeg_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "evil.jpg", "image/jpeg", SVG_XML);
            assertThatThrownBy(() -> R2StorageService.validatePhoto(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ne correspond pas au type");
        }

        @Test
        @DisplayName("JPEG spoofed as PNG is rejected")
        void jpegSpoofedAsPng_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.png", "image/png", JPEG_HEADER);
            assertThatThrownBy(() -> R2StorageService.validatePhoto(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ne correspond pas");
        }

        @Test
        @DisplayName("Empty file is rejected")
        void empty_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.jpg", "image/jpeg", new byte[0]);
            assertThatThrownBy(() -> R2StorageService.validatePhoto(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Aucun fichier");
        }

        @Test
        @DisplayName("Oversized file (>2MB) is rejected (existing behaviour preserved)")
        void oversized_throws() {
            byte[] big = new byte[3 * 1024 * 1024];
            System.arraycopy(JPEG_HEADER, 0, big, 0, JPEG_HEADER.length);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.jpg", "image/jpeg", big);
            assertThatThrownBy(() -> R2StorageService.validatePhoto(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("taille maximale");
        }

        @Test
        @DisplayName("Unsupported MIME (image/gif) is rejected before magic-bytes check")
        void unsupportedMime_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.gif", "image/gif", JPEG_HEADER);
            assertThatThrownBy(() -> R2StorageService.validatePhoto(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Type de fichier non supporté");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // validateSvg — content must actually look like SVG
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateSvg")
    class ValidateSvg {

        @Test
        @DisplayName("Valid SVG (with XML prolog) passes")
        void svgXml_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.svg", "image/svg+xml", SVG_XML);
            R2StorageService.validateSvg(file);
        }

        @Test
        @DisplayName("Valid SVG starting directly with <svg> passes")
        void svgDirect_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.svg", "image/svg+xml", SVG_DIRECT);
            R2StorageService.validateSvg(file);
        }

        @Test
        @DisplayName("Valid SVG with UTF-8 BOM passes")
        void svgWithBom_passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.svg", "image/svg+xml", SVG_WITH_BOM);
            R2StorageService.validateSvg(file);
        }

        @Test
        @DisplayName("JPEG bytes with SVG Content-Type + .svg extension is rejected")
        void jpegSpoofedAsSvg_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "evil.svg", "image/svg+xml", JPEG_HEADER);
            assertThatThrownBy(() -> R2StorageService.validateSvg(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ne correspond pas");
        }

        @Test
        @DisplayName("Extension not .svg is rejected (existing behaviour preserved)")
        void wrongExtension_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.png", "image/svg+xml", SVG_XML);
            assertThatThrownBy(() -> R2StorageService.validateSvg(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Extension");
        }

        @Test
        @DisplayName("Empty SVG is rejected")
        void empty_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.svg", "image/svg+xml", new byte[0]);
            assertThatThrownBy(() -> R2StorageService.validateSvg(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Aucun fichier");
        }

        @Test
        @DisplayName("Random binary content with SVG headers is rejected")
        void binaryContent_throws() {
            byte[] random = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logo.svg", "image/svg+xml", random);
            assertThatThrownBy(() -> R2StorageService.validateSvg(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ne correspond pas");
        }
    }
}
