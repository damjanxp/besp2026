package com.bsep.pki.security;

import com.bsep.pki.model.entity.UserSession;
import com.bsep.pki.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger AUDIT = org.slf4j.LoggerFactory.getLogger("SECURITY_AUDIT");

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final SessionService sessionService;

    public static final String CURRENT_JTI_ATTRIBUTE = "currentJti";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/") || path.startsWith("/api/crl/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtService.extractUsername(jwt);
            final String jti = jwtService.extractJti(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                Optional<UserSession> activeSession = sessionService.findValid(jti);
                boolean tokenValid = jwtService.validateToken(jwt, userDetails);
                if (tokenValid && activeSession.isPresent()) {
                    sessionService.touch(activeSession.get());
                    request.setAttribute(CURRENT_JTI_ATTRIBUTE, jti);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else if (tokenValid) {
                    AUDIT.warn("JWT_SESSION_REVOKED jti={} path={}", jti, request.getServletPath());
                }
            }
        } catch (Exception e) {
            AUDIT.warn("JWT_VALIDATION_FAILURE reason={} path={}", e.getClass().getSimpleName(), request.getServletPath());
        }

        filterChain.doFilter(request, response);
    }
}
