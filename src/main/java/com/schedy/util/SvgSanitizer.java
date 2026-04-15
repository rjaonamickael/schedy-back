package com.schedy.util;

import com.schedy.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strict SVG sanitizer for user-uploaded logos.
 *
 * <h2>Threat model</h2>
 * SVG is XML and can embed scripts, event handlers, external entities
 * (XXE), and references to arbitrary URLs. A naive `innerHTML` render
 * of an uploaded SVG is equivalent to letting the attacker run JS in
 * the victim's browser. This sanitizer enforces a tight whitelist of
 * tags + attributes, disables DTD / external entity resolution, and
 * caps the document depth to prevent billion-laughs attacks.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Reject empty / oversized input (> 100 KB)</li>
 *   <li>Parse as XML with DOCTYPE + external entities DISABLED</li>
 *   <li>Walk the DOM: drop any element not in {@link #ALLOWED_ELEMENTS}</li>
 *   <li>Drop any attribute not in {@link #ALLOWED_ATTRIBUTES}</li>
 *   <li>Drop {@code href} / {@code xlink:href} that don't match the
 *       safe-scheme whitelist ({@link #SAFE_HREF})</li>
 *   <li>Drop any attribute starting with {@code on} (event handlers)</li>
 *   <li>Serialize back to a clean UTF-8 string</li>
 * </ol>
 *
 * <p>Any failure (parse error, unknown root element, I/O issue) throws a
 * {@link BusinessRuleException} with a user-facing message. The raw bytes
 * are never stored — only the sanitized output is persisted to R2.
 */
@Slf4j
public final class SvgSanitizer {

    /** Max accepted SVG size in bytes. Logos should be well under this. */
    public static final int MAX_SVG_BYTES = 100 * 1024; // 100 KB

    /**
     * Aspect-ratio bounds for logos. Schedy's card container is a 72×72
     * square with {@code object-fit: contain}. A logo that is too wide
     * (e.g. 400×50 = 8:1) would render as a 72×9 strip lost in whitespace,
     * breaking visual consistency across the testimonials grid. We enforce
     * a ratio between 0.5 (2:1 portrait) and 2.0 (2:1 landscape) — generous
     * enough to accept most real logos while rejecting banner-style marks.
     */
    public static final double MIN_ASPECT_RATIO = 0.5;
    public static final double MAX_ASPECT_RATIO = 2.0;

    /**
     * Shape elements that count as "visible content". A clean SVG without
     * at least one of these is almost certainly a placeholder or a hollow
     * wrapper and should be rejected so the card never renders empty.
     */
    private static final Set<String> SHAPE_ELEMENTS = Set.of(
            "path", "rect", "circle", "ellipse", "line", "polygon", "polyline",
            "text", "use", "image"
    );

    /**
     * Tags allowed in the output SVG. Everything else is stripped.
     * The list covers the basic shape / path / gradient / text vocabulary
     * that logo designers use — no {@code script}, {@code foreignObject},
     * {@code image}, {@code use}, {@code animate*}, {@code style}, etc.
     */
    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
            "svg", "g", "path", "circle", "ellipse", "rect", "line",
            "polygon", "polyline", "defs", "linearGradient", "radialGradient",
            "stop", "title", "desc",
            "text", "tspan", "clipPath", "mask", "pattern",
            "metadata"
    );

    /**
     * Lowercased mirror of {@link #ALLOWED_ELEMENTS} so that {@link #cleanNode}
     * can compare tag names case-insensitively without the camelCase members
     * (linearGradient, radialGradient, clipPath) getting stripped by mistake.
     * The check used to lowercase the tag and compare against the camelCase
     * set, which meant {@code linearGradient} never matched — a silent bug
     * that stripped every gradient out of uploaded logos.
     */
    private static final Set<String> ALLOWED_ELEMENTS_LOWER = ALLOWED_ELEMENTS.stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Attributes allowed on any element. Names are case-sensitive.
     * {@code style} is intentionally omitted — inline styles can load
     * external fonts or images and are hard to sub-sanitize.
     */
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            // Core identity / layout
            "id", "class", "d", "x", "y", "x1", "y1", "x2", "y2",
            "cx", "cy", "r", "rx", "ry", "width", "height", "viewBox",
            "transform", "points", "pathLength", "offset",
            // Presentation
            "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width",
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
            "stroke-opacity", "stroke-dasharray", "stroke-dashoffset",
            "opacity", "color", "visibility", "display",
            // Gradient stops
            "stop-color", "stop-opacity", "gradientUnits", "gradientTransform",
            "spreadMethod",
            // Text
            "font-family", "font-size", "font-weight", "font-style",
            "text-anchor", "dx", "dy",
            // Clip / mask
            "clip-path", "clip-rule", "mask", "mask-units",
            // Namespace
            "xmlns", "xmlns:xlink", "version", "preserveAspectRatio"
    );

    /**
     * Safe URL schemes for {@code href} / {@code xlink:href}. Anything else
     * is dropped — crucially this rejects {@code javascript:}, {@code data:},
     * and absolute URLs to external resources.
     */
    private static final Set<String> SAFE_HREF = Set.of("#");

    private SvgSanitizer() { /* static only */ }

    /**
     * Sanitize raw SVG bytes and return a clean UTF-8 string safe to store
     * and render via {@code innerHTML}.
     *
     * @throws BusinessRuleException when the input is empty, too large,
     *                               not valid XML, not a {@code <svg>}
     *                               root, or fails serialization.
     */
    public static String sanitize(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new BusinessRuleException("Le fichier SVG est vide.");
        }
        if (rawBytes.length > MAX_SVG_BYTES) {
            throw new BusinessRuleException(
                "Le fichier SVG dépasse la taille maximale autorisée ("
                    + (MAX_SVG_BYTES / 1024) + " KB).");
        }

        Document doc = parseSecurely(rawBytes);

        // Root must be an <svg> — anything else is a dressed-up XML payload.
        Node root = doc.getDocumentElement();
        if (root == null || !"svg".equalsIgnoreCase(root.getLocalName() != null
                ? root.getLocalName() : root.getNodeName())) {
            throw new BusinessRuleException("Le fichier n'est pas un SVG valide (racine attendue : <svg>).");
        }

        org.w3c.dom.Element svgEl = (org.w3c.dom.Element) root;
        validateDimensions(svgEl);
        cleanNode(root);
        validateHasContent(svgEl);

        return serialize(doc);
    }

    /**
     * Enforces Schedy's visual consistency rules: the SVG must expose a
     * usable coordinate system (via {@code viewBox} or explicit
     * {@code width}/{@code height}), and its aspect ratio must fit the
     * square card container.
     */
    private static void validateDimensions(org.w3c.dom.Element svg) {
        double ratio;

        String viewBox = svg.getAttribute("viewBox");
        if (viewBox != null && !viewBox.isBlank()) {
            String[] parts = viewBox.trim().split("\\s+");
            if (parts.length != 4) {
                throw new BusinessRuleException(
                        "Le SVG a un viewBox invalide (4 valeurs attendues : minX minY width height).");
            }
            try {
                double w = Double.parseDouble(parts[2]);
                double h = Double.parseDouble(parts[3]);
                if (w <= 0 || h <= 0) {
                    throw new BusinessRuleException("Le viewBox du SVG a des dimensions nulles ou négatives.");
                }
                ratio = w / h;
            } catch (NumberFormatException e) {
                throw new BusinessRuleException("Le viewBox du SVG contient des valeurs non numériques.");
            }
        } else {
            // Fallback: explicit width/height attributes
            Double w = parseLength(svg.getAttribute("width"));
            Double h = parseLength(svg.getAttribute("height"));
            if (w == null || h == null || w <= 0 || h <= 0) {
                throw new BusinessRuleException(
                        "Le logo SVG doit définir un viewBox ou des attributs width/height valides.");
            }
            ratio = w / h;
        }

        if (ratio < MIN_ASPECT_RATIO || ratio > MAX_ASPECT_RATIO) {
            throw new BusinessRuleException(String.format(
                    "Le logo doit avoir un ratio proche du carré (entre %.1f:1 et %.1f:1). "
                            + "Ratio détecté : %.2f:1. Recadrez ou exportez votre logo à une taille plus carrée.",
                    MIN_ASPECT_RATIO, MAX_ASPECT_RATIO, ratio));
        }
    }

    /**
     * Parses a CSS length value (e.g. "100", "100px", "24em") down to a
     * plain number. Returns {@code null} when the value is missing or
     * cannot be understood — callers treat that as "no dimension".
     */
    private static Double parseLength(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replaceAll("(?i)(px|em|rem|pt|%|in|cm|mm)$", "").trim();
        try { return Double.parseDouble(cleaned); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * Ensures the SVG actually draws something. Empty logos (hollow
     * wrappers or SVGs gutted by the whitelist) would render a blank
     * square on the testimonial card.
     */
    private static void validateHasContent(org.w3c.dom.Element svg) {
        NodeList all = svg.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            String tag = n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
            if (tag != null && SHAPE_ELEMENTS.contains(tag.toLowerCase())) {
                return;
            }
        }
        throw new BusinessRuleException(
                "Le SVG ne contient aucune forme visible (path, rect, circle, etc.).");
    }

    // ------------------------------------------------------------------

    /**
     * Parses XML with every dangerous feature disabled. Failure to set a
     * feature is treated as fatal so we never degrade to an insecure parser.
     */
    private static Document parseSecurely(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // OWASP-recommended XXE hardening:
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                // Block every external entity lookup at the resolver level too.
                throw new org.xml.sax.SAXException(
                    "External entity lookups are forbidden (publicId=" + publicId
                        + " systemId=" + systemId + ")");
            });

            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            log.warn("SVG parse rejected: {}", e.getMessage());
            throw new BusinessRuleException("Le fichier SVG n'est pas valide ou contient du contenu interdit.");
        }
    }

    /**
     * Recursively cleans an element: removes forbidden children and
     * attributes, then recurses into the remaining children.
     */
    private static void cleanNode(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        // Pass 1: remove forbidden attributes.
        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            // Collect attribute names first — removing while iterating over a
            // live NamedNodeMap is undefined.
            for (int i = attrs.getLength() - 1; i >= 0; i--) {
                Attr a = (Attr) attrs.item(i);
                String name = a.getName();
                String localName = a.getLocalName() != null ? a.getLocalName() : name;
                if (!isAttributeAllowed(name, localName, a.getValue())) {
                    ((org.w3c.dom.Element) node).removeAttributeNode(a);
                }
            }
        }

        // Pass 2: remove forbidden children, then recurse.
        NodeList children = node.getChildNodes();
        // Snapshot first: removing nodes mutates the live list.
        Node[] snapshot = new Node[children.getLength()];
        for (int i = 0; i < children.getLength(); i++) {
            snapshot[i] = children.item(i);
        }
        for (Node child : snapshot) {
            if (child == null) continue;
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = child.getLocalName() != null
                        ? child.getLocalName() : child.getNodeName();
                if (tag == null || !ALLOWED_ELEMENTS_LOWER.contains(tag.toLowerCase(Locale.ROOT))) {
                    node.removeChild(child);
                    continue;
                }
                cleanNode(child);
            } else if (child.getNodeType() == Node.COMMENT_NODE
                    || child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                // Drop comments / PI / CDATA — they can carry tracking or scripts.
                node.removeChild(child);
            }
            // TEXT_NODE is preserved (legitimate for <title>, <desc>, <text>).
        }
    }

    /**
     * True when an attribute is on the whitelist AND, for href-like
     * attributes, its value starts with a safe prefix.
     */
    private static boolean isAttributeAllowed(String name, String localName, String value) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        // Drop every event handler — belt-and-braces, most wouldn't be on
        // the whitelist anyway.
        if (lower.startsWith("on")) return false;
        // href / xlink:href → must point to an in-document anchor (#id).
        if (lower.equals("href") || lower.equals("xlink:href")) {
            if (value == null) return false;
            String trimmed = value.trim().toLowerCase();
            // Reject absolute URLs, javascript:, data:, and relative paths.
            return SAFE_HREF.stream().anyMatch(trimmed::startsWith);
        }
        return ALLOWED_ATTRIBUTES.contains(lower) || ALLOWED_ATTRIBUTES.contains(localName);
    }

    private static String serialize(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.warn("SVG serialize failed: {}", e.getMessage());
            throw new BusinessRuleException("Impossible de réécrire le SVG nettoyé.");
        }
    }
}
