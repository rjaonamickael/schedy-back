package com.schedy.util;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Utility class for locale and language detection based on ISO country codes.
 *
 * <p>Consolidates the French-speaking country logic that was previously duplicated
 * across AuthService, EmployeService, SuperAdminService, and RegistrationRequestService
 * with diverging country lists. This is the single source of truth.
 *
 * <p>Country coverage: all sovereign and non-sovereign French-speaking territories
 * recognised by the Organisation Internationale de la Francophonie (OIF), using
 * ISO 3166-1 alpha-2 codes. MDG (alpha-3) is also accepted for backward compatibility
 * with existing data that may have stored the 3-letter code.
 *
 * <p>Canada (CA / CAN) intentionally returns {@code false}: Canada is officially
 * bilingual (French + English) and the Schedy default experience is English unless a
 * province such as Quebec is explicitly modelled. Adjust if per-province locale is added.
 */
public final class LocaleUtils {

    private LocaleUtils() {}

    /**
     * Full union of French-speaking ISO alpha-2 codes across all four legacy service
     * implementations (AuthService, EmployeService, SuperAdminService,
     * RegistrationRequestService), extended to the complete OIF member list.
     *
     * <p>"MDG" (ISO alpha-3 for Madagascar) is included for backward compatibility.
     */
    private static final Set<String> FRENCH_SPEAKING = Set.of(
        // Western Europe
        "FR", "BE", "CH", "LU", "MC",
        // Madagascar (alpha-2 and alpha-3 for legacy data)
        "MG", "MDG",
        // Caribbean / Americas
        "HT",
        // West Africa
        "SN", "CI", "BF", "GN", "ML", "NE", "TG", "BJ", "GA",
        // Central Africa
        "CM", "CG", "CD", "TD",
        // East Africa / Indian Ocean
        "DJ", "KM", "SC",
        // French Overseas (DOM-ROM-COM)
        "RE", "GP", "MQ", "GF", "YT",
        // French Pacific territories
        "NC", "PF", "WF",
        // French Saint-Martin / Saint-Barth / Saint-Pierre
        "PM", "BL", "MF"
    );

    /**
     * Returns the canonical {@link ZoneId} for a given ISO country code (B-22).
     *
     * <p>Accepts both ISO alpha-2 (e.g. "MG") and the legacy alpha-3 variants already
     * present in the data ("MDG", "CAN"). The mapping covers the two primary beta markets
     * (Madagascar UTC+3, Quebec UTC-5/-4) plus France and a safe UTC default.
     *
     * @param pays ISO alpha-2 or alpha-3 country code stored on {@code Organisation.pays};
     *             {@code null} is safe and returns UTC
     * @return a non-null {@link ZoneId}; defaults to {@link ZoneOffset#UTC}
     */
    public static ZoneId zoneIdFromPays(String pays) {
        if (pays == null) return ZoneOffset.UTC;
        return switch (pays.toUpperCase().trim()) {
            case "MDG", "MG"  -> ZoneId.of("Indian/Antananarivo"); // UTC+3
            case "CAN", "CA"  -> ZoneId.of("America/Montreal");    // UTC-5 / UTC-4 (DST)
            case "FR"         -> ZoneId.of("Europe/Paris");        // UTC+1 / UTC+2 (DST)
            case "BE"         -> ZoneId.of("Europe/Brussels");
            case "CH"         -> ZoneId.of("Europe/Zurich");
            // HIGH-06: added missing beta-market and French-speaking countries
            case "LU"         -> ZoneId.of("Europe/Luxembourg");   // UTC+1 / UTC+2 (DST)
            case "HT"         -> ZoneId.of("America/Port-au-Prince"); // UTC-5 / UTC-4 (DST)
            case "SN"         -> ZoneId.of("Africa/Dakar");        // UTC+0 (no DST)
            case "CI"         -> ZoneId.of("Africa/Abidjan");      // UTC+0 (no DST)
            case "CM"         -> ZoneId.of("Africa/Douala");       // UTC+1 (no DST)
            default           -> ZoneOffset.UTC;
        };
    }

    /**
     * Returns {@code true} if the given ISO country code corresponds to a
     * predominantly French-speaking territory.
     *
     * <p>Accepts both alpha-2 (e.g. "FR") and the legacy alpha-3 "MDG" (Madagascar).
     * Lookup is case-insensitive and trims surrounding whitespace.
     *
     * <p>Canada (CA / CAN) always returns {@code false}: Canada is bilingual and
     * Schedy defaults to English for Canadian organisations.
     *
     * @param pays ISO alpha-2 (or "MDG") country code; {@code null} is safe
     * @return {@code true} if the country is French-speaking, {@code false} otherwise
     */
    public static boolean isFrenchSpeaking(String pays) {
        if (pays == null) return false;
        String upper = pays.toUpperCase().trim();
        // Canada is bilingual — default to English
        if ("CAN".equals(upper) || "CA".equals(upper)) return false;
        return FRENCH_SPEAKING.contains(upper);
    }
}
