package com.bsep.pki.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificate_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ca_issuer_id", nullable = false)
    private Certificate caIssuer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column
    private String cnRegex;

    @Column
    private String sanRegex;

    @Column
    private Integer maxTtlDays;

    @Column
    private String defaultKeyUsage;

    @Column
    private String defaultExtendedKeyUsage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
