package com.schedy.config;

import com.schedy.entity.User;
import com.schedy.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantContext tenantContext;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, @Lazy TenantContext tenantContext, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.tenantContext = tenantContext;
        this.userRepository = userRepository;
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

        // Reject refresh tokens and 2FA-pending tokens — neither must establish a security context.
        if (jwtUtil.isTokenValid(token)
                && !jwtUtil.isRefreshToken(token)
                && !jwtUtil.is2faPendingToken(token)) {
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
                request.setAttribute("organisationId", organisationId);

                // SEC-05: For impersonation tokens, re-verify at request time that the
                // impersonator still holds the SUPERADMIN role in the database.
                // A SUPERADMIN demoted or deleted during the 30-minute token window
                // must not be able to continue acting under an impersonation session.
                // This adds one DB query per impersonated request, which is acceptable
                // given the elevated sensitivity of impersonation actions.
                String impersonator = jwtUtil.extractClaim(token, "impersonator");
                if (impersonator != null) {
                    User impersonatorUser = userRepository.findByEmail(impersonator).orElse(null);
                    if (impersonatorUser == null || impersonatorUser.getRole() != User.UserRole.SUPERADMIN) {
                        log.warn("SEC-05: impersonation token rejected — impersonator '{}' is no longer a SUPERADMIN", impersonator);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Impersonation token invalide (impersonator not a superadmin)\"}");
                        SecurityContextHolder.clearContext();
                        return;
                    }
                    request.setAttribute("impersonator", impersonator);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
