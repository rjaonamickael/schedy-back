package com.schedy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (origins != null && !origins.isBlank()) {
            // Production / custom: use explicitly configured origins
            // For LAN mobile testing, set CORS_ALLOWED_ORIGINS=http://localhost:4200,http://192.168.*:*,http://10.*:*
            config.setAllowedOriginPatterns(List.of(origins.split(",")));
        } else {
            // Dev default: localhost + LAN (for mobile testing)
            config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",
                "http://localhost:*",
                "http://192.168.*:*",
                "http://10.*:*",
                "http://172.16.*:*"
            ));
        }
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
