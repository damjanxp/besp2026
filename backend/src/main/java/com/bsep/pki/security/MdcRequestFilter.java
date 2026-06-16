package com.bsep.pki.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates SLF4J MDC with per-request context so every log line carries:
 *   requestId  – unique trace ID for correlating log entries across a single request
 *   clientIp   – originating IP (honours X-Forwarded-For)
 *   userEmail  – claimed email from the JWT (decoded without re-validation, for logging only)
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class MdcRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            MDC.put("clientIp",  resolveClientIp(request));
            MDC.put("userEmail", extractEmailFromToken(request.getHeader("Authorization")));
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Decodes the JWT payload (base64url, no signature check) just to extract the
     * subject claim for logging.  Never used for authorisation decisions.
     */
    private String extractEmailFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return "anonymous";
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length != 3) return "anonymous";
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(
                    parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4)
            );
            String payload = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
            int idx = payload.indexOf("\"sub\":\"");
            if (idx < 0) return "anonymous";
            int start = idx + 7;
            int end = payload.indexOf('"', start);
            return end > start ? payload.substring(start, end) : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
