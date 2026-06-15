package com.bsep.pki.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an active login session (a single issued JWT).
 *
 * SECURITY: The JWT itself is NEVER stored. Only the token's unique identifier
 * ({@code jti} claim) plus metadata is kept, so a token can be individually
 * revoked (e.g. "log out this device") without a server-side token store.
 */
@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The JWT "jti" claim — links this session record to an issued token. */
    @Column(unique = true, nullable = false)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    /** Human-friendly label derived from the user agent, e.g. "Chrome na Windows". */
    @Column
    private String deviceLabel;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
