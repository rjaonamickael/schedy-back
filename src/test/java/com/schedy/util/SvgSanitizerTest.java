package com.schedy.util;

import com.schedy.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for {@link SvgSanitizer}. Every branch is exercised so that
 * a future refactor can't silently reintroduce an XSS or XXE vector.
 */
@DisplayName("SvgSanitizer — XSS / XXE / DoS hardening")
class SvgSanitizerTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ====================================================================
    // Happy path
    // ====================================================================

    @Nested
    @DisplayName("Valid SVG is preserved")
    class ValidSvg {

        @Test
        @DisplayName("keeps a minimal <svg> with a <path>")
        void keepsMinimalSvg() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">"
                    + "<path d=\"M 0 0 L 24 24\" fill=\"#10B981\" /></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).contains("<svg");
            assertThat(out).contains("<path");
            assertThat(out).contains("d=\"M 0 0 L 24 24\"");
            assertThat(out).contains("fill=\"#10B981\"");
        }

        @Test
        @DisplayName("keeps allowed gradient structure")
        void keepsGradients() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<defs><linearGradient id=\"g\">"
                    + "<stop offset=\"0\" stop-color=\"#000\"/>"
                    + "<stop offset=\"1\" stop-color=\"#fff\"/>"
                    + "</linearGradient></defs>"
                    + "<rect width=\"100\" height=\"100\" fill=\"url(#g)\"/>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).contains("linearGradient");
            assertThat(out).contains("stop");
            assertThat(out).contains("rect");
        }

        @Test
        @DisplayName("keeps <title> and <desc> text nodes alongside a shape")
        void keepsTitleAndDesc() {
            // <title>/<desc> alone would now be rejected by validateHasContent;
            // include a real shape so we still test the metadata preservation path.
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<title>Schedy</title><desc>Logo officiel</desc>"
                    + "<circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).contains("Schedy");
            assertThat(out).contains("Logo officiel");
            assertThat(out).contains("circle");
        }
    }

    // ====================================================================
    // XSS vectors
    // ====================================================================

    @Nested
    @DisplayName("XSS vectors are stripped")
    class XssVectors {

        @Test
        @DisplayName("strips inline <script> children")
        void stripsScriptTag() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<script>alert('xss')</script>"
                    + "<circle cx=\"10\" cy=\"10\" r=\"5\"/></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContainIgnoringCase("script");
            assertThat(out).doesNotContain("alert");
            assertThat(out).contains("circle");
        }

        @Test
        @DisplayName("strips onload attribute")
        void stripsOnloadAttribute() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\" onload=\"alert(1)\">"
                    + "<rect width=\"10\" height=\"10\"/></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("onload");
            assertThat(out).doesNotContain("alert");
        }

        @Test
        @DisplayName("strips onclick / onmouseover / onfocus / onanimationstart")
        void stripsEveryOnAttribute() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<rect width=\"10\" height=\"10\""
                    + " onclick=\"alert(1)\""
                    + " onmouseover=\"alert(2)\""
                    + " onfocus=\"alert(3)\""
                    + " onanimationstart=\"alert(4)\""
                    + "/></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("onclick");
            assertThat(out).doesNotContain("onmouseover");
            assertThat(out).doesNotContain("onfocus");
            assertThat(out).doesNotContain("onanimationstart");
            assertThat(out).doesNotContain("alert");
        }

        @Test
        @DisplayName("strips <foreignObject> (renders HTML → XSS pivot)")
        void stripsForeignObject() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<foreignObject><body><script>alert(1)</script></body></foreignObject>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContainIgnoringCase("foreignObject");
            assertThat(out).doesNotContainIgnoringCase("script");
            assertThat(out).doesNotContain("alert");
        }

        @Test
        @DisplayName("strips javascript: href on <a>")
        void stripsJavascriptHref() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
                    + "<a xlink:href=\"javascript:alert(1)\"><rect width=\"10\" height=\"10\"/></a>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            // <a> isn't whitelisted — the whole element is dropped along with
            // its attribute, which also kills the javascript: payload.
            assertThat(out).doesNotContain("javascript");
            assertThat(out).doesNotContain("alert");
        }

        @Test
        @DisplayName("strips <use xlink:href> pointing at external document")
        void stripsUseTag() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
                    + "<use xlink:href=\"https://evil.example.com/pwn.svg#x\"/>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("use");
            assertThat(out).doesNotContain("evil.example.com");
        }

        @Test
        @DisplayName("strips <image href> referencing an external URL")
        void stripsImageHref() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<image href=\"https://tracker.example.com/pixel.gif\"/>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("image");
            assertThat(out).doesNotContain("tracker.example.com");
        }

        @Test
        @DisplayName("strips <style> tag")
        void stripsStyleTag() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<style>@import url('https://evil.example.com/styles.css');</style>"
                    + "</svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("style");
            assertThat(out).doesNotContain("@import");
            assertThat(out).doesNotContain("evil.example.com");
        }

        @Test
        @DisplayName("strips XML comments (tracking-pixel vector)")
        void stripsComments() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<!-- leaked: apikey-12345 -->"
                    + "<rect width=\"10\" height=\"10\"/></svg>";
            String out = SvgSanitizer.sanitize(bytes(input));
            assertThat(out).doesNotContain("leaked");
            assertThat(out).doesNotContain("apikey-12345");
        }
    }

    // ====================================================================
    // XXE / external entities
    // ====================================================================

    @Nested
    @DisplayName("XXE vectors are rejected by the parser")
    class XxeVectors {

        @Test
        @DisplayName("rejects DOCTYPE declarations outright")
        void rejectsDoctype() {
            String input = "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><text>&xxe;</text></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects external entity via SYSTEM")
        void rejectsSystemEntity() {
            String input = "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE svg [<!ENTITY x SYSTEM \"http://evil.example.com/x\">]>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">&x;</svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects billion-laughs style entity expansion (DoS)")
        void rejectsBillionLaughs() {
            String input = "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE lolz ["
                    + "  <!ENTITY lol \"lol\">"
                    + "  <!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;\">"
                    + "  <!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;\">"
                    + "]>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><text>&lol2;</text></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ====================================================================
    // Input validation
    // ====================================================================

    @Nested
    @DisplayName("Input validation")
    class Validation {

        @Test
        @DisplayName("rejects null bytes")
        void rejectsNullInput() {
            assertThatThrownBy(() -> SvgSanitizer.sanitize(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("vide");
        }

        @Test
        @DisplayName("rejects empty bytes")
        void rejectsEmptyInput() {
            assertThatThrownBy(() -> SvgSanitizer.sanitize(new byte[0]))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("vide");
        }

        @Test
        @DisplayName("rejects oversized input (> 100 KB)")
        void rejectsOversizedInput() {
            byte[] big = new byte[SvgSanitizer.MAX_SVG_BYTES + 1];
            assertThatThrownBy(() -> SvgSanitizer.sanitize(big))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("taille maximale");
        }

        @Test
        @DisplayName("rejects non-SVG root elements")
        void rejectsNonSvgRoot() {
            String input = "<html><body>hack</body></html>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects malformed XML")
        void rejectsMalformedXml() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><unclosed>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ====================================================================
    // Homogeneity — aspect ratio + dimensions + non-empty content
    // ====================================================================

    @Nested
    @DisplayName("Homogeneity rules (viewBox + aspect ratio + content)")
    class Homogeneity {

        @Test
        @DisplayName("accepts a square SVG via viewBox")
        void acceptsSquareViewBox() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 128 128\">"
                    + "<rect width=\"100\" height=\"100\"/></svg>";
            assertThat(SvgSanitizer.sanitize(bytes(input))).contains("rect");
        }

        @Test
        @DisplayName("accepts a 2:1 landscape ratio (boundary)")
        void acceptsTwoToOneLandscape() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 100\">"
                    + "<rect width=\"200\" height=\"100\"/></svg>";
            assertThat(SvgSanitizer.sanitize(bytes(input))).contains("rect");
        }

        @Test
        @DisplayName("accepts a 1:2 portrait ratio (boundary)")
        void acceptsOneToTwoPortrait() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 200\">"
                    + "<rect width=\"100\" height=\"200\"/></svg>";
            assertThat(SvgSanitizer.sanitize(bytes(input))).contains("rect");
        }

        @Test
        @DisplayName("rejects an extreme 8:1 banner ratio")
        void rejectsBannerRatio() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 100\">"
                    + "<rect width=\"800\" height=\"100\"/></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ratio");
        }

        @Test
        @DisplayName("rejects an extreme 1:8 portrait ratio")
        void rejectsExtremePortrait() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 800\">"
                    + "<rect width=\"100\" height=\"800\"/></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ratio");
        }

        @Test
        @DisplayName("accepts width/height fallback when viewBox is missing")
        void acceptsWidthHeightFallback() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128px\" height=\"128px\">"
                    + "<rect width=\"100\" height=\"100\"/></svg>";
            assertThat(SvgSanitizer.sanitize(bytes(input))).contains("rect");
        }

        @Test
        @DisplayName("rejects an SVG without viewBox AND without width/height")
        void rejectsMissingDimensions() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<rect width=\"10\" height=\"10\"/></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects a malformed viewBox (only 3 values)")
        void rejectsMalformedViewBox() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100\">"
                    + "<rect width=\"10\" height=\"10\"/></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects a zero-dimension viewBox")
        void rejectsZeroDimensionViewBox() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 0 100\">"
                    + "<rect width=\"10\" height=\"10\"/></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects an empty SVG (no shapes)")
        void rejectsEmptySvg() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("forme");
        }

        @Test
        @DisplayName("rejects an SVG that only contains <title> / <desc> (no shapes)")
        void rejectsOnlyTitleDesc() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<title>Schedy</title><desc>Logo</desc></svg>";
            assertThatThrownBy(() -> SvgSanitizer.sanitize(bytes(input)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ====================================================================
    // Regression — after sanitization, output must still parse as SVG
    // ====================================================================

    @Nested
    @DisplayName("Output is valid SVG")
    class OutputValidity {

        @Test
        @DisplayName("sanitized output re-parses cleanly")
        void outputReparses() {
            String input = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                    + "<script>alert(1)</script>"
                    + "<rect width=\"50\" height=\"50\" fill=\"red\"/>"
                    + "</svg>";
            String once = SvgSanitizer.sanitize(bytes(input));
            // Second pass must succeed AND produce the same output.
            String twice = SvgSanitizer.sanitize(once.getBytes(StandardCharsets.UTF_8));
            assertThat(twice).isEqualTo(once);
            assertThat(twice).contains("<rect");
            assertThat(twice).doesNotContainIgnoringCase("script");
        }
    }
}
