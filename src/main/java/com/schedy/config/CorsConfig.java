package com.schedy.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    void validateCorsProfile() {
        if ("dev".equals(activeProfile)) {
            boolean hasNonLocal = Arrays.stream(allowedOrigins.split(","))
                    .anyMatch(o -> !o.contains("localhost") && !o.contains("127.0.0.1"));
            if (hasNonLocal) {
                log.error("SECURITY: dev profile active with non-localhost allowed origin '{}'. "
                        + "LAN CORS wildcards are enabled. Set SPRING_PROFILES_ACTIVE=prod for production.", allowedOrigins);
                throw new IllegalStateException("Dev CORS profile detected with production-like origins. "
                        + "Set SPRING_PROFILES_ACTIVE=prod");
            }
            log.warn("CORS: dev profile active — LAN wildcard patterns enabled for mobile testing");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>(List.of(allowedOrigins.split(",")));

        // Only add permissive LAN patterns in dev (for mobile testing)
        if ("dev".equals(activeProfile)) {
            origins.addAll(List.of(
                "http://localhost:*",
                "http://192.168.*:*",
                "http://10.*:*",
                "http://172.16.*:*"
            ));
        }

        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
