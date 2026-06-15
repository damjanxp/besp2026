package com.bsep.pki.service;

import com.bsep.pki.model.dto.SessionResponse;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserSession;
import com.bsep.pki.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;

    /** Refresh lastActivityAt at most this often, to avoid a DB write on every request. */
    private static final Duration ACTIVITY_THROTTLE = Duration.ofMinutes(1);

    @Transactional
    public void createSession(User user, String jti, String ipAddress, String userAgent, long expirationMs) {
        LocalDateTime now = LocalDateTime.now();
        UserSession session = UserSession.builder()
                .jti(jti)
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceLabel(parseDeviceLabel(userAgent))
                .issuedAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusNanos(expirationMs * 1_000_000))
                .revoked(false)
                .build();
        sessionRepository.save(session);
        log.info("Session created for user {} (jti={}, ip={})", user.getEmail(), jti, ipAddress);
    }

    /**
     * Returns the session for this jti only if it is still valid
     * (exists, not revoked, not expired). Used by the JWT filter.
     */
    public Optional<UserSession> findValid(String jti) {
        return sessionRepository.findByJti(jti)
                .filter(s -> !s.isRevoked())
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /** Update lastActivityAt, throttled to avoid excessive writes. */
    @Transactional
    public void touch(UserSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getLastActivityAt() == null
                || Duration.between(session.getLastActivityAt(), now).compareTo(ACTIVITY_THROTTLE) >= 0) {
            session.setLastActivityAt(now);
            sessionRepository.save(session);
        }
    }

    public List<SessionResponse> listForUser(User user, String currentJti) {
        return sessionRepository.findByUserAndRevokedFalseOrderByLastActivityAtDesc(user).stream()
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(s -> SessionResponse.builder()
                        .jti(s.getJti())
                        .ipAddress(s.getIpAddress())
                        .deviceLabel(s.getDeviceLabel())
                        .issuedAt(s.getIssuedAt())
                        .lastActivityAt(s.getLastActivityAt())
                        .current(s.getJti().equals(currentJti))
                        .build())
                .collect(Collectors.toList());
    }

    /** Revoke a single session — only the owner may revoke their own session. */
    @Transactional
    public void revoke(String jti, User user) {
        UserSession session = sessionRepository.findByJti(jti)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        session.setRevoked(true);
        sessionRepository.save(session);
        log.info("Session revoked for user {} (jti={})", user.getEmail(), jti);
    }

    /** Periodically remove expired sessions (log rotation / housekeeping). */
    @Scheduled(fixedRate = 3_600_000) // hourly
    @Transactional
    public void cleanupExpired() {
        List<UserSession> expired = sessionRepository.findByExpiresAtBefore(LocalDateTime.now());
        if (!expired.isEmpty()) {
            sessionRepository.deleteAll(expired);
            log.info("Cleaned up {} expired session(s)", expired.size());
        }
    }

    /** Best-effort human-friendly label from a User-Agent string. */
    private String parseDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Nepoznat uređaj";
        }
        String ua = userAgent;
        String browser = "Nepoznat browser";
        if (ua.contains("Edg")) browser = "Edge";
        else if (ua.contains("OPR") || ua.contains("Opera")) browser = "Opera";
        else if (ua.contains("Chrome")) browser = "Chrome";
        else if (ua.contains("Firefox")) browser = "Firefox";
        else if (ua.contains("Safari")) browser = "Safari";

        String os = "Nepoznat OS";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";
        else if (ua.contains("Mac OS")) os = "macOS";
        else if (ua.contains("Linux")) os = "Linux";

        return browser + " na " + os;
    }
}
