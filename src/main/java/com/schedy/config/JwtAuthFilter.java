package com.schedy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantContext tenantContext;

    public JwtAuthFilter(JwtUtil jwtUtil, @Lazy TenantContext tenantContext) {
        this.jwtUtil = jwtUtil;
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtUtil.isTokenValid(token) && !jwtUtil.isRefreshToken(token)) {
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            String organisationId = jwtUtil.extractOrganisationId(token);

            // SUPERADMIN operates without an organisation context
            if ("SUPERADMIN".equals(role)) {
                tenantContext.markAsSuperAdmin();
            } else {
                tenantContext.setOrganisationId(organisationId);
            }

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authToken = new UsernamePasswordAuthenticationToken(email, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Store organisationId as request attribute for downstream services
                // For impersonation tokens, also expose the impersonator claim for audit logging
                request.setAttribute("organisationId", organisationId);
                String impersonator = jwtUtil.extractClaim(token, "impersonator");
                if (impersonator != null) {
                    request.setAttribute("impersonator", impersonator);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
