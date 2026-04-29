package com.bsep.pki.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_shares")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private PasswordEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(nullable = false)
    private LocalDateTime sharedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by", nullable = false)
    private User sharedBy;

    @PrePersist
    protected void onCreate() {
        sharedAt = LocalDateTime.now();
    }
}

